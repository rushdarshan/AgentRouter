# Claims to executed tests

Committed evidence lives in `artifacts/operating-envelope/raw/`. The
`target/surefire-reports` rows below are local artifacts of the provisioned
2026-09-13 `mvn verify` run (regenerable with `mvn verify`); they are not
committed.

| Claim | Test | Evidence |
|-------|------|----------|
| Duplicate submit is deduped, never double-accepted | src/test/java/dev/darshan/agentrouter/job/JobServiceTest.java | target/surefire-reports/dev.darshan.agentrouter.job.JobServiceTest.txt |
| Concurrent claim yields one winner, loser sees contention | src/test/java/dev/darshan/agentrouter/job/ClaimContentionTest.java | artifacts/operating-envelope/raw/junit.json |
| Expired work is ineligible with zero executions | src/test/java/dev/darshan/agentrouter/job/ExpiryZeroExecTest.java | artifacts/operating-envelope/raw/junit.json |
| Envelope invariants hold at 1/4/16 clients | src/test/java/dev/darshan/agentrouter/job/OperatingEnvelopeTest.java | artifacts/operating-envelope/raw/junit.json |
| Measured submit/completion latencies per level | scripts/envelope/src/EnvelopeBench.java | artifacts/operating-envelope/raw/envelope-deterministic.json |
| Dispatch identity survives crash before persist | src/test/java/dev/darshan/agentrouter/job/DispatchTest.java | target/surefire-reports/dev.darshan.agentrouter.job.DispatchTest.txt |
| Reconciliation never starts replacement work | src/test/java/dev/darshan/agentrouter/job/ReconcileTest.java | target/surefire-reports/dev.darshan.agentrouter.job.ReconcileTest.txt |
| Fencing blocks a live owner takeover | src/test/java/dev/darshan/agentrouter/job/FencingTest.java | target/surefire-reports/dev.darshan.agentrouter.job.FencingTest.txt |
