# Operating-envelope architecture

```mermaid
flowchart LR
    C[concurrent clients 1/4/16] --> POST[POST /jobs]
    POST --> ACCEPT[accept TX: idempotency + queue bound]
    ACCEPT -->|202| FIND[GET /jobs/id status read]
    ACCEPT --> CLAIM[claim: BEGIN IMMEDIATE + conditional UPDATE]
    CLAIM -->|STORE_BUSY| RETRY[caller retries]
    CLAIM -->|CLAIMED| BACKEND[backend create + start]
    BACKEND --> OBS[observations persisted]
    OBS --> EV[evidence package]
    EV --> GUARD[Guard evaluation]
    GUARD --> VERIFY[read-only --verify]
```

Single SQLite connection, `synchronized` store methods, `busy_timeout = 0`.
Submission (accept) and completion (claim + backend) are separate columns;
status reads share the same monitor, so they degrade with dispatch contention.

## Limitation statement

Single host, single active service, SQLite-backed. Results describe this
machine only and are not enterprise-scale claims. The Docker tier reports
`UNAVAILABLE` when Docker is absent; nothing is substituted.
