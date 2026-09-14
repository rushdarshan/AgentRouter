# Operating envelope (deterministic tier, single machine)

Date: 2026-09-15 (harness landed; provisioned numbers pending — this file states the bottleneck model, not measured facts).

## Bottleneck explanation

`SqliteJobStore.claim` is `synchronized` on a single SQLite connection with
`PRAGMA busy_timeout = 0` + `BEGIN IMMEDIATE`. Under concurrent dispatch,
claim attempts serialize on the Java monitor first, then on the SQLite write
lock: losers observe `STORE_BUSY` (contention, retryable at a higher layer)
rather than queueing. Throughput therefore saturates at the claim write
transaction rate while submission acceptance stays fast — submission latency
and completion latency diverge by design. This is the deliberate single-instance
trade-off, not a regression.

## Improvement decision

No code change warranted in this increment: any pooling/writer-split would move
the single-instance safety boundary the fencing design depends on, without
measured demand. Revisit only if provisioned 1/4/16/64 runs show the claim
rate — not the workload — is the binding constraint with the dispatch
invariant intact before/after.
