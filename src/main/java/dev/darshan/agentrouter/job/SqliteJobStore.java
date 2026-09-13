package dev.darshan.agentrouter.job;

import dev.darshan.agentrouter.reliability.*;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Transactional single-instance SQLite store. The connection is deliberately
 * owned by one service instance; active_service fences a second instance.
 */
@Component
public final class SqliteJobStore implements AutoCloseable {
    private final Connection connection;
    private final String instanceId;
    private final int workflowQueueCapacity;
    private final String fencingToken;

    @org.springframework.beans.factory.annotation.Autowired
    public SqliteJobStore() {
        this(System.getProperty("agentrouter.jobs.db", "jdbc:sqlite:agentrouter.db"),
                UUID.randomUUID().toString(), SciConfig.defaults().workflowQueue());
    }

    public SqliteJobStore(String jdbcUrl) {
        this(jdbcUrl, UUID.randomUUID().toString(), SciConfig.defaults().workflowQueue());
    }

    public SqliteJobStore(String jdbcUrl, String instanceId, int workflowQueueCapacity) {
        try {
            this.connection = DriverManager.getConnection(jdbcUrl);
            this.instanceId = instanceId;
            this.workflowQueueCapacity = workflowQueueCapacity;
            initialize();
            this.fencingToken = acquireActiveService();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to initialize SQLite job store", e);
        }
    }

    private void initialize() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 0");
            statement.execute("CREATE TABLE IF NOT EXISTS schema_version(version INTEGER NOT NULL)");
            int version = 0;
            try (ResultSet rs = statement.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
                if (rs.next()) version = rs.getInt(1);
            }
            if (version == 0) statement.execute("INSERT INTO schema_version(version) VALUES (1)");
            else if (version != 1) throw new SQLException("Unsupported sci-schema-v1");
            statement.execute("""
                CREATE TABLE IF NOT EXISTS operations(
                  operation_id TEXT PRIMARY KEY, request_id TEXT NOT NULL, principal TEXT NOT NULL,
                  idempotency_key TEXT NOT NULL, canonical_hash TEXT NOT NULL, normalized_json TEXT NOT NULL,
                  workflow TEXT NOT NULL, state TEXT NOT NULL, outcome TEXT NOT NULL,
                  evidence_status TEXT NOT NULL, recovery_status TEXT NOT NULL, reason_code TEXT NOT NULL,
                  cancellation_requested INTEGER NOT NULL DEFAULT 0, accepted_at TEXT NOT NULL,
                  observed_at TEXT NOT NULL, state_version INTEGER NOT NULL DEFAULT 1
                )""");
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS op_principal_key ON operations(principal,idempotency_key)");
            statement.execute("""
                CREATE TABLE IF NOT EXISTS attempts(
                  attempt_id TEXT PRIMARY KEY, operation_id TEXT NOT NULL, invocation_id TEXT NOT NULL,
                  action TEXT NOT NULL, result TEXT, reason_code TEXT, observed_at TEXT NOT NULL,
                  FOREIGN KEY(operation_id) REFERENCES operations(operation_id)
                )""");
            statement.execute("""
                CREATE TABLE IF NOT EXISTS events(
                  event_id TEXT PRIMARY KEY, operation_id TEXT NOT NULL, producer TEXT NOT NULL,
                  stream_id TEXT NOT NULL, sequence INTEGER NOT NULL, event_type TEXT NOT NULL,
                  state_before TEXT, state_after TEXT, reason_code TEXT, observed_at TEXT NOT NULL,
                  UNIQUE(producer,stream_id,sequence),
                  FOREIGN KEY(operation_id) REFERENCES operations(operation_id)
                )""");
            statement.execute("""
                CREATE TABLE IF NOT EXISTS dispatch(
                  operation_id TEXT PRIMARY KEY, backend_key TEXT NOT NULL UNIQUE,
                  backend_identity TEXT, creation_status TEXT NOT NULL, start_history TEXT NOT NULL,
                  dispatch_status TEXT NOT NULL, fencing_token TEXT NOT NULL,
                  FOREIGN KEY(operation_id) REFERENCES operations(operation_id)
                )""");
            statement.execute("""
                CREATE TABLE IF NOT EXISTS active_service(
                  singleton INTEGER PRIMARY KEY CHECK(singleton=1), instance_id TEXT NOT NULL,
                  fencing_token TEXT NOT NULL, heartbeat TEXT NOT NULL, pid INTEGER
                )""");
            statement.execute("""
                CREATE TABLE IF NOT EXISTS artifacts(
                  artifact_id TEXT PRIMARY KEY, operation_id TEXT NOT NULL,
                  relative_path TEXT NOT NULL, size INTEGER NOT NULL,
                  sha256 TEXT NOT NULL, observed_at TEXT NOT NULL,
                  UNIQUE(operation_id,relative_path),
                  FOREIGN KEY(operation_id) REFERENCES operations(operation_id)
                )""");
            boolean hasPid = false;
            try (ResultSet columns = statement.executeQuery("PRAGMA table_info(active_service)")) {
                while (columns.next()) {
                    if ("pid".equals(columns.getString("name"))) hasPid = true;
                }
            }
            if (!hasPid) statement.execute("ALTER TABLE active_service ADD COLUMN pid INTEGER");
            boolean hasClaim = false;
            boolean hasDispatchVersion = false;
            try (ResultSet columns = statement.executeQuery("PRAGMA table_info(dispatch)")) {
                while (columns.next()) {
                    String name = columns.getString("name");
                    if ("dispatch_claim".equals(name)) hasClaim = true;
                    if ("dispatch_version".equals(name)) hasDispatchVersion = true;
                }
            }
            if (!hasClaim) statement.execute("ALTER TABLE dispatch ADD COLUMN dispatch_claim TEXT NOT NULL DEFAULT 'NOT_CLAIMED'");
            if (!hasDispatchVersion) statement.execute("ALTER TABLE dispatch ADD COLUMN dispatch_version INTEGER NOT NULL DEFAULT 0");
        }
    }

    private String acquireActiveService() throws SQLException {
        String token = UUID.randomUUID().toString();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT instance_id,pid FROM active_service WHERE singleton=1")) {
                try (ResultSet rs = query.executeQuery()) {
                    if (rs.next() && !rs.getString("instance_id").equals(instanceId)) {
                        long ownerPid = rs.getLong("pid");
                        boolean pidPresent = !rs.wasNull() && ownerPid > 0;
                        if (!pidPresent) {
                            connection.rollback();
                            throw new IllegalStateException(
                                    "active owner cannot be proven dead; refusing takeover");
                        }
                        boolean ownerAlive = ProcessHandle.of(ownerPid)
                                .map(ProcessHandle::isAlive).orElse(false);
                        if (ownerAlive) {
                            connection.rollback();
                            throw new IllegalStateException(
                                    "Another live service (pid " + ownerPid + ") owns the SQLite store");
                        }
                        // ProcessHandle absence is the explicit proven-dead
                        // evidence. Heartbeat age alone is never takeover
                        // evidence. Outstanding operations retain history and
                        // are reconciled before any new dispatch.
                    }
                }
            }
            try (PreparedStatement upsert = connection.prepareStatement("""
                    INSERT INTO active_service(singleton,instance_id,fencing_token,heartbeat,pid)
                    VALUES(1,?,?,?,?)
                    ON CONFLICT(singleton) DO UPDATE SET instance_id=excluded.instance_id,
                    fencing_token=excluded.fencing_token, heartbeat=excluded.heartbeat,
                    pid=excluded.pid""")) {
                upsert.setString(1, instanceId);
                upsert.setString(2, token);
                upsert.setString(3, Instant.now().toString());
                upsert.setLong(4, ProcessHandle.current().pid());
                upsert.executeUpdate();
            }
            connection.commit();
            connection.setAutoCommit(true);
            return token;
        } catch (RuntimeException | SQLException e) {
            try { connection.rollback(); } catch (SQLException ignored) {}
            connection.setAutoCommit(true);
            throw e;
        }
    }

    public synchronized AcceptanceResult accept(String principal, String idempotencyKey,
                                                JobRequest request, String requestId,
                                                String canonicalJson, String canonicalHash) {
        Objects.requireNonNull(principal);
        try {
            connection.setAutoCommit(false);
            checkOwnershipTx();
            Existing existing = findByKey(principal, idempotencyKey);
            if (existing != null) {
                connection.commit();
                connection.setAutoCommit(true);
                if (existing.hash.equals(canonicalHash) && existing.operationId.equals(request.operationId())) {
                    return new AcceptanceResult(200, existing.representation, "idempotent duplicate");
                }
                return new AcceptanceResult(409, existing.representation, "idempotency key conflict");
            }
            Existing sameOperation = findByOperation(request.operationId());
            if (sameOperation != null) {
                connection.commit();
                connection.setAutoCommit(true);
                return new AcceptanceResult(409, sameOperation.representation, "operationId conflict");
            }
            if (activeQueueCount() >= workflowQueueCapacity) {
                connection.commit();
                connection.setAutoCommit(true);
                return new AcceptanceResult(429, null, "admission saturated; retry later");
            }
            Instant now = Instant.now();
            JobRepresentation representation = new JobRepresentation(
                    request.operationId(), requestId, principal, idempotencyKey, canonicalHash,
                    OperationState.ACCEPTED, Outcome.ACCEPTED, EvidenceStatus.ABSENT,
                    RecoveryStatus.NOT_REQUIRED, ReasonCode.ACCEPTED, false, now, 1);
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO operations(operation_id,request_id,principal,idempotency_key,canonical_hash,
                    normalized_json,workflow,state,outcome,evidence_status,recovery_status,reason_code,
                    accepted_at,observed_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)""")) {
                insert.setString(1, request.operationId());
                insert.setString(2, requestId);
                insert.setString(3, principal);
                insert.setString(4, idempotencyKey);
                insert.setString(5, canonicalHash);
                insert.setString(6, canonicalJson);
                insert.setString(7, request.workflow());
                insert.setString(8, OperationState.ACCEPTED.name());
                insert.setString(9, Outcome.ACCEPTED.name());
                insert.setString(10, EvidenceStatus.ABSENT.name());
                insert.setString(11, RecoveryStatus.NOT_REQUIRED.name());
                insert.setString(12, ReasonCode.ACCEPTED.name());
                insert.setString(13, now.toString());
                insert.setString(14, now.toString());
                insert.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO dispatch(operation_id,backend_key,creation_status,start_history,
                    dispatch_status,fencing_token) VALUES(?,?,?,?,?,?)""")) {
                insert.setString(1, request.operationId());
                insert.setString(2, "sci-" + request.operationId());
                insert.setString(3, "NOT_CREATED");
                insert.setString(4, "NEVER_STARTED");
                insert.setString(5, "ELIGIBLE");
                insert.setString(6, fencingToken);
                insert.executeUpdate();
            }
            appendEvent(request.operationId(), "job-service", "operations", "ACCEPT",
                    null, OperationState.ACCEPTED, ReasonCode.ACCEPTED, now);
            connection.commit();
            connection.setAutoCommit(true);
            return new AcceptanceResult(202, representation, "accepted");
        } catch (SQLException | RuntimeException e) {
            try { connection.rollback(); } catch (SQLException ignored) {}
            try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
            throw new IllegalStateException("Atomic job acceptance failed", e);
        }
    }

    private int activeQueueCount() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM operations WHERE state IN ('ACCEPTED','RUNNING','UNKNOWN')")) {
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void appendEvent(String operationId, String producer, String stream, String type,
                             OperationState before, OperationState after, ReasonCode reason,
                             Instant observedAt) throws SQLException {
        long sequence = 1;
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT COALESCE(MAX(sequence),0)+1 FROM events WHERE producer=? AND stream_id=?")) {
            query.setString(1, producer);
            query.setString(2, stream);
            try (ResultSet rs = query.executeQuery()) { rs.next(); sequence = rs.getLong(1); }
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO events(event_id,operation_id,producer,stream_id,sequence,event_type,
                state_before,state_after,reason_code,observed_at) VALUES(?,?,?,?,?,?,?,?,?,?)""")) {
            insert.setString(1, UUID.randomUUID().toString());
            insert.setString(2, operationId);
            insert.setString(3, producer);
            insert.setString(4, stream);
            insert.setLong(5, sequence);
            insert.setString(6, type);
            insert.setString(7, before == null ? null : before.name());
            insert.setString(8, after == null ? null : after.name());
            insert.setString(9, reason == null ? null : reason.name());
            insert.setString(10, observedAt.toString());
            insert.executeUpdate();
        }
    }

    public synchronized Optional<JobRepresentation> find(String principal, String operationId) {
        try {
            requireOwner();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM operations WHERE operation_id=? AND principal=?")) {
                statement.setString(1, operationId);
                statement.setString(2, principal);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.of(readRepresentation(rs)) : Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to read operation", e);
        }
    }

    public synchronized Optional<JobRepresentation> findAny(String operationId) {
        try {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM operations WHERE operation_id=?")) {
                statement.setString(1, operationId);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.of(readRepresentation(rs)) : Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public synchronized Optional<JobRepresentation> cancel(String principal, String operationId) {
        try {
            connection.setAutoCommit(false);
            checkOwnershipTx();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT * FROM operations WHERE operation_id=? AND principal=?")) {
                query.setString(1, operationId);
                query.setString(2, principal);
                try (ResultSet rs = query.executeQuery()) {
                    if (!rs.next()) {
                        connection.rollback(); connection.setAutoCommit(true); return Optional.empty();
                    }
                    JobRepresentation current = readRepresentation(rs);
                    if (isTerminal(current.operationState())) {
                        connection.commit(); connection.setAutoCommit(true); return Optional.of(current);
                    }
                    Instant now = Instant.now();
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE operations SET cancellation_requested=1,reason_code=?,observed_at=?,state_version=state_version+1 WHERE operation_id=?")) {
                        update.setString(1, ReasonCode.CANCEL_REQUESTED.name());
                        update.setString(2, now.toString());
                        update.setString(3, operationId);
                        update.executeUpdate();
                    }
                    appendEvent(operationId, "job-service", "operations", "CANCEL_REQUESTED",
                            current.operationState(), current.operationState(), ReasonCode.CANCEL_REQUESTED, now);
                    connection.commit(); connection.setAutoCommit(true);
                    return find(principal, operationId);
                }
            }
        } catch (SQLException | RuntimeException e) {
            try { connection.rollback(); connection.setAutoCommit(true); } catch (SQLException ignored) {}
            throw new IllegalStateException("Unable to cancel operation", e);
        }
    }

    /** Persist an independently observed backend transition without regressing terminals. */
    public synchronized Optional<JobRepresentation> recordBackendObservation(
            String principal, String operationId, BackendRuntime.BackendObservation observation) {
        Objects.requireNonNull(observation, "observation");
        try {
            connection.setAutoCommit(false);
            checkOwnershipTx();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT * FROM operations WHERE operation_id=? AND principal=?")) {
                query.setString(1, operationId);
                query.setString(2, principal);
                try (ResultSet rs = query.executeQuery()) {
                    if (!rs.next()) {
                        connection.rollback(); connection.setAutoCommit(true); return Optional.empty();
                    }
                    JobRepresentation current = readRepresentation(rs);
                    OperationState next = backendState(observation.state());
                    if (isTerminal(current.operationState())) {
                        connection.commit(); connection.setAutoCommit(true); return Optional.of(current);
                    }
                    Outcome outcome = switch (next) {
                        case COMPLETED -> Outcome.SUCCESS;
                        case FAILED -> Outcome.EXECUTION_FAILURE;
                        case CANCELLED -> Outcome.CANCELLED;
                        case UNKNOWN -> Outcome.UNKNOWN_OUTCOME;
                        default -> Outcome.ACCEPTED;
                    };
                    ReasonCode reason = switch (next) {
                        case CANCELLED -> ReasonCode.CANCEL_CONFIRMED;
                        case FAILED -> ReasonCode.BACKEND_UNAVAILABLE;
                        default -> ReasonCode.ACCEPTED;
                    };
                    Instant now = Instant.now();
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE operations SET state=?,outcome=?,evidence_status=?,recovery_status=?,"
                                    + "reason_code=?,observed_at=?,state_version=state_version+1 WHERE operation_id=?")) {
                        update.setString(1, next.name());
                        update.setString(2, outcome.name());
                        update.setString(3, EvidenceStatus.COMPLETE.name());
                        update.setString(4, RecoveryStatus.RESOLVED.name());
                        update.setString(5, reason.name());
                        update.setString(6, now.toString());
                        update.setString(7, operationId);
                        update.executeUpdate();
                    }
                    appendEvent(operationId, "job-service", "operations", "BACKEND_OBSERVED",
                            current.operationState(), next, reason, now);
                    connection.commit(); connection.setAutoCommit(true);
                    return find(principal, operationId);
                }
            }
        } catch (SQLException | RuntimeException e) {
            try { connection.rollback(); connection.setAutoCommit(true); } catch (SQLException ignored) {}
            throw new IllegalStateException("Unable to persist backend observation", e);
        }
    }

    public synchronized Optional<JobRepresentation> confirmCancellation(String principal, String operationId) {
        return recordBackendObservation(principal, operationId,
                new BackendRuntime.BackendObservation("sci-" + operationId, operationId,
                        true, true, "CANCELLED", 0));
    }

    /** Record that a backend was created and/or started for the given operation. */
    public synchronized void confirmDispatchProgress(String operationId, boolean created, boolean started) {
        try {
            connection.setAutoCommit(false);
            checkOwnershipTx();
            if (created) {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE dispatch SET creation_status='CREATED' WHERE operation_id=?")) {
                    update.setString(1, operationId);
                    update.executeUpdate();
                }
            }
            if (started) {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE dispatch SET start_history='STARTED' WHERE operation_id=?")) {
                    update.setString(1, operationId);
                    update.executeUpdate();
                }
            }
            connection.commit();
            connection.setAutoCommit(true);
        } catch (SQLException | RuntimeException e) {
            try { connection.rollback(); connection.setAutoCommit(true); } catch (SQLException ignored) {}
            throw new IllegalStateException("Unable to record dispatch progress", e);
        }
    }

    private static OperationState backendState(String state) {
        if (state == null) return OperationState.UNKNOWN;
        return switch (state.toUpperCase(Locale.ROOT)) {
            case "COMPLETED" -> OperationState.COMPLETED;
            case "FAILED", "ERROR" -> OperationState.FAILED;
            case "CANCELLED", "CANCELED" -> OperationState.CANCELLED;
            case "RUNNING" -> OperationState.RUNNING;
            default -> OperationState.ACCEPTED;
        };
    }

    private static boolean isTerminal(OperationState state) {
        return state == OperationState.COMPLETED || state == OperationState.FAILED
                || state == OperationState.CANCELLED;
    }

    /**
     * Prevention terminal for undispatched accepted work whose workflow budget
     * expired while queued: never started, recorded FAILED/DEADLINE_EXCEEDED
     * with zero executions. Only ACCEPTED (never dispatched) may expire;
     * RUNNING work is never expired here.
     */
    public synchronized Optional<JobRepresentation> expireUndispatched(String principal, String operationId) {
        try {
            connection.setAutoCommit(false);
            checkOwnershipTx();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT * FROM operations WHERE operation_id=? AND principal=?")) {
                query.setString(1, operationId);
                query.setString(2, principal);
                try (ResultSet rs = query.executeQuery()) {
                    if (!rs.next()) {
                        connection.rollback(); connection.setAutoCommit(true); return Optional.empty();
                    }
                    JobRepresentation current = readRepresentation(rs);
                    if (current.operationState() != OperationState.ACCEPTED) {
                        connection.commit(); connection.setAutoCommit(true); return Optional.of(current);
                    }
                    Instant now = Instant.now();
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE operations SET state=?,outcome=?,reason_code=?,observed_at=?,state_version=state_version+1 WHERE operation_id=?")) {
                        update.setString(1, OperationState.FAILED.name());
                        update.setString(2, Outcome.DEADLINE_EXCEEDED.name());
                        update.setString(3, ReasonCode.DEADLINE_EXPIRED.name());
                        update.setString(4, now.toString());
                        update.setString(5, operationId);
                        update.executeUpdate();
                    }
                    appendEvent(operationId, "job-service", "operations", "RECONCILE",
                            OperationState.ACCEPTED, OperationState.FAILED, ReasonCode.DEADLINE_EXPIRED, now);
                    connection.commit(); connection.setAutoCommit(true);
                    return find(principal, operationId);
                }
            }
        } catch (SQLException | RuntimeException e) {
            try { connection.rollback(); connection.setAutoCommit(true); } catch (SQLException ignored) {}
            throw new IllegalStateException("Unable to expire undispatched operation", e);
        }
    }

    public synchronized long countOperations() {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM operations")) {
            rs.next(); return rs.getLong(1);
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public synchronized List<Map<String, Object>> listArtifacts(String principal, String operationId) {
        try {
            requireOwner();
            if (find(principal, operationId).isEmpty()) return List.of();
            List<Map<String, Object>> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT relative_path,size,sha256,observed_at FROM artifacts WHERE operation_id=? ORDER BY relative_path")) {
                statement.setString(1, operationId);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        result.add(Map.of("path", rs.getString(1), "size", rs.getLong(2),
                                "sha256", rs.getString(3), "observedAt", rs.getString(4)));
                    }
                }
            }
            return List.copyOf(result);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to read persisted artifacts", e);
        }
    }

    public synchronized void addArtifact(String operationId, String relativePath, long size, String sha256) {
        if (relativePath == null || relativePath.isBlank() || !sha256.matches("[0-9a-fA-F]{64}")
                || size < 0) throw new IllegalArgumentException("invalid artifact metadata");
        try {
            connection.setAutoCommit(false);
            checkOwnershipTx();
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT OR REPLACE INTO artifacts(artifact_id,operation_id,relative_path,size,sha256,observed_at)
                    VALUES(?,?,?,?,?,?)""")) {
                statement.setString(1, UUID.randomUUID().toString());
                statement.setString(2, operationId);
                statement.setString(3, relativePath.replace('\\', '/'));
                statement.setLong(4, size);
                statement.setString(5, sha256.toLowerCase(Locale.ROOT));
                statement.setString(6, Instant.now().toString());
                statement.executeUpdate();
            }
            connection.commit();
            connection.setAutoCommit(true);
        } catch (SQLException | RuntimeException e) {
            try { connection.rollback(); connection.setAutoCommit(true); } catch (SQLException ignored) {}
            throw new IllegalStateException("Unable to persist artifact", e);
        }
    }

    public synchronized boolean isOwner() {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT fencing_token FROM active_service WHERE singleton=1 AND instance_id=?")) {
            statement.setString(1, instanceId);
            try (ResultSet rs = statement.executeQuery()) { return rs.next() && fencingToken.equals(rs.getString(1)); }
        } catch (SQLException e) { return false; }
    }

    private void requireOwner() {
        if (!isOwner()) throw new IllegalStateException("active service fencing token is invalid");
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE active_service SET heartbeat=? WHERE singleton=1 AND instance_id=? AND fencing_token=?")) {
            update.setString(1, Instant.now().toString());
            update.setString(2, instanceId);
            update.setString(3, fencingToken);
            update.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to heartbeat active service", e);
        }
    }

    /**
     * Fencing validation inside the caller's transaction: the active_service
     * row must still bind this instance and fencing token, otherwise the
     * mutation rolls back. Same message as the pre-check so stale authority
     * is indistinguishable from never-owned.
     */
    private void checkOwnershipTx() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT instance_id,fencing_token FROM active_service WHERE singleton=1")) {
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next() || !instanceId.equals(rs.getString(1))
                        || !fencingToken.equals(rs.getString(2))) {
                    throw new IllegalStateException("active service fencing token is invalid");
                }
            }
        }
        try (PreparedStatement heartbeat = connection.prepareStatement(
                "UPDATE active_service SET heartbeat=? WHERE singleton=1")) {
            heartbeat.setString(1, Instant.now().toString());
            heartbeat.executeUpdate();
        }
    }

    public enum ClaimResult { CLAIMED, ALREADY_CLAIMED, INELIGIBLE, STORE_BUSY }

    /**
     * Durable dispatch claim: short BEGIN IMMEDIATE write transaction with conditional
     * update. Returns only after COMMIT; STORE_BUSY is contention without authority.
     * Unexpected storage errors are thrown, not subsumed as STORE_BUSY.
     */
    public synchronized ClaimResult claim(String principal, String operationId) {
        Objects.requireNonNull(principal);
        Objects.requireNonNull(operationId);
        try {
            try (Statement s = connection.createStatement()) { s.execute("PRAGMA busy_timeout = 0"); } catch (SQLException ignored) {}
            try { connection.createStatement().execute("BEGIN IMMEDIATE"); }
            catch (SQLException e) { if (isBusy(e)) return ClaimResult.STORE_BUSY; throw new IllegalStateException("Unable to begin claim transaction", e); }
            try {
                checkOwnershipTx();
                JobRepresentation op;
                try (PreparedStatement q = connection.prepareStatement("SELECT * FROM operations WHERE operation_id=? AND principal=?")) {
                    q.setString(1, operationId); q.setString(2, principal);
                    try (ResultSet rs = q.executeQuery()) {
                        if (!rs.next()) { rollbackQuietly(); return ClaimResult.INELIGIBLE; }
                        op = readRepresentation(rs);
                    }
                }
                if (isTerminal(op.operationState()) || op.cancellationRequested()) { rollbackQuietly(); return ClaimResult.INELIGIBLE; }
                if (op.operationState() != OperationState.ACCEPTED) { rollbackQuietly(); return ClaimResult.INELIGIBLE; }
                String claim = "NOT_CLAIMED"; int version = 0;
                try (PreparedStatement q = connection.prepareStatement("SELECT dispatch_claim, dispatch_version FROM dispatch WHERE operation_id=?")) {
                    q.setString(1, operationId);
                    try (ResultSet rs = q.executeQuery()) {
                        if (rs.next()) { claim = rs.getString(1); version = rs.getInt(2); }
                        else { rollbackQuietly(); return ClaimResult.INELIGIBLE; }
                    }
                }
                if (!"NOT_CLAIMED".equals(claim)) { rollbackQuietly(); return ClaimResult.ALREADY_CLAIMED; }
                int updated;
                try (PreparedStatement u = connection.prepareStatement("UPDATE dispatch SET dispatch_claim='CLAIMED', dispatch_version=?, fencing_token=? WHERE operation_id=? AND dispatch_claim='NOT_CLAIMED' AND dispatch_version=?")) {
                    u.setInt(1, version + 1); u.setString(2, fencingToken); u.setString(3, operationId); u.setInt(4, version);
                    updated = u.executeUpdate();
                }
                if (updated != 1) { rollbackQuietly(); return ClaimResult.ALREADY_CLAIMED; }
                try { connection.createStatement().execute("COMMIT"); }
                catch (SQLException e) {
                    if (isBusy(e)) { rollbackQuietly(); return ClaimResult.STORE_BUSY; }
                    rollbackQuietly(); throw new IllegalStateException("Unable to commit claim", e);
                }
                return ClaimResult.CLAIMED;
            } catch (SQLException e) {
                if (isBusy(e)) { rollbackQuietly(); return ClaimResult.STORE_BUSY; }
                rollbackQuietly(); throw new IllegalStateException("Unable to claim dispatch", e);
            } catch (RuntimeException e) { rollbackQuietly(); throw e; }
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("fencing token is invalid")) return ClaimResult.INELIGIBLE;
            throw e;
        }
    }

    private static boolean isBusy(SQLException e) {
        String m = e.getMessage() == null ? "" : e.getMessage().toLowerCase(java.util.Locale.ROOT);
        return e.getErrorCode() == 5 || m.contains("busy") || m.contains("locked");
    }

    private void rollbackQuietly() {
        try { connection.createStatement().execute("ROLLBACK"); } catch (SQLException ignored) {}
    }

    /** Live queue depth for health reporting; never exposes fencing material. */
    public synchronized int queueDepth() {
        try {
            return activeQueueCount();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public int queueCapacity() {
        return workflowQueueCapacity;
    }

    private Existing findByKey(String principal, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM operations WHERE principal=? AND idempotency_key=?")) {
            statement.setString(1, principal); statement.setString(2, key);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return null;
                return new Existing(rs.getString("canonical_hash"), rs.getString("operation_id"), readRepresentation(rs));
            }
        }
    }

    private Existing findByOperation(String operationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM operations WHERE operation_id=?")) {
            statement.setString(1, operationId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return null;
                return new Existing(rs.getString("canonical_hash"), rs.getString("operation_id"), readRepresentation(rs));
            }
        }
    }

    private JobRepresentation readRepresentation(ResultSet rs) throws SQLException {
        return new JobRepresentation(rs.getString("operation_id"), rs.getString("request_id"),
                rs.getString("principal"), rs.getString("idempotency_key"), rs.getString("canonical_hash"),
                OperationState.valueOf(rs.getString("state")), Outcome.valueOf(rs.getString("outcome")),
                EvidenceStatus.valueOf(rs.getString("evidence_status")),
                RecoveryStatus.valueOf(rs.getString("recovery_status")),
                ReasonCode.valueOf(rs.getString("reason_code")),
                rs.getBoolean("cancellation_requested"),
                Instant.parse(rs.getString("accepted_at")), rs.getLong("state_version"));
    }

    private record Existing(String hash, String operationId, JobRepresentation representation) {}

    public String getFencingToken() { return fencingToken; }

    @Override
    public void close() {
        try {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM active_service WHERE singleton=1 AND instance_id=? AND fencing_token=?")) {
                delete.setString(1, instanceId);
                delete.setString(2, fencingToken);
                delete.executeUpdate();
            }
            connection.close();
        } catch (SQLException ignored) {}
    }
}
