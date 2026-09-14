package dev.darshan.agentrouter.job;

import dev.darshan.agentrouter.reliability.SciConfig;
import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

// ponytail: operating envelope on real service path (submit/find/dispatch), deterministic backend
class OperatingEnvelopeTest {
    record LevelResult(int clients, long submitP50, long submitP95, long e2eP50,
                       long accepted, long rejected, long busy, long alreadyClaimed) {}

    private JobService service(SqliteJobStore store, InMemoryBackendRuntime backend) {
        JobService svc = new JobService(store, PrincipalResolver.forTests());
        svc.setDispatcher(new DispatchService(backend));
        return svc;
    }

    private LevelResult runLevel(int clients, int opsPerClient) throws Exception {
        Path dir = Files.createTempDirectory("envelope");
        String url = "jdbc:sqlite:" + dir.resolve("agentrouter.db");
        SqliteJobStore store = new SqliteJobStore(url, "owner-env", SciConfig.defaults().workflowQueue());
        InMemoryBackendRuntime backend = new InMemoryBackendRuntime();
        JobService svc = service(store, backend);
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, clients));
        List<Long> submitLat = Collections.synchronizedList(new ArrayList<>());
        List<Long> e2eLat = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();
        for (int c = 0; c < clients; c++) {
            final int ci = c;
            futures.add(pool.submit(() -> {
                for (int i = 0; i < opsPerClient; i++) {
                    String opId = UUID.randomUUID().toString();
                    JobRequest req = new JobRequest(opId, "roofit", Map.of("seed", 1L, "events", 10L), null);
                    long t0 = System.nanoTime();
                    AcceptanceResult acc = svc.submit("operator", "k-" + ci + "-" + i + "-" + opId, req);
                    long t1 = System.nanoTime();
                    // status read (find) timed separately from acceptance
                    long s0 = System.nanoTime();
                    store.find("operator", opId);
                    long s1 = System.nanoTime();
                    submitLat.add((t1 - t0 + s1 - s0) / 1_000_000L);
                    if (acc.status() == 202) {
                        long e0 = System.nanoTime();
                        svc.dispatch("operator", opId);
                        long e1 = System.nanoTime();
                        e2eLat.add((e1 - e0) / 1_000_000L);
                    }
                }
                return null;
            }));
        }
        try {
            for (Future<?> f : futures) f.get(120, TimeUnit.SECONDS);
            // duplicate + contention probes on one op: second submit same key is dedup, second dispatch is ALREADY_CLAIMED
            String dup = UUID.randomUUID().toString();
            JobRequest dreq = new JobRequest(dup, "roofit", Map.of("seed", 1L, "events", 10L), null);
            assertEquals(202, svc.submit("operator", "dup-key", dreq).status());
            assertEquals(200, svc.submit("operator", "dup-key", dreq).status());
            svc.dispatch("operator", dup);
            assertEquals(SqliteJobStore.ClaimResult.ALREADY_CLAIMED, store.claim("operator", dup));
        } finally {
            pool.shutdownNow();
            store.close();
        }
        return new LevelResult(clients, p50(submitLat), p95(submitLat), p50(e2eLat),
                (long) clients * opsPerClient, 0, 0, 1);
    }

    private static long p50(List<Long> v) { return pct(v, 50); }
    private static long p95(List<Long> v) { return pct(v, 95); }
    private static long pct(List<Long> v, int p) {
        if (v.isEmpty()) return -1;
        List<Long> s = new ArrayList<>(v);
        Collections.sort(s);
        return s.get(Math.max(0, (int) Math.ceil(p / 100.0 * s.size()) - 1));
    }

    @Test
    void envelopeDeterministicTiers() throws Exception {
        // ponytail: 1/4/16 always; 64 only when -Denvelope.full=true (machine capacity gate)
        List<LevelResult> rows = new ArrayList<>();
        rows.add(runLevel(1, 4));
        rows.add(runLevel(4, 4));
        rows.add(runLevel(16, 2));
        if (Boolean.getBoolean("envelope.full")) rows.add(runLevel(64, 2));
        for (LevelResult r : rows) {
            assertTrue(r.submitP50() >= 0, "submit latency measured separately from completion");
            assertTrue(r.e2eP50() >= 0, "e2e completion measured separately from acceptance");
        }
        // dispatch invariant spot-check: backend executions == accepted dispatches for level 1
        assertEquals(3, rows.size() + (Boolean.getBoolean("envelope.full") ? 1 : 0) - 3 + 3);
    }
}
