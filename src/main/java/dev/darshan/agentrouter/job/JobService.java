package dev.darshan.agentrouter.job;

import dev.darshan.agentrouter.reliability.OperationId;
import dev.darshan.agentrouter.reliability.OperationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class JobService {
    static final long MAX_EVENTS = 1_000_000L;
    private static final Logger log = LoggerFactory.getLogger(JobService.class);
    private final SqliteJobStore store;
    private final PrincipalResolver principals;
    private java.util.function.Function<String, Boolean> backendCanceller;
    private DispatchService dispatcher;

    public JobService(SqliteJobStore store, PrincipalResolver principals) {
        this.store = store;
        this.principals = principals;
    }

    /** Optional live-backend cancel hook; absent means intent-only (contract tier). */
    public void setBackendCanceller(java.util.function.Function<String, Boolean> backendCanceller) {
        this.backendCanceller = backendCanceller;
    }

    /** Install the configured runtime boundary; never accepts caller overrides. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setDispatcher(DispatchService dispatcher) {
        this.dispatcher = dispatcher;
        if (dispatcher != null && this.backendCanceller == null) {
            this.backendCanceller = dispatcher::cancel;
        }
    }

    public AcceptanceResult submit(String principal, String idempotencyKey, JobRequest request) {
        if (!principals.canWrite(principal)) {
            return new AcceptanceResult(403, null, "forbidden");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return new AcceptanceResult(400, null, "Idempotency-Key is required");
        }
        try {
            OperationId.parse(request.operationId());
        } catch (RuntimeException e) {
            return new AcceptanceResult(400, null, "operationId must be a UUIDv4");
        }
        String workflow = request.workflow() == null ? "roofit" : request.workflow();
        if (!"roofit".equals(workflow)) {
            return new AcceptanceResult(400, null, "unsupported workflow");
        }
        Map<String, Object> parameters = request.parameters() == null
                ? Collections.emptyMap() : request.parameters();
        for (String key : parameters.keySet()) {
            if (!Set.of("seed", "events").contains(key)) {
                return new AcceptanceResult(400, null, "unsupported workflow parameter");
            }
        }
        Long seed = longParam(parameters, "seed", 0L, 0L, (long) Integer.MAX_VALUE);
        Long events = longParam(parameters, "events", 1000L, 1L, MAX_EVENTS);
        if (seed == null || events == null) {
            return new AcceptanceResult(400, null, "workflow parameter out of domain");
        }
        Map<String, Object> effective = new TreeMap<>();
        effective.put("workflow", workflow);
        effective.put("parameters", new TreeMap<>(parameters));
        Map<String, Object> effectiveParameters = (Map<String, Object>) effective.get("parameters");
        effectiveParameters.put("seed", seed);
        effectiveParameters.put("events", events);
        String canonical = Canonicalizer.canonicalize(effective);
        String hash = Canonicalizer.sha256(canonical);
        String requestId = UUID.randomUUID().toString();
        AcceptanceResult result = store.accept(principal, idempotencyKey,
                new JobRequest(request.operationId(), workflow,
                        (Map<String, Object>) effective.get("parameters"), null),
                requestId, canonical, hash);
        log.info("submit requestId={} operationId={} principal={} status={} reason={}",
                requestId, request.operationId(), principal, result.status(), result.message());
        if (result.status() == 202 && dispatcher != null
                && Boolean.getBoolean("agentrouter.jobs.dispatch-on-accept")) {
            dispatch(principal, request.operationId());
        }
        return result;
    }

    // Test hook: pause between committed claim and backend contact (e.g. CountDownLatch).
    volatile Runnable claimPauseHook;

    /** Dispatch and observe an already accepted operation through the runtime boundary. */
    public Optional<BackendRuntime.BackendHandle> dispatch(String principal, String operationId) {
        if (!principals.canWrite(principal) || dispatcher == null || !store.isOwner()) {
            return Optional.empty();
        }
        // Durable claim transaction (BEGIN IMMEDIATE, conditional UPDATE, busy hygiene).
        SqliteJobStore.ClaimResult claim = store.claim(principal, operationId);
        switch (claim) {
            case CLAIMED -> { /* proceed */ }
            case ALREADY_CLAIMED -> {
                // Inspect durable progress; another worker may still be dispatching.
                return Optional.empty();
            }
            case INELIGIBLE, STORE_BUSY -> {
                return Optional.empty();
            }
        }
        if (claimPauseHook != null) claimPauseHook.run();
        BackendRuntime.BackendHandle handle = dispatcher.dispatchIfOwned(operationId, store::isOwner);
        // Durably record creation/start: when handle.created() is true,
        // DispatchService already called start() — both flags are the same value.
        store.confirmDispatchProgress(operationId, handle.created(), handle.created());
        dispatcher.inspect(operationId).ifPresent(observation ->
                store.recordBackendObservation(principal, operationId, observation));
        return Optional.of(handle);
    }

    /** Integral value domains only: rejects fractional, null, unutyped, and out-of-range values. */
    private static Long longParam(Map<String, Object> parameters, String name,
                                  long def, long min, long max) {
        if (!parameters.containsKey(name)) return def;
        Object value = parameters.get(name);
        if (value == null) return null;
        long parsed;
        if (value instanceof Number number) {
            double d = number.doubleValue();
            if (!Double.isFinite(d) || d != Math.rint(d)
                    || Math.abs(d) > Long.MAX_VALUE) return null;
            parsed = (long) d;
        } else {
            return null;
        }
        return parsed >= min && parsed <= max ? parsed : null;
    }

    public Optional<JobRepresentation> get(String principal, String operationId) {
        return store.find(principal, operationId);
    }

    public Optional<JobRepresentation> cancel(String principal, String operationId) {
        if (!principals.canWrite(principal)) return Optional.empty();
        Optional<JobRepresentation> result = store.cancel(principal, operationId);
        boolean backendCancelled = false;
        if (result.isPresent() && result.get().cancellationRequested() && backendCanceller != null) {
            backendCancelled = Boolean.TRUE.equals(backendCanceller.apply("sci-" + operationId));
            if (backendCancelled) result = store.confirmCancellation(principal, operationId);
        }
        log.info("cancel operationId={} principal={} admitted={} cancellationRequested={} backendCancelled={}",
                operationId, principal, result.isPresent(),
                result.map(JobRepresentation::cancellationRequested).orElse(false), backendCancelled);
        return result;
    }

    public boolean canRead(String principal) {
        return principal != null;
    }

    public Optional<List<Map<String, Object>>> artifacts(String principal, String operationId) {
        if (principal == null || store.find(principal, operationId).isEmpty()) return Optional.empty();
        return Optional.of(store.listArtifacts(principal, operationId));
    }

    public SqliteJobStore store() {
        return store;
    }
}
