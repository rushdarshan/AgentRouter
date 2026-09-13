## Residual Review Findings
Source: ce-code-review 20260914-031747-a3f9c2e1 on branch feat/e2e-demo (bb2f744) vs origin/main
Plan: docs/plans/2026-09-14-001-feat-upgrades-enough-gates-plan.md (inferred absent at review — plan lives outside AgentRouter repo, filed as no_sink)
Run artifact: .context/compound-engineering/ce-code-review/20260914-031747-a3f9c2e1/

No GH issue sink attempted in this session (PR absent, hasIssuesEnabled=true but defer routed to durable fallback file per pipeline step 6). Findings durably recorded here.

- **P1** \src/test/java/dev/darshan/agentrouter/job/ExpiryZeroExecTest.java:24\ — Expiry test does not exercise expiry path (gated_auto → downstream-resolver)
- **P2** \docs/e2e-demo-matrix.md:1\ — e2e demo matrix is placeholder without rows (manual → downstream-resolver)
- **P2** \scripts/repro-proxy.sh:3\ — Proxy and verify shims have no executable test (gated_auto → downstream-resolver)
- **P2** \scripts/frozen-input.json:4\ — frozen-input.json commits placeholder digests (gated_auto → downstream-resolver)
- **P2** \README.md:156\ — README references plan file absent on disk (manual → downstream-resolver)

All 5 findings are \
o_sink\ inlined verbatim; no tracker filing was attempted (ny_sink_available=true via gh but PR absent → fallback file is the durable record). Next provisioned run with Docker + verified Guard should replace placeholders and exercise real expiry path.