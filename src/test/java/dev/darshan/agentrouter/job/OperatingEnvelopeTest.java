package dev.darshan.agentrouter.job;

import dev.darshan.agentrouter.reliability.SciConfig;
import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

// ponytail: operating envelope on real service path (submit/find/dispatch), deterministic backend
class OperatingEnvelopeTest {
    record LevelResult(int clients, long submitP50, long statusP50, long dispatchP50, long e2eP50,
                       long accepted, long rejected, long executions, long alreadyClaimed) {}

    private JobService service(SqliteJobStore store, InMemoryBackendRuntime backend) {
        JobService svc = new JobService(store, PrincipalResolver.forTests());
        svc.setDispatcher(new DispatchService(backend));
        return svc;
    }

    private LevelResult runLevel(int clients, int opsPerClient) throws Exception {
        Path dir = Files.createTempDirectory("envelope");
        String url = "jdbc:sqlite:" + dir.resolve("agentrouter.db");
        // Latency sweep uses capacity 128 (documented; max sweep load is 64 ops + probes);
        // the default bound (4) is probed separately in saturationBound().
        SqliteJobStore store = new SqliteJobStore(url, "owner-env", 128);
        InMemoryBackendRuntime backend = new InMemoryBackendRuntime();
        JobService svc = service(store, backend);
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, clients));
        List<Long> submitLat = Collections.synchronizedList(new ArrayList<>());
        List<Long> statusLat = Collections.synchronizedList(new ArrayList<>());
        List<Long> dispatchLat = Collections.synchronizedList(new ArrayList<>());
        List<Long> e2eLat = Collections.synchronizedList(new ArrayList<>());
        java.util.concurrent.atomic.AtomicLong repAcc = new java.util.concurrent.atomic.AtomicLong();
        java.util.concurrent.atomic.AtomicLong repRej = new java.util.concurrent.atomic.AtomicLong();
        List<Future<String>> futures = new ArrayList<>();
        for (int c = 0; c < clients; c++) {
            final int ci = c;
            futures.add(pool.submit(() -> {
                for (int i = 0; i < opsPerClient; i++) {
                    String opId = UUID.randomUUID().toString();
                    JobRequest req = new JobRequest(opId, "roofit", Map.of("seed", 1L, "events", 10L), null);
                    long t0 = System.nanoTime();
                    AcceptanceResult acc = svc.submit("operator", "k-" + ci + "-" + i + "-" + opId, req);
                    long t1 = System.nanoTime();
                    long s0 = System.nanoTime();
                    var found = store.find("operator", opId);
                    long s1 = System.nanoTime();
                    if (found.isEmpty()) return "lost-accepted-op:" + opId;
                    submitLat.add((t1 - t0) / 1_000_000L);
                    statusLat.add((s1 - s0) / 1_000_000L);
                    if (acc.status() == 202) {
                        long e0 = System.nanoTime();
                        svc.dispatch("operator", opId);
                        long e1 = System.nanoTime();
                        dispatchLat.add((e1 - e0) / 1_000_000L);
                        e2eLat.add((e1 - t0) / 1_000_000L);
                        repAcc.incrementAndGet();
                    } else repRej.incrementAndGet();
                }
                return "ok";
            }));
        }
        long accepted = 0, rejected = 0, alreadyClaimed = 0;
        try {
            for (Future<String> f : futures) {
                String o = f.get(120, TimeUnit.SECONDS);
                if (!o.equals("ok")) fail("worker failure: " + o);
            }
            accepted = repAcc.get();
            rejected = repRej.get();
            assertEquals(accepted, backend.executionCount(),
                    "one accepted op means one execution before probes dispatch anything");
            // duplicate + contention probes on one op: second submit same key is dedup, second dispatch is ALREADY_CLAIMED
            String dup = UUID.randomUUID().toString();
            JobRequest dreq = new JobRequest(dup, "roofit", Map.of("seed", 1L, "events", 10L), null);
            assertEquals(202, svc.submit("operator", "dup-key", dreq).status());
            assertEquals(200, svc.submit("operator", "dup-key", dreq).status());
            String dup2 = UUID.randomUUID().toString();
            JobRequest dreq2 = new JobRequest(dup2, "roofit", Map.of("seed", 1L, "events", 10L), null);
            assertEquals(202, svc.submit("operator", "dup2-key", dreq2).status());
            assertEquals(SqliteJobStore.ClaimResult.CLAIMED, store.claim("operator", dup2));
            assertEquals(SqliteJobStore.ClaimResult.ALREADY_CLAIMED, store.claim("operator", dup2));
            alreadyClaimed = 1;
            svc.dispatch("operator", dup);
            int execsBefore = backend.executionCount();
            svc.dispatch("operator", dup);
            assertEquals(execsBefore, backend.executionCount(), "re-dispatch must not start a replacement execution");
        } finally {
            pool.shutdownNow();
            store.close();
        }
        return new LevelResult(clients, p50(submitLat), p50(statusLat), p50(dispatchLat), p50(e2eLat),
                accepted, rejected, backend.executionCount(), alreadyClaimed);
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
    void saturationBound() throws Exception {
        Path dir = Files.createTempDirectory("envelope-sat");
        String url = "jdbc:sqlite:" + dir.resolve("agentrouter.db");
        SqliteJobStore store = new SqliteJobStore(url, "owner-sat", SciConfig.defaults().workflowQueue());
        JobService svc = new JobService(store, PrincipalResolver.forTests());
        int acc = 0, rej = 0;
        for (int i = 0; i < 6; i++) {
            String opId = UUID.randomUUID().toString();
            int st = svc.submit("operator", "sat-" + i,
                    new JobRequest(opId, "roofit", Map.of("seed", 1L, "events", 10L), null)).status();
            if (st == 202) acc++;
            else if (st == 429) rej++;
            else fail("unexpected saturation status " + st);
        }
        assertEquals(4, acc, "default workflowQueue admits 4");
        assertEquals(2, rej, "6th+ submits are 429, counted as rejected — never as latency wins");
        store.close();
    }

    @Test
    void envelopeDeterministicTiers() throws Exception {
        // ponytail: 1/4/16 always; 64 only when -Denvelope.full=true (machine capacity gate)
        List<LevelResult> rows = new ArrayList<>();
        rows.add(runLevel(1, 8));
        rows.add(runLevel(4, 6));
        rows.add(runLevel(16, 4));
        if (Boolean.getBoolean("envelope.full")) rows.add(runLevel(64, 4));
        for (LevelResult r : rows) {
            assertEquals(0, r.rejected(), "deterministic tier rejects nothing at " + r.clients() + " clients");
            assertTrue(r.submitP50() >= 0 && r.statusP50() >= 0 && r.dispatchP50() >= 0 && r.e2eP50() >= 0,
                    "acceptance, status-read, dispatch and completion timed in separate columns");
            assertEquals(1, r.alreadyClaimed(), "re-claim probe observes ALREADY_CLAIMED");
        }
    }
}
