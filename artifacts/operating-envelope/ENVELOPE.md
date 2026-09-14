# Operating envelope (deterministic tier, single machine)

Date: 2026-09-15. Raw: `artifacts/operating-envelope/raw/envelope-deterministic.json`
(3 reps per level; 64-client level skipped by capacity gate — rerun with `--full`).

## Measured levels (workflowQueue=128 for the sweep)

| clients | n | submitP50 | statusP50 | dispatchP50 | e2eP50 (completion) | accepted | rejected |
|---------|---|-----------|-----------|-------------|---------------------|----------|----------|
| 1 | 24 | 1ms | 0ms | 3ms | 6ms | 24 | 0 |
| 4 | 48 | 4ms | 1ms | 11ms | 18ms | 48 | 0 |
| 16 | 96 | 15ms | 12ms | 49ms | 76ms | 96 | 0 |

Submission (acceptance) and completion are separate columns, never merged.
No p99 is reported: with n of 24–96 a p99 is a single sample, not a stable
characteristic. Rejected counts sit beside every latency row, so no latency
number is obtained by rejecting more requests.

## Saturation probe (default bound, workflowQueue=4)

6 submits → 4 accepted, 2 rejected with 429. The bound sheds load by design.

## Contention and expiry probes

Cross-connection `BEGIN IMMEDIATE` while a claim is attempted yields
`STORE_BUSY`; after rollback the claim commits (`claimedAfterRollback`).
A terminal (`COMPLETED`) operation claims `INELIGIBLE` with 0 executions.

## What fails first and why

Dispatch fails first: at 16 clients dispatch p50 (49ms) dominates end-to-end
(76ms) while submission stays at 15ms, because claims serialize on the
synchronized single-connection store (`BEGIN IMMEDIATE`, `busy_timeout = 0`).
Status reads degrade alongside (12ms) since `find` shares the same monitor.
The first binding constraint under higher load is the claim write rate, with
the 429 admission bound as the designed shed — not a failure.

## Improvement decision

No code change in this increment: pooling or a writer split would move the
single-instance safety boundary the fencing design depends on, without measured
demand that the claim rate (rather than the workload) is binding.
