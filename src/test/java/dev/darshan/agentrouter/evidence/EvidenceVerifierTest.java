package dev.darshan.agentrouter.evidence;

import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EvidenceVerifierTest {
    @Test
    void missingRequiredPackageFilesFailIntegrity() throws Exception {
        Path root = Paths.get("target", "evidence-test-" + UUID.randomUUID());
        Files.createDirectories(root.resolve("artifacts"));
        Files.writeString(root.resolve("manifest.json"), "{}");
        Files.writeString(root.resolve("events.jsonl"), "");
        Files.writeString(root.resolve("final-state.json"), "{}");
        VerificationResult result = new EvidenceVerifier().verify(root);
        assertEquals(VerificationStatus.FAIL, result.status());
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (Exception ignored) {}
            });
        }
    }
}
