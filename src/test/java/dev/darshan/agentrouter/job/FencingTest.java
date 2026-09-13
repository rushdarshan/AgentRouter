package dev.darshan.agentrouter.job;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** sci-job-fencing: no live takeover, stale authority rejected on mutating ops. */
class FencingTest {
    private static String fileUrl() throws Exception {
        Path file = Files.createTempFile("fencing-" + UUID.randomUUID(), ".db");
        file.toFile().deleteOnExit();
        return "jdbc:sqlite:" + file.toAbsolutePath();
    }

    @Test
    void secondCannotBecomeActive() throws Exception {
        String url = fileUrl();
        try (SqliteJobStore first = new SqliteJobStore(url)) {
            assertTrue(first.isOwner());
            assertThrows(IllegalStateException.class, () -> new SqliteJobStore(url, "other", 64));
            assertTrue(first.isOwner());
        }
    }

    @Test
    void staleAuthorityRejected() throws Exception {
        String url = fileUrl();
        try (SqliteJobStore store = new SqliteJobStore(url)) {
            try (var connection = DriverManager.getConnection(url);
                 var delete = connection.prepareStatement("DELETE FROM active_service")) {
                delete.executeUpdate();
            }
            assertFalse(store.isOwner());
            assertThrows(IllegalStateException.class, () -> store.accept("operator", "k1",
                    new JobRequest(UUID.randomUUID().toString(), "roofit", Map.of(), null),
                    UUID.randomUUID().toString(), "{}", "hash"));
        }
    }

    @Test
    void takeoverMidLifeRejectsEveryMutatingOp() throws Exception {        String url = fileUrl();
        try (SqliteJobStore store = new SqliteJobStore(url)) {
            String id = UUID.randomUUID().toString();
            new JobService(store, PrincipalResolver.forTests()).submit("operator", "k1",
                    new JobRequest(id, "roofit", Map.of(), null));
            try (var connection = DriverManager.getConnection(url);
                 var takeover = connection.prepareStatement(
                         "UPDATE active_service SET instance_id='intruder',fencing_token='intruder'")) {
                takeover.executeUpdate();
            }
            assertFalse(store.isOwner());
            String id2 = UUID.randomUUID().toString();
            assertThrows(IllegalStateException.class, () -> store.accept("operator", "k2",
                    new JobRequest(id2, "roofit", Map.of(), null),
                    UUID.randomUUID().toString(), "{}", "hash"));
            assertThrows(IllegalStateException.class, () -> store.cancel("operator", id));
            assertThrows(IllegalStateException.class, () -> store.expireUndispatched("operator", id));
            assertTrue(store.findAny(id).isPresent());
        }
    }

    @Test
    void liveOwnerPidStillFailsClosed() throws Exception {
        String url = fileUrl();
        try (SqliteJobStore first = new SqliteJobStore(url, "owner", 64)) {
            assertTrue(first.isOwner());
            // Same JVM pid is alive: a second claimant fails closed, never takes over.
            assertThrows(IllegalStateException.class, () -> new SqliteJobStore(url, "other", 64));
            assertTrue(first.isOwner());
        }
    }

    @Test
    void provenDeadOwnerReclaimsWithFreshToken() throws Exception {
        String url = fileUrl();
        String id = UUID.randomUUID().toString();
        // Abrupt death: the owner never runs close(), so its row survives it.
        SqliteJobStore dead = new SqliteJobStore(url, "dead-owner", 64);
        new JobService(dead, PrincipalResolver.forTests()).submit("operator", "k1",
                new JobRequest(id, "roofit", Map.of(), null));
        try (var connection = DriverManager.getConnection(url);
             var plant = connection.prepareStatement(
                     "UPDATE active_service SET pid=2147483647")) {
            assertEquals(1, plant.executeUpdate());
        }
        try (SqliteJobStore next = new SqliteJobStore(url, "successor", 64)) {
            assertTrue(next.isOwner());
            // Outstanding work survives the restart with its history intact.
            assertTrue(next.findAny(id).isPresent());
            String id2 = UUID.randomUUID().toString();
            assertEquals(202, new JobService(next, PrincipalResolver.forTests()).submit(
                    "operator", "k2", new JobRequest(id2, "roofit", Map.of(), null)).status());
        }
    }
}
