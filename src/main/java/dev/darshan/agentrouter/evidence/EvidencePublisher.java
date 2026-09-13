package dev.darshan.agentrouter.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/**
 * Staged collect/freeze/evaluate/publish writer. A package is not published
 * unless the frozen input and a real evaluator result are both present.
 */
public final class EvidencePublisher {
    private final ObjectMapper json = new ObjectMapper();
    private final EvidenceVerifier verifier = new EvidenceVerifier();

    /**
     * Compatibility entry point retained for callers that do not have Guard
     * output. It deliberately fails closed instead of publishing an
     * INVENTORY-only or UNAVAILABLE package.
     */
    public Path publish(Path root, String runId, String scenarioId, String upstreamCommit,
                        String containerDigest, String eventsJsonl, String finalStateJson) throws Exception {
        throw new IllegalStateException("UNAVAILABLE: Guard frozen-input and evaluator results are required");
    }

    public Path publish(Path root, String runId, String scenarioId, String upstreamCommit,
                        String containerDigest, String eventsJsonl, String finalStateJson,
                        String frozenInputJson, String guardResultsJson,
                        String guardEvaluatorVersion, String evaluationPolicyVersion) throws Exception {
        Objects.requireNonNull(root, "root");
        requireNonBlank(runId, "runId");
        requireNonBlank(scenarioId, "scenarioId");
        requireAvailable(upstreamCommit, "upstreamCommit");
        requireAvailable(containerDigest, "containerDigest");
        requireAvailable(eventsJsonl, "events");
        requireAvailable(finalStateJson, "final-state");
        requireAvailable(frozenInputJson, "frozen-input");
        requireAvailable(guardResultsJson, "guard-results");
        requireAvailable(guardEvaluatorVersion, "guard evaluator");
        requireAvailable(evaluationPolicyVersion, "evaluation policy");

        JsonNode frozen = json.readTree(frozenInputJson);
        JsonNode guardResults = json.readTree(guardResultsJson);
        if (!frozen.path("inputDigest").isTextual() || !frozen.path("bounds").isObject()) {
            throw new IllegalArgumentException("frozen input must contain inputDigest and bounds");
        }
        if (!guardResults.isArray() || guardResults.isEmpty()) {
            throw new IllegalArgumentException("guard-results must contain evaluator results");
        }
        for (JsonNode result : guardResults) {
            if (!Set.of("PASS", "FAIL", "INSUFFICIENT", "ERROR", "REJECTED")
                    .contains(result.path("verdict").asText())
                    || !frozen.path("inputDigest").asText()
                    .equals(result.path("inputDigest").asText())) {
                throw new IllegalArgumentException("invalid Guard result");
            }
        }

        Path target = root.resolve("artifacts").resolve("scientific-workflow-reliability-v1")
                .resolve(runId).toAbsolutePath().normalize();
        if (Files.exists(target)) throw new FileAlreadyExistsException(target.toString());
        Path staging = target.resolveSibling(runId + ".staging-" + UUID.randomUUID());
        try {
            Files.createDirectories(staging.resolve("artifacts"));
            Files.writeString(staging.resolve("events.jsonl"), eventsJsonl, StandardCharsets.UTF_8);
            Files.writeString(staging.resolve("final-state.json"), finalStateJson, StandardCharsets.UTF_8);
            Files.writeString(staging.resolve("frozen-input.json"), frozenInputJson, StandardCharsets.UTF_8);
            Files.writeString(staging.resolve("guard-results.json"), guardResultsJson, StandardCharsets.UTF_8);
            Files.writeString(staging.resolve("report.txt"), renderReport(guardResults), StandardCharsets.UTF_8);

            ObjectNode manifest = json.createObjectNode();
            manifest.put("schemaVersion", "sci-manifest-v1");
            manifest.put("publicationStatus", "PUBLISHED");
            manifest.put("manifestId", UUID.randomUUID().toString());
            manifest.put("scenarioId", scenarioId);
            manifest.put("policyVersion", "sci-config-v1");
            manifest.put("configDigest", digest("sci-config-v1"));
            manifest.put("upstreamCommit", upstreamCommit);
            manifest.put("containerDigest", containerDigest);
            manifest.put("guardImporterVersion", "configured");
            manifest.put("guardEvaluatorVersion", guardEvaluatorVersion);
            manifest.put("evaluationPolicyVersion", evaluationPolicyVersion);
            manifest.put("runtimeEnvironment", System.getProperty("os.name"));
            manifest.put("createdAt", Instant.now().toString());
            manifest.put("evaluationInputDigest", frozen.path("inputDigest").asText());
            manifest.put("completePackageDigest", "");
            manifest.putArray("redactions");
            manifest.putArray("unavailableArtifacts");

            String packageDigest = digestInventory(staging);
            manifest.put("completePackageDigest", packageDigest);
            ArrayNode inventory = manifest.putArray("inventory");
            addInventory(staging, staging, inventory);
            Files.writeString(staging.resolve("manifest.json"),
                    json.writerWithDefaultPrettyPrinter().writeValueAsString(manifest),
                    StandardCharsets.UTF_8);
            Files.writeString(staging.resolve("complete-package.sha256"), packageDigest + "\n",
                    StandardCharsets.UTF_8);
            Files.writeString(staging.resolve("evaluation-input.sha256"),
                    frozen.path("inputDigest").asText() + "\n", StandardCharsets.UTF_8);

            VerificationResult result = verifier.verifyPublished(staging);
            if (!result.passed()) throw new IllegalStateException("evidence validation failed: "
                    + result.diagnostics());
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(staging, target);
            }
            return target;
        } catch (Exception e) {
            deleteTree(staging);
            throw e;
        }
    }

    private String renderReport(JsonNode results) {
        StringBuilder report = new StringBuilder("Guard evaluation\n");
        for (JsonNode result : results) {
            report.append(result.path("assertionId").asText("assertion"))
                    .append(": ").append(result.path("verdict").asText()).append('\n');
        }
        return report.toString();
    }

    private void addInventory(Path root, Path current, ArrayNode inventory) throws Exception {
        try (var paths = Files.list(current)) {
            for (Path path : paths.sorted().toList()) {
                if (path.getFileName().toString().equals("manifest.json")
                        || path.getFileName().toString().endsWith(".sha256")) continue;
                if (Files.isDirectory(path)) addInventory(root, path, inventory);
                else if (Files.isRegularFile(path)) {
                    String relative = root.relativize(path).toString().replace('\\', '/');
                    ObjectNode entry = inventory.addObject();
                    entry.put("path", relative);
                    entry.put("size", Files.size(path));
                    entry.put("sha256", EvidenceVerifier.sha256(path));
                }
            }
        }
    }

    private String digestInventory(Path root) throws Exception {
        List<Path> files;
        try (var paths = Files.walk(root)) {
            files = paths.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals("manifest.json"))
                    .filter(path -> !path.getFileName().toString().endsWith(".sha256"))
                    .sorted().toList();
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Path path : files) {
            digest.update(root.relativize(path).toString().replace('\\', '/')
                    .getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Files.readAllBytes(path));
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String digest(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static void requireAvailable(String value, String field) {
        requireNonBlank(value, field);
        if ("UNAVAILABLE".equalsIgnoreCase(value.trim())) {
            throw new IllegalStateException("UNAVAILABLE: " + field);
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (Exception ignored) { }
            });
        }
    }
}
