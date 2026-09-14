# Five-minute demonstration

Minute 0 — architecture: open `artifacts/operating-envelope/ARCHITECTURE.md`
and walk the mermaid flow from `POST /jobs` to read-only `--verify`.

Minute 1 — reproduce: run `node scripts/envelope/run-bench.mjs`.
It compiles `scripts/envelope/src/EnvelopeBench.java` against
`target/classes` with cached jars and writes
`artifacts/operating-envelope/raw/envelope-deterministic.json`.

Minute 2 — faults: run `node scripts/envelope/run-junit.mjs`.
It executes `src/test/java/dev/darshan/agentrouter/job/ClaimContentionTest.java`,
`src/test/java/dev/darshan/agentrouter/job/ExpiryZeroExecTest.java` and
`src/test/java/dev/darshan/agentrouter/job/OperatingEnvelopeTest.java`,
writing `artifacts/operating-envelope/raw/junit.json`.

Minute 3 — results: read `artifacts/operating-envelope/ENVELOPE.md`
for the level table and the what-fails-first note; cross-check one number
against `artifacts/operating-envelope/raw/envelope-deterministic.json`.

Minute 4 — claims: walk `artifacts/operating-envelope/CLAIMS.md`
and open one evidence file, e.g.
`target/surefire-reports/dev.darshan.agentrouter.job.DispatchTest.txt`.

Minute 5 — limits: state the limitation in
`artifacts/operating-envelope/ARCHITECTURE.md` and confirm
`artifacts/operating-envelope/README.md` tier labels for anything unrun.
