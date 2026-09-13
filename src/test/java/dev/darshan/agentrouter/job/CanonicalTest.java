package dev.darshan.agentrouter.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * sci-job-canonical: hash covers post-default normalized form; sci-c14n-v1
 * vector pins the Java side. TypeScript-side equality is verified when the
 * Guard importer lands (matrix row stays NOT_RUN until then).
 */
class CanonicalTest {
    private static String memoryUrl() {
        return "jdbc:sqlite:file:canonical-" + UUID.randomUUID() + "?mode=memory&cache=shared";
    }

    @Test
    void defaultsCovered() {
        try (SqliteJobStore store = new SqliteJobStore(memoryUrl())) {
            JobService jobs = new JobService(store, PrincipalResolver.forTests());
            AcceptanceResult implicit = jobs.submit("operator", "k-implicit",
                    new JobRequest(UUID.randomUUID().toString(), "roofit", Map.of(), null));
            AcceptanceResult explicit = jobs.submit("operator", "k-explicit",
                    new JobRequest(UUID.randomUUID().toString(), "roofit",
                            Map.of("seed", 0, "events", 1000), null));
            assertEquals(202, implicit.status());
            assertEquals(202, explicit.status());
            assertEquals(implicit.representation().canonicalHash(), explicit.representation().canonicalHash());
        }
    }

    @Test
    void vectorPinsJavaSide() throws Exception {
        ObjectMapper json = new ObjectMapper();
        JsonNode vector;
        try (InputStream in = getClass().getResourceAsStream("/sci-c14n-v1/vectors.json")) {
            assertNotNull(in, "shared c14n vector artifact is missing");
            vector = json.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        JsonNode first = vector.path("vectors").path(0);
        String canonical = Canonicalizer.canonicalize(first.path("input").toString());
        assertEquals(first.path("canonical").asText(), canonical);
        String hash = Canonicalizer.sha256(canonical);
        assertTrue(hash.matches("[0-9a-f]{64}"));
        try (SqliteJobStore store = new SqliteJobStore(memoryUrl())) {
            JobService jobs = new JobService(store, PrincipalResolver.forTests());
            AcceptanceResult accepted = jobs.submit("operator", "k-vector",
                    new JobRequest(UUID.randomUUID().toString(), "roofit",
                            Map.of("seed", 7), null));
            assertEquals(202, accepted.status());
            assertEquals(hash, accepted.representation().canonicalHash());
        }
    }
}
