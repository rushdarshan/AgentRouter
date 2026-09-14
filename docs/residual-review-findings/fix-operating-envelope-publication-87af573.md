## Residual Review Findings
Source: ce-code-review 20260914-235644-envelope on branch fix/operating-envelope-publication (87af573) vs origin/main
Plan: docs/plans/2026-09-15-002-fix-operating-envelope-study-publication-plan.md
Run artifact: .context/compound-engineering/ce-code-review/20260914-235644-envelope/

No open PR for fix/operating-envelope-publication; findings durably recorded here (no_sink, inlined verbatim).

- **P1** \src/test/java/dev/darshan/agentrouter/job/OperatingEnvelopeTest.java:93\ — Tautological assertion (manual → downstream-resolver)
- **P1** \src/test/java/dev/darshan/agentrouter/job/OperatingEnvelopeTest.java:67\ — Hardcoded busy/rejected counts (manual → downstream-resolver)
- **P1** \src/test/java/dev/darshan/agentrouter/job/OperatingEnvelopeTest.java:46\ — Conflated latency columns (manual → downstream-resolver)
- **P1** \src/test/java/dev/darshan/agentrouter/job/OperatingEnvelopeTest.java:26\ — Single shared store, no real STORE_BUSY under load (gated_auto → downstream-resolver)
- **P2** \src/test/java/dev/darshan/agentrouter/job/OperatingEnvelopeTest.java:84\ — Tiny samples, no repetitions/raw outputs (manual → downstream-resolver)
- **P2** \rtifacts/operating-envelope/ENVELOPE.md:3\ — Model-only bottleneck, no measured deltas (manual → downstream-resolver)

All 6 findings are \
o_sink\; no tracker filing attempted (PR absent → fallback file is the durable record). Provisioned 1/4/16/64 runs with raw outputs should close P1s/P2s.