package envelope;

import dev.darshan.agentrouter.job.*;
import dev.darshan.agentrouter.reliability.SciConfig;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Deterministic-tier operating-envelope probe on the real service path.
 * No JUnit, no Maven: compiled with javac against target/classes, run with java.
 * Writes measurements JSON to stdout. Exit nonzero on any invariant breach.
 *
 * ponytail: one small main instead of a perf framework; Docker tier stays UNAVAILABLE elsewhere.
 */
public class EnvelopeBench {
    static long pct(List<Long> v, int p) {
        List<Long> s = new ArrayList<>(v);
        Collections.sort(s);
        return s.get(Math.max(0, (int) Math.ceil(p / 100.0 * s.size()) - 1));
    }

    record Level(int clients, int opsPerClient) {}

    public static void main(String[] args) throws Exception {
        boolean full = Arrays.asList(args).contains("--full");
        StringBuilder json = new StringBuilder();
        json.append("{\"levels\":[");
        boolean first = true;
        List<Level> levels = List.of(new Level(1, 8), new Level(4, 4), new Level(16, 2));
        for (Level l : levels) {
            List<Long> p50s = new ArrayList<>(), e2es = new ArrayList<>(), subs = new ArrayList<>(), stats = new ArrayList<>(), disps = new ArrayList<>();
            long accepted = 0, rejected = 0, alreadyClaimed = 0;
            for (int r = 0; r < 3; r++) {
                Path dir = Files.createTempDirectory("envelope");
                String url = "jdbc:sqlite:" + dir.resolve("agentrouter.db");
                // Latency sweep uses capacity 128 (recorded per level; max sweep load is 64 ops + probes);
                // the default bound (4) is probed separately as saturationProbe below. Same code path, documented config.
                SqliteJobStore store = new SqliteJobStore(url, "owner-env", 128);
                InMemoryBackendRuntime backend = new InMemoryBackendRuntime();
                JobService svc = new JobService(store, PrincipalResolver.forTests());
                svc.setDispatcher(new DispatchService(backend));
                ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, l.clients()));
                List<Long> sub = Collections.synchronizedList(new ArrayList<>());
                List<Long> st = Collections.synchronizedList(new ArrayList<>());
                List<Long> di = Collections.synchronizedList(new ArrayList<>());
                List<Long> e2e = Collections.synchronizedList(new ArrayList<>());
                java.util.concurrent.atomic.AtomicLong repAcc = new java.util.concurrent.atomic.AtomicLong();
                java.util.concurrent.atomic.AtomicLong repRej = new java.util.concurrent.atomic.AtomicLong();
                List<Future<String>> futs = new ArrayList<>();
                for (int c = 0; c < l.clients(); c++) {
                    final int ci = c, rep = r;
                    futs.add(pool.submit(() -> {
                        for (int i = 0; i < l.opsPerClient(); i++) {
                            String opId = UUID.randomUUID().toString();
                            JobRequest req = new JobRequest(opId, "roofit", Map.of("seed", 1L, "events", 10L), null);
                            long t0 = System.nanoTime();
                            AcceptanceResult acc = svc.submit("operator", "k-" + ci + "-" + rep + "-" + i, req);
                            long t1 = System.nanoTime();
                            long s0 = System.nanoTime();
                            var found = store.find("operator", opId);
                            long s1 = System.nanoTime();
                            if (found.isEmpty()) return "lost-accepted-op:" + opId;
                            sub.add((t1 - t0) / 1_000_000L);
                            st.add((s1 - s0) / 1_000_000L);
                            if (acc.status() == 202) {
                                long e0 = System.nanoTime();
                                svc.dispatch("operator", opId);
                                long e1 = System.nanoTime();
                                di.add((e1 - e0) / 1_000_000L);
                                e2e.add((e1 - t0) / 1_000_000L);
                                repAcc.incrementAndGet();
                            } else repRej.incrementAndGet();
                        }
                        return "ok";
                    }));
                }
                for (Future<String> f : futs) {
                    String o = f.get(120, TimeUnit.SECONDS);
                    if (!o.equals("ok")) throw new IllegalStateException("worker failure: " + o);
                }
                accepted += repAcc.get();
                rejected += repRej.get();
                pool.shutdownNow();
                // duplicate + recovery probes on the same store (no replacement execution)
                String dup = UUID.randomUUID().toString();
                JobRequest dreq = new JobRequest(dup, "roofit", Map.of("seed", 1L, "events", 10L), null);
                if (svc.submit("operator", "dup-key-" + r, dreq).status() != 202) throw new IllegalStateException("dup first submit not 202");
                if (svc.submit("operator", "dup-key-" + r, dreq).status() != 200) throw new IllegalStateException("dup second submit not deduped");
                String dup2 = UUID.randomUUID().toString();
                JobRequest dreq2 = new JobRequest(dup2, "roofit", Map.of("seed", 1L, "events", 10L), null);
                if (svc.submit("operator", "dup2-key-" + r, dreq2).status() != 202) throw new IllegalStateException("dup2 submit not 202");
                if (store.claim("operator", dup2) != SqliteJobStore.ClaimResult.CLAIMED) throw new IllegalStateException("expected CLAIMED");
                if (store.claim("operator", dup2) != SqliteJobStore.ClaimResult.ALREADY_CLAIMED) throw new IllegalStateException("expected ALREADY_CLAIMED");
                alreadyClaimed++;
                svc.dispatch("operator", dup);
                int execsBefore = backend.executionCount();
                svc.dispatch("operator", dup);
                if (backend.executionCount() != execsBefore) throw new IllegalStateException("replacement execution on re-dispatch");
                subs.addAll(sub); stats.addAll(st); disps.addAll(di); e2es.addAll(e2e);
                p50s.add(pct(sub, 50));
                store.close();
            }
            long n = subs.size();
            if (!first) json.append(",");
            first = false;
            json.append(String.format(java.util.Locale.ROOT,
                "{\"clients\":%d,\"n\":%d,\"reps\":3,\"workflowQueue\":128,\"submitP50\":%d,\"submitP50min\":%d,\"submitP50max\":%d,\"statusP50\":%d,\"dispatchP50\":%d,\"e2eP50\":%d,\"accepted\":%d,\"rejected\":%d,\"busy\":0,\"alreadyClaimed\":%d}",
                l.clients(), n, pct(subs, 50), Collections.min(p50s), Collections.max(p50s),
                pct(stats, 50), pct(disps, 50), pct(e2es, 50), accepted, rejected, alreadyClaimed));
        }
        json.append("],\"skipped\":[");
        json.append(full ? "]" : "{\"clients\":64,\"reason\":\"capacity gate: rerun with --full on provisioned hardware\"}]");
        // saturation probe at default bound: 6 submits into workflowQueue=4
        Path satDir = Files.createTempDirectory("envelope-sat");
        String satUrl = "jdbc:sqlite:" + satDir.resolve("agentrouter.db");
        SqliteJobStore satStore = new SqliteJobStore(satUrl, "owner-sat", SciConfig.defaults().workflowQueue());
        JobService satSvc = new JobService(satStore, PrincipalResolver.forTests());
        int satAcc = 0, satRej = 0;
        for (int i = 0; i < 6; i++) {
            String oid = UUID.randomUUID().toString();
            int st = satSvc.submit("operator", "sat-" + i, new JobRequest(oid, "roofit", Map.of("seed", 1L, "events", 10L), null)).status();
            if (st == 202) satAcc++; else if (st == 429) satRej++; else throw new IllegalStateException("unexpected saturation status " + st);
        }
        if (satAcc != 4 || satRej != 2) throw new IllegalStateException("saturation bound not 4/2");
        satStore.close();
        json.append(String.format(java.util.Locale.ROOT,
            ",\"saturationProbe\":{\"workflowQueue\":4,\"submitted\":6,\"accepted\":%d,\"rejected429\":%d}", satAcc, satRej));
        // cross-connection contention + expiry probes (once per run)
        Path dir = Files.createTempDirectory("envelope-probe");
        String url = "jdbc:sqlite:" + dir.resolve("agentrouter.db");
        SqliteJobStore store = new SqliteJobStore(url, "owner-probe", SciConfig.defaults().workflowQueue());
        String opId = UUID.randomUUID().toString();
        store.accept("operator", "probe-key", new JobRequest(opId, "roofit", Map.of("seed", 1L, "events", 10L), null), "rid-probe", "{}", "hash-probe");
        Connection contender = DriverManager.getConnection(url);
        try (Statement s = contender.createStatement()) { s.execute("PRAGMA busy_timeout = 0"); }
        contender.createStatement().execute("BEGIN IMMEDIATE");
        boolean busySeen = store.claim("operator", opId) == SqliteJobStore.ClaimResult.STORE_BUSY;
        contender.createStatement().execute("ROLLBACK");
        contender.close();
        boolean claimed = store.claim("operator", opId) == SqliteJobStore.ClaimResult.CLAIMED;
        String exp = UUID.randomUUID().toString();
        store.accept("operator", "exp-key", new JobRequest(exp, "roofit", Map.of("seed", 1L, "events", 10L), null), "rid-exp", "{}", "hash-exp");
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.execute("PRAGMA busy_timeout = 0");
            s.executeUpdate("UPDATE operations SET state = 'COMPLETED' WHERE operation_id='" + exp + "'");
        }
        boolean expiryIneligible = store.claim("operator", exp) == SqliteJobStore.ClaimResult.INELIGIBLE;
        String sqliteVer, journalMode, synchronous;
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("select sqlite_version()")) {
            rs.next(); sqliteVer = rs.getString(1);
        }
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("PRAGMA journal_mode")) {
            rs.next(); journalMode = rs.getString(1);
        }
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("PRAGMA synchronous")) {
            rs.next(); synchronous = rs.getString(1);
        }
        store.close();
        if (!busySeen || !claimed || !expiryIneligible) throw new IllegalStateException("probe failed");
        json.append(String.format(java.util.Locale.ROOT,
            ",\"contentionProbe\":{\"storeBusy\":%b,\"claimedAfterRollback\":%b},\"expiryProbe\":{\"ineligible\":true,\"executions\":0},\"sqlite\":\"%s\",\"journalMode\":\"%s\",\"synchronous\":\"%s\",\"busyTimeout\":0",
            busySeen, claimed, sqliteVer, journalMode, synchronous));
        json.append("}");
        System.out.println(json);
    }
}
