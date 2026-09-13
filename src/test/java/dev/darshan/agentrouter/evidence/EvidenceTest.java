package dev.darshan.agentrouter.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** sci-ev-*: manifest, event stream, integrity, frozen boundary, report regeneration. */
class EvidenceTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private Path packageDir() throws Exception {
        return Files.createDirectories(
                Path.of("target", "evidence-" + UUID.randomUUID()).toAbsolutePath());
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static ObjectNode event(String id, String producer, String stream, long sequence,
                                    String type) {
        ObjectNode event = JSON.createObjectNode();
        event.put("eventSchemaVersion", "sci-events-v1");
        event.put("eventId", id);
        event.put("scenarioId", "sci-test");
        event.put("producer", producer);
        event.put("streamId", stream);
        event.put("sequence", sequence);
        event.put("eventType", type);
        event.put("observedAt", "2026-09-13T00:00:00Z");
        return event;
    }

    /** Minimal integrity-passing package; inventory covers events + final-state only. */
    private Path validPackage() throws Exception {
        Path root = packageDir();
        Files.createDirectories(root.resolve("artifacts"));
        String acceptId = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();
        String operationId = UUID.randomUUID().toString();
        ObjectNode accept = event(acceptId, "job-service", "operations", 1, "ACCEPT");
        accept.put("operationId", operationId);
        accept.put("requestId", requestId);
        String events = accept + "\n";
        Files.writeString(root.resolve("events.jsonl"), events, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("final-state.json"), "{}", StandardCharsets.UTF_8);
        ObjectNode manifest = JSON.createObjectNode();
        manifest.put("schemaVersion", "sci-manifest-v1");
        manifest.put("manifestId", UUID.randomUUID().toString());
        manifest.put("scenarioId", "sci-test");
        manifest.put("policyVersion", "sci-policy-v1");
        manifest.put("configDigest", "cfg");
        manifest.put("upstreamCommit", "upstream");
        manifest.put("containerDigest", "container");
        manifest.put("guardImporterVersion", "guard-importer-v1");
        manifest.put("guardEvaluatorVersion", "guard-evaluator-v1");
        manifest.put("evaluationPolicyVersion", "sci-policy-v1");
        manifest.put("runtimeEnvironment", "test");
        ArrayNode inventory = manifest.putArray("inventory");
        for (String name : new String[]{"events.jsonl", "final-state.json"}) {
            byte[] bytes = Files.readAllBytes(root.resolve(name));
            ObjectNode entry = JSON.createObjectNode();
            entry.put("path", name);
            entry.put("size", bytes.length);
            entry.put("sha256", sha256(bytes));
            inventory.add(entry);
        }
        Files.writeString(root.resolve("manifest.json"), manifest.toString(), StandardCharsets.UTF_8);
        return root;
    }

    @Test
    void manifestRequiredFields() throws Exception {
        Path root = packageDir();
        Files.createDirectories(root.resolve("artifacts"));
        Files.writeString(root.resolve("manifest.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("events.jsonl"), "", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("final-state.json"), "{}", StandardCharsets.UTF_8);
        VerificationResult result = new EvidenceVerifier().verify(root);
        assertEquals(VerificationStatus.FAIL, result.status());
    }

    @Test
    void manifestSelfExclusion() throws Exception {
        VerificationResult result = new EvidenceVerifier().verify(validPackage());
        assertEquals(VerificationStatus.PASS, result.status(), result.diagnostics().toString());
    }

    @Test
    void seqMonotonic() throws Exception {
        Path root = validPackage();
        ObjectNode gap = event(UUID.randomUUID().toString(), "job-service", "operations", 3, "RECONCILE");
        Files.writeString(root.resolve("events.jsonl"), gap + "\n",
                StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        assertEquals(VerificationStatus.FAIL, new EvidenceVerifier().verify(root).status());
    }

    @Test
    void requiredPerType() throws Exception {
        Path root = validPackage();
        ObjectNode bare = event(UUID.randomUUID().toString(), "job-service", "operations", 2, "ACCEPT");
        Files.writeString(root.resolve("events.jsonl"), bare + "\n",
                StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        VerificationResult result = new EvidenceVerifier().verify(root);
        assertEquals(VerificationStatus.FAIL, result.status());
    }

    @Test
    void unknownEnum() throws Exception {
        Path root = validPackage();
        ObjectNode unknown = event(UUID.randomUUID().toString(), "job-service", "operations", 2, "FROBNICATE");
        Files.writeString(root.resolve("events.jsonl"), unknown + "\n",
                StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        assertEquals(VerificationStatus.FAIL, new EvidenceVerifier().verify(root).status());
    }

    @Test
    void pathTraversal() throws Exception {
        Path root = validPackage();
        ObjectNode manifest = (ObjectNode) JSON.readTree(Files.readString(root.resolve("manifest.json")));
        ObjectNode evil = JSON.createObjectNode();
        evil.put("path", "../../etc/passwd");
        ((ArrayNode) manifest.path("inventory")).add(evil);
        Files.writeString(root.resolve("manifest.json"), manifest.toString(), StandardCharsets.UTF_8);
        assertEquals(VerificationStatus.FAIL, new EvidenceVerifier().verify(root).status());
    }

    @Test
    void duplicateId() throws Exception {
        Path root = packageDir();
        Files.createDirectories(root.resolve("artifacts"));
        String id = UUID.randomUUID().toString();
        ObjectNode first = event(id, "job-service", "operations", 1, "RECONCILE");
        ObjectNode second = event(id, "job-service", "operations", 2, "RECONCILE");
        Files.writeString(root.resolve("events.jsonl"), first + "\n" + second + "\n",
                StandardCharsets.UTF_8);
        Files.writeString(root.resolve("final-state.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("manifest.json"), manifestFor(root), StandardCharsets.UTF_8);
        assertEquals(VerificationStatus.FAIL, new EvidenceVerifier().verify(root).status());
    }

    private static String manifestFor(Path root) throws Exception {
        ObjectNode manifest = JSON.createObjectNode();
        manifest.put("schemaVersion", "sci-manifest-v1");
        manifest.put("manifestId", UUID.randomUUID().toString());
        manifest.put("scenarioId", "sci-test");
        manifest.put("policyVersion", "sci-policy-v1");
        manifest.put("configDigest", "cfg");
        manifest.put("upstreamCommit", "upstream");
        manifest.put("containerDigest", "container");
        manifest.put("guardImporterVersion", "guard-importer-v1");
        manifest.put("guardEvaluatorVersion", "guard-evaluator-v1");
        manifest.put("evaluationPolicyVersion", "sci-policy-v1");
        manifest.put("runtimeEnvironment", "test");
        ArrayNode inventory = manifest.putArray("inventory");
        for (String name : new String[]{"events.jsonl", "final-state.json"}) {
            byte[] bytes = Files.readAllBytes(root.resolve(name));
            ObjectNode entry = JSON.createObjectNode();
            entry.put("path", name);
            entry.put("size", bytes.length);
            entry.put("sha256", sha256(bytes));
            inventory.add(entry);
        }
        return manifest.toString();
    }

    @Test
    void reportRegenerates() throws Exception {
        Path root = validPackage();
        EvidenceVerifier verifier = new EvidenceVerifier();
        VerificationResult first = verifier.verify(root);
        VerificationResult second = verifier.verify(root);
        assertEquals(first.status(), second.status());
        assertEquals(first.diagnostics(), second.diagnostics());
    }

    @Test
    void lateEventExcluded() throws Exception {
        Path root = validPackage();
        EvidenceVerifier verifier = new EvidenceVerifier();
        ObjectNode frozen = JSON.createObjectNode();
        ObjectNode bounds = frozen.putObject("bounds");
        bounds.put("job-service/operations", 1L);
        Path frozenManifest = root.resolve("frozen-input.json");
        Files.writeString(frozenManifest, frozen.toString(), StandardCharsets.UTF_8);
        assertEquals(VerificationStatus.PASS, verifier.verifyFrozen(root, frozenManifest).status());
        ObjectNode late = event(UUID.randomUUID().toString(), "job-service", "operations", 2, "RECONCILE");
        Files.writeString(root.resolve("events.jsonl"), late + "\n",
                StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        VerificationResult excluded = verifier.verifyFrozen(root, frozenManifest);
        assertEquals(VerificationStatus.FAIL, excluded.status());
        assertTrue(excluded.diagnostics().stream().anyMatch(d -> d.contains("late event")));
    }
}
