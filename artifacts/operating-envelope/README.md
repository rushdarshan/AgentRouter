# Operating envelope runs

One row per (concurrency, repetition). Backend tier labeled on every row.

- `deterministic`: `InMemoryBackendRuntime` via `OperatingEnvelopeTest` (offline, always available).
- `docker`: actual Docker executions only; `UNAVAILABLE` when Docker/Guard absent — never `FAIL`, never substituted.
- Columns never merged: `submit_ms` (HTTP acceptance + status read) vs `backend_ms` vs `e2e_ms` (completion).
- `rejected` reported beside latency (a latency win via higher rejection is invalid).
- `p99` only with stated sample size; small-sample p99 marked unstable or omitted.
- Single-machine results are not enterprise claims.

Raw outputs live beside `ENVELOPE.md` once provisioned runs execute.
