package dev.darshan.agentrouter.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/** Read-only sci-manifest-v1/sci-events-v1 integrity verifier. */
public final class EvidenceVerifier {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> EVENT_TYPES = Set.of("SUBMIT", "ACCEPT", "REJECT",
            "DISPATCH", "BACKEND_OBSERVED", "CANCEL_REQUESTED", "CANCEL_CONFIRMED", "RECONCILE");

    public VerificationResult verify(Path packageDir) {
        List<String> errors = new ArrayList<>();
        if (packageDir == null || !Files.isDirectory(packageDir)) {
            return new VerificationResult(VerificationStatus.NOT_RUN, List.of("package directory is missing"));
        }

        Path root = packageDir.toAbsolutePath().normalize();
        Path manifest = root.resolve("manifest.json");
        Path events = packageDir.resolve("events.jsonl");
        Path finalState = packageDir.resolve("final-state.json");
        if (!Files.isRegularFile(manifest) || !Files.isRegularFile(events)
                || !Files.isRegularFile(finalState) || !Files.isDirectory(root.resolve("artifacts"))) {
            return new VerificationResult(VerificationStatus.FAIL, List.of("required package file missing"));
        }
        try {
            JsonNode manifestJson = JSON.readTree(Files.readString(manifest));
            requireText(manifestJson, "schemaVersion", errors);
            requireText(manifestJson, "manifestId", errors);
            requireText(manifestJson, "scenarioId", errors);
            requireText(manifestJson, "policyVersion", errors);
            requireText(manifestJson, "configDigest", errors);
            requireText(manifestJson, "upstreamCommit", errors);
            requireText(manifestJson, "containerDigest", errors);
            requireText(manifestJson, "guardImporterVersion", errors);
            requireText(manifestJson, "guardEvaluatorVersion", errors);
            requireText(manifestJson, "evaluationPolicyVersion", errors);
            requireText(manifestJson, "runtimeEnvironment", errors);
            JsonNode inventory = manifestJson.path("inventory");
            if (!inventory.isArray() || inventory.isEmpty()) errors.add("manifest inventory is empty");
            if (!"sci-manifest-v1".equals(manifestJson.path("schemaVersion").asText())) {
                errors.add("unsupported manifest schema");
            }
            if (manifestJson.has("publicationStatus")
                    && !"PUBLISHED".equals(manifestJson.path("publicationStatus").asText())) {
                errors.add("package is not published");
            }
            // A package which advertises publication must contain the complete
            // frozen-input/evaluation boundary. The legacy core verifier remains
            // usable for pre-publication contract fixtures.
            if ("PUBLISHED".equals(manifestJson.path("publicationStatus").asText())) {
                requirePublishedEvaluation(root, manifestJson, errors);
            }
            Set<String> ids = new HashSet<>();
            Map<String, Long> sequences = new HashMap<>();
            try (BufferedReader reader = Files.newBufferedReader(events, StandardCharsets.UTF_8)) {
                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    if (line.isBlank()) {
                        errors.add("line " + lineNumber + ": blank event record");
                        continue;
                    }
                    JsonNode event = JSON.readTree(line);
                    requireText(event, "eventSchemaVersion", errors);
                    requireText(event, "eventId", errors);
                    requireText(event, "producer", errors);
                    requireText(event, "streamId", errors);
                    requireText(event, "eventType", errors);
                    if (!"sci-events-v1".equals(event.path("eventSchemaVersion").asText())) {
                        errors.add("line " + lineNumber + ": schema version");
                    }
                    String id = event.path("eventId").asText();
                    if (!ids.add(id)) errors.add("duplicate eventId");
                    requireUuid(id, "eventId", errors);
                    if (event.has("requestId")) {
                        String requestId = event.path("requestId").asText();
                        requireUuid(requestId, "requestId", errors);
                    }
                    if (event.has("operationId")) {
                        String operationId = event.path("operationId").asText();
                        requireUuid(operationId, "operationId", errors);
                    }
                    if (event.has("attemptId")) {
                        String attemptId = event.path("attemptId").asText();
                        requireUuid(attemptId, "attemptId", errors);
                    }
                    if (!EVENT_TYPES.contains(event.path("eventType").asText())) errors.add("unknown eventType");
                    String key = event.path("producer").asText() + "/" + event.path("streamId").asText();
                    long sequence = event.path("sequence").asLong(-1);
                    long expected = sequences.getOrDefault(key, 0L) + 1;
                    if (sequence != expected) errors.add("non-monotonic event sequence");
                    sequences.put(key, Math.max(sequence, sequences.getOrDefault(key, 0L)));
                    if (!event.has("observedAt") || !isTimestamp(event.path("observedAt").asText())) {
                        errors.add("event missing or malformed observedAt");
                    }
                    if ("ACCEPT".equals(event.path("eventType").asText())
                            && (!event.has("operationId") || !event.has("requestId"))) {
                        errors.add("ACCEPT missing identity");
                    }
                    if ("BACKEND_OBSERVED".equals(event.path("eventType").asText())
                            && (!event.has("operationId") || !event.has("backendSnapshotRef"))) {
                        errors.add("BACKEND_OBSERVED missing backend snapshot reference");
                    }
                    if (event.has("artifactRefs") && !event.path("artifactRefs").isArray()) {
                        errors.add("artifactRefs must be an array");
                    }
                }
            }
            verifyInventory(root, inventory, errors);
        } catch (Exception e) {
            errors.add("malformed evidence: " + e.getMessage());
        }
        return new VerificationResult(errors.isEmpty() ? VerificationStatus.PASS : VerificationStatus.FAIL,
                List.copyOf(errors));
    }

    /** Strict verifier used by publication/--verify; no missing evaluation can pass. */
    public VerificationResult verifyPublished(Path packageDir) {
        VerificationResult base = verify(packageDir);
        if (!base.passed()) return base;
        try {
            JsonNode manifest = JSON.readTree(Files.readString(
                    packageDir.toAbsolutePath().normalize().resolve("manifest.json")));
            if (!"PUBLISHED".equals(manifest.path("publicationStatus").asText())) {
                return new VerificationResult(VerificationStatus.NOT_RUN,
                        List.of("package is unpublished; evaluation was not attempted"));
            }
            List<String> errors = new ArrayList<>();
            requirePublishedEvaluation(packageDir.toAbsolutePath().normalize(), manifest, errors);
            return new VerificationResult(errors.isEmpty() ? VerificationStatus.PASS : VerificationStatus.FAIL,
                    List.copyOf(errors));
        } catch (Exception e) {
            return new VerificationResult(VerificationStatus.FAIL,
                    List.of("malformed published evidence: " + e.getMessage()));
        }
    }

    private void requirePublishedEvaluation(Path root, JsonNode manifest, List<String> errors) {
            for (String name : new String[]{"frozen-input.json", "guard-results.json", "report.txt"}) {
                if (!Files.isRegularFile(root.resolve(name))) errors.add("published package missing " + name);
            }
            for (String field : new String[]{"evaluationInputDigest", "completePackageDigest"}) {
                requireDigest(manifest, field, errors);
            }
            if ("UNAVAILABLE".equalsIgnoreCase(manifest.path("guardEvaluatorVersion").asText())
                    || "UNAVAILABLE".equalsIgnoreCase(manifest.path("evaluationPolicyVersion").asText())) {
                errors.add("published package has unavailable evaluator");
            }
            try {
                JsonNode finalState = JSON.readTree(Files.readString(root.resolve("final-state.json")));
                if (!finalState.isObject() || finalState.size() == 0
                        || (!finalState.has("observations") && !finalState.has("router")
                        && !finalState.has("routerObservations"))) {
                    errors.add("final-state is malformed or missing observations");
                }
                JsonNode frozen = JSON.readTree(Files.readString(root.resolve("frozen-input.json")));
                if (!frozen.path("inputDigest").isTextual() || frozen.path("inputDigest").asText().isBlank()
                        || !frozen.path("bounds").isObject()) {
                    errors.add("frozen-input manifest missing digest or bounds");
                }
                JsonNode results = JSON.readTree(Files.readString(root.resolve("guard-results.json")));
                if (!results.isArray() || results.isEmpty()) {
                    errors.add("guard-results has no evaluator results");
                } else {
                    for (JsonNode result : results) {
                        String verdict = result.path("verdict").asText();
                        if (!Set.of("PASS", "FAIL", "INSUFFICIENT", "ERROR", "REJECTED").contains(verdict)) {
                            errors.add("unknown Guard verdict");
                        }
                        if (!result.path("inputDigest").asText().equals(frozen.path("inputDigest").asText())) {
                            errors.add("Guard result input digest mismatch");
                        }
                    }
                }
            } catch (Exception e) {
                errors.add("malformed evaluation artifacts: " + e.getMessage());
            }
        }

        private static void requireDigest(JsonNode node, String field, List<String> errors) {
            String value = node.path(field).asText("");
            if (!value.matches("[0-9a-fA-F]{64}")) errors.add("manifest missing " + field);
        }

        private static void requireUuid(String value, String field, List<String> errors) {
            try {
                UUID.fromString(value);
            } catch (RuntimeException e) {
                errors.add("malformed " + field);
            }
        }

        private static boolean isTimestamp(String value) {
            try {
                Instant.parse(value);
                return true;
            } catch (RuntimeException e) {
                return false;
            }
            }

    /**
     * Frozen-input boundary: events beyond the frozen per-producer sequence
     * bounds are late arrivals and must not silently enter the evaluated set.
     * Frozen manifest shape: {"bounds": {"producer/streamId": maxSequence}}.
     * Requires a new snapshot/evaluation or a recorded exclusion.
     */
    public VerificationResult verifyFrozen(Path packageDir, Path frozenManifest) {
        if (frozenManifest == null || !Files.isRegularFile(frozenManifest)) {
            return new VerificationResult(VerificationStatus.NOT_RUN, List.of("frozen manifest is missing"));
        }
        List<String> errors = new ArrayList<>();
        try {
            JsonNode frozen = JSON.readTree(Files.readString(frozenManifest));
            JsonNode bounds = frozen.path("bounds");
            Map<String, Long> seen = new HashMap<>();
            try (BufferedReader reader = Files.newBufferedReader(packageDir.resolve("events.jsonl"),
                    StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    JsonNode event = JSON.readTree(line);
                    String key = event.path("producer").asText() + "/" + event.path("streamId").asText();
                    long sequence = event.path("sequence").asLong(-1);
                    seen.merge(key, sequence, Math::max);
                    if (bounds.has(key) && sequence > bounds.path(key).asLong(-1)) {
                        errors.add("late event excluded from evaluated input: " + key + " #" + sequence);
                    }
                }
            }
        } catch (Exception e) {
            errors.add("malformed evidence: " + e.getMessage());
        }
        return new VerificationResult(errors.isEmpty() ? VerificationStatus.PASS : VerificationStatus.FAIL,
                List.copyOf(errors));
    }

    private void verifyInventory(Path root, JsonNode inventory, List<String> errors) throws Exception {
        if (!inventory.isArray()) return;
        for (JsonNode entry : inventory) {
            String relative = entry.path("path").asText("");
            if (relative.isBlank() || entry.path("size").isMissingNode()
                    || !entry.path("size").canConvertToLong()
                    || !entry.path("sha256").asText("").matches("[0-9a-fA-F]{64}")) {
                errors.add("inventory entry missing required size/hash: " + relative);
                continue;
            }
            Path resolved;
            try {
                Path relativePath = Path.of(relative);
                Path canonicalRoot = root.toAbsolutePath().normalize();
                if (relativePath.isAbsolute() || relative.indexOf('\0') >= 0) throw new InvalidPathException(relative, "unsafe path");
                resolved = canonicalRoot.resolve(relativePath).normalize();
                if (!resolved.startsWith(canonicalRoot)
                        || relative.equals("manifest.json") || relative.endsWith(".sha256")) {
                    throw new InvalidPathException(relative, "unsafe path");
                }
                if (Files.exists(resolved)
                        && !resolved.toRealPath().startsWith(canonicalRoot.toRealPath())) {
                    throw new InvalidPathException(relative, "symlink escapes package");
                }
            } catch (InvalidPathException e) {
                errors.add("unsafe or self-referential inventory path");
                continue;
            }
            if (!Files.isRegularFile(resolved)) {
                errors.add("inventoried file missing: " + relative);
                continue;
            }
            if (entry.has("size") && Files.size(resolved) != entry.path("size").asLong(-1)) {
                errors.add("size mismatch: " + relative);
            }
            if (entry.has("sha256") && !entry.path("sha256").asText().equals(sha256(resolved))) {
                errors.add("hash mismatch: " + relative);
            }
        }
    }

    private static void requireText(JsonNode node, String field, List<String> errors) {
        if (!node.has(field) || !node.path(field).isTextual() || node.path(field).asText().isBlank()) {
            errors.add("manifest missing " + field);
        }
    }

    static String sha256(Path path) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        StringBuilder out = new StringBuilder();
        for (byte b : digest) out.append(String.format("%02x", b));
        return out.toString();
    }
}
