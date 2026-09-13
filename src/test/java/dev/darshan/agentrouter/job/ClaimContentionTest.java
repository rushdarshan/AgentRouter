package dev.darshan.agentrouter.job;

import dev.darshan.agentrouter.reliability.SciConfig;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

// ponytail: separate-connection contention vs ALREADY_CLAIMED — proves busy_timeout=0 & COMMIT BUSY→ROLLBACK
class ClaimContentionTest {
    @Test
    void separateConnectionsSameEpochBusyVsAlreadyClaimed() throws Exception {
        Path dir = Files.createTempDirectory("claim-test");
        String url = "jdbc:sqlite:" + dir.resolve("agentrouter.db");
        SqliteJobStore store = new SqliteJobStore(url, "owner-1", SciConfig.defaults().workflowQueue());
        // create operation via accept
        String opId = java.util.UUID.randomUUID().toString();
        JobRequest req = new JobRequest(opId, "reana-demo-root6-roofit", java.util.Map.of(), "alice");
        store.accept("alice", "k-" + opId, req, "rid-" + opId, "{}", "hash-" + opId);

        // hold write tx on second raw connection to force STORE_BUSY on BEGIN IMMEDIATE
        try (Connection contender = DriverManager.getConnection(url)) {
            try (Statement s = contender.createStatement()) { s.execute("PRAGMA busy_timeout = 0"); }
            contender.createStatement().execute("BEGIN IMMEDIATE");
            SqliteJobStore.ClaimResult busy = store.claim("alice", opId);
            assertEquals(SqliteJobStore.ClaimResult.STORE_BUSY, busy, "second claimant should see STORE_BUSY");
            contender.createStatement().execute("ROLLBACK");
        }

        // now claim succeeds
        SqliteJobStore.ClaimResult claimed = store.claim("alice", opId);
        assertEquals(SqliteJobStore.ClaimResult.CLAIMED, claimed);

        // second claim on same op -> ALREADY_CLAIMED not STORE_BUSY
        SqliteJobStore.ClaimResult already = store.claim("alice", opId);
        assertEquals(SqliteJobStore.ClaimResult.ALREADY_CLAIMED, already);

        store.close();
    }
}
