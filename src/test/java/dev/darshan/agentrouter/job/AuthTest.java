package dev.darshan.agentrouter.job;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** sci-auth-*: distinguishable principals, scoped dedup, allowlist, safe denial. */
class AuthTest {
    private static String memoryUrl() {
        return "jdbc:sqlite:file:auth-" + UUID.randomUUID() + "?mode=memory&cache=shared";
    }

    private static JobService service(SqliteJobStore store) {
        return new JobService(store, PrincipalResolver.forTests());
    }

    private static JobRequest request() {
        return new JobRequest(UUID.randomUUID().toString(), "roofit", Map.of(), null);
    }

    @Test
    void principalsDistinguishable() {
        try (SqliteJobStore store = new SqliteJobStore(memoryUrl())) {
            JobService jobs = service(store);
            assertEquals(202, jobs.submit("operator", "k1", request()).status());
            assertEquals(403, jobs.submit("viewer", "k2", request()).status());
        }
    }

    @Test
    void serviceNotCollapse() {
        try (SqliteJobStore store = new SqliteJobStore(memoryUrl())) {
            JobService jobs = service(store);
            String id = UUID.randomUUID().toString();
            assertEquals(202, jobs.submit("operator", "shared",
                    new JobRequest(id, "roofit", Map.of(), null)).status());
            assertEquals(403, jobs.submit("viewer", "shared", request()).status());
            assertTrue(jobs.get("operator", id).isPresent());
            assertTrue(jobs.get("viewer", id).isEmpty());
        }
    }

    @Test
    void principalIdIgnored() {
        try (SqliteJobStore store = new SqliteJobStore(memoryUrl())) {
            JobService jobs = service(store);
            String id = UUID.randomUUID().toString();
            AcceptanceResult accepted = jobs.submit("operator", "k1",
                    new JobRequest(id, "roofit", Map.of(), "viewer"));
            assertEquals(202, accepted.status());
            assertEquals("operator", accepted.representation().principalRef());
        }
    }

    @Test
    void crossPrincipal404() {
        try (SqliteJobStore store = new SqliteJobStore(memoryUrl())) {
            JobService jobs = service(store);
            String id = UUID.randomUUID().toString();
            assertEquals(202, jobs.submit("operator", "k1",
                    new JobRequest(id, "roofit", Map.of(), null)).status());
            assertTrue(jobs.get("viewer", id).isEmpty());
            assertTrue(jobs.cancel("viewer", id).isEmpty());
        }
    }

    @Test
    void samePrincipal409() {
        try (SqliteJobStore store = new SqliteJobStore(memoryUrl())) {
            JobService jobs = service(store);
            String id = UUID.randomUUID().toString();
            assertEquals(202, jobs.submit("operator", "k1",
                    new JobRequest(id, "roofit", Map.of("seed", 1), null)).status());
            assertEquals(409, jobs.submit("operator", "k1",
                    new JobRequest(id, "roofit", Map.of("seed", 2), null)).status());
            assertEquals(409, jobs.submit("operator", "k-other",
                    new JobRequest(id, "roofit", Map.of(), null)).status());
        }
    }

    @Test
    void allowlistReject() {
        try (SqliteJobStore store = new SqliteJobStore(memoryUrl())) {
            JobService jobs = service(store);
            AcceptanceResult rejected = jobs.submit("operator", "k1",
                    new JobRequest(UUID.randomUUID().toString(), "roofit",
                            Map.of("imageOverride", "evil"), null));
            assertEquals(400, rejected.status());
            assertFalse(rejected.message().contains("evil"));
        }
    }

    @Test
    void deniedNoBackend() {
        try (SqliteJobStore store = new SqliteJobStore(memoryUrl())) {
            JobService jobs = service(store);
            assertEquals(403, jobs.submit("viewer", "k1", request()).status());
            assertEquals(0, store.countOperations());
        }
    }

    @Test
    void unknownTokenResolvesEmpty() {
        assertTrue(PrincipalResolver.forTests().resolve("Bearer unknown").isEmpty());
        assertTrue(PrincipalResolver.forTests().resolve(null).isEmpty());
    }

    @Test
    void missingCredentialConfigFailsFast() {
        String prior = System.getProperty("agentrouter.tokens");
        System.setProperty("agentrouter.tokens", "  ");
        try {
            assertThrows(IllegalStateException.class, PrincipalResolver::parseConfiguredTokens);
            assertThrows(IllegalStateException.class, PrincipalResolver::new);
        } finally {
            if (prior == null) System.clearProperty("agentrouter.tokens");
            else System.setProperty("agentrouter.tokens", prior);
        }
    }

    @Test
    void malformedCredentialConfigFailsFast() {
        String prior = System.getProperty("agentrouter.tokens");
        System.setProperty("agentrouter.tokens", "operator-without-separator");
        try {
            assertThrows(IllegalStateException.class, PrincipalResolver::parseConfiguredTokens);
        } finally {
            if (prior == null) System.clearProperty("agentrouter.tokens");
            else System.setProperty("agentrouter.tokens", prior);
        }
    }
}
