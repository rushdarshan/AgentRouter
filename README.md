# AgentRouter

AgentRouter is a Java agent tool orchestration framework that routes user intents to appropriate tools, executes them with fault tolerance, and reports results with comprehensive observability. It implements its own deterministic 4-node pipeline plus a durable `/jobs` scientific-workflow path backed by a single-instance SQLite store with claim fencing — a modest single-machine design with explicit limits (see Operating envelope), not an enterprise-scale platform.

## Core Architecture

AgentRouter implements a deterministic 4-node pipeline inspired by LangGraph's StateGraph pattern, with a separate error handler branch:

```text
ResolveIntent → ValidateInput → ExecuteTool → ReportResult
                                      ↓
                                 ErrorHandler (on any failure)
```

Each node is a pure function (`ExecutionContext → ExecutionContext`). State flows through a single immutable-ish context object. On any error at any stage, execution branches to `ErrorHandler` rather than relying on scattered exception propagation.

### Key Components

*   **StateGraph**: Orchestrates the 4+1 node pipeline.
*   **IntentClassifier**: Strategy pattern (currently Keyword/Regex based) for classifying user intents. Pluggable design allows easy swapping to an LLM-based classifier.
*   **ToolRegistry**: Agent discovery and lookup registry by name, intent, or domain capability.
*   **SchemaValidator**: Pure gate enforcing tool input contracts (required fields, types, enum constraints) prior to execution.
*   **CircuitBreaker**: Wraps tool executions to prevent cascading failures. Implements a CLOSED → OPEN → HALF_OPEN state machine.
*   **MetricsCollector**: Thread-safe observability collector for recording tool latencies (P50/P99), error rates, and circuit state transitions.

## Example Tools Included

1.  **WeatherTool**: Simulated weather lookup supporting 10 US city codes.
2.  **CRMQueryTool**: Simulated SOQL-style customer database query.
3.  **CalculatorTool**: Basic arithmetic expression evaluator.

## REST API

### Route Request (`POST /route`)

**Request:**
```json
{
  "request": "What's the weather in San Francisco?"
}
```

**Response:**
```json
{
  "success": true,
  "intent": "weather_query",
  "tool": "WeatherTool",
  "result": {
    "temp": 65,
    "forecast": "foggy",
    "location": "San Francisco, CA"
  },
  "metrics": {
    "tool_name": "WeatherTool",
    "latency_ms": 42,
    "timestamp": "2026-04-25T13:45:00Z"
  }
}
```

### Health & Metrics (`GET /health`)

Returns circuit states, call counts, P50/P99 latencies, and error rates for all registered tools.

## Getting Started

### Prerequisites
*   Java 17+
*   Maven

### Build and Test

```bash
# Compile, run unit and integration tests, and generate JaCoCo coverage report
mvn clean verify
```

### Run Locally

```bash
# Start the Spring Boot application on port 8080
mvn spring-boot:run
```

## Resume Highlights

*   **Orchestration Architecture**: Designed a deterministic, LangGraph-inspired 4-node Java state graph for agent tool routing, mirroring Salesforce Agentforce's orchestration model and enabling observable execution state.
*   **Fault Tolerance**: Implemented a configurable Circuit Breaker pattern preventing cascading failures across distributed agent tools, tracking state transitions (CLOSED/OPEN/HALF_OPEN).
*   **System Design & Patterns**: Applied Strategy, Chain-of-Responsibility, and Registry design patterns to build a modular intent classifier and tool execution pipeline.
*   **Testing & CI/CD**: JaCoCo enforces the agreed 85% line threshold; the measured result is reported by `mvn verify`.
## Reliable scientific workflow contract

The `/jobs` contract is the durable workflow path. It uses caller-supplied
UUIDv4 operation identities, principal-scoped idempotency keys, typed states
and outcomes, and a single-instance SQLite store with an active-service fencing
row. Production authentication requires a configured signed JWT contract
(issuer and secret); literal test credentials are available only through
explicit test fixtures. Caller-supplied `principalId` values are ignored.

### Dispatch eligibility

`JobService.dispatch()` rejects operations before any backend side effects
when the operation is terminal (`COMPLETED`, `FAILED`, `CANCELLED`) or has a
pending cancellation request. This prevents dispatch of expired, cancelled,
or already-finished work.

### Durable execution history

Creation and start progress are durably persisted in the `dispatch` table as
they occur via `confirmDispatchProgress()`. The execution path records
backend history atomically at each stage, not after the fact from a snapshot.
This prevents duplicate execution after an original backend becomes
undiscoverable.

### Container exit handling

`DockerBackendRuntime.inspect()` reads the container exit code and maps
`EXITED` with non-zero exit to `FAILED`, not `COMPLETED`. The
`BackendObservation` record carries the exit code so callers can distinguish
successful from failed container exits.

### Independent verification

`repro.sh --verify` independently reconstructs the Guard verdict from
evidence in `final-state.json` (router outcome, backend state, artifacts
availability) using the same consistency mapping as `GuardEvaluator.java`,
then compares the derived verdict against the stored one. A fixture with
mismatched stored and derived verdicts is rejected.

### Docker bounded execution

All Docker subprocess calls use a 30-second timeout and 1 MiB output cap.
Processes that exceed the timeout are forcibly destroyed. Output exceeding the
bound is truncated.

### Canonicalization

`sci-c14n-v1` follows RFC 8785 number spelling. Exponent signs are preserved:
positive exponents receive an explicit `+`, already-signed exponents are
unchanged.

### Reproducibility

The real RooFit and Guard tier is intentionally not simulated. Run
`./repro.sh scientific-workflow-reliability-v1` to receive explicit
`UNAVAILABLE` diagnostics when Docker, the configured Guard checkout, or the
pinned importer/runtime is absent. `./repro.sh ... --verify <run-directory>`
is read-only evidence verification. Missing frozen-input, Guard results, or
evaluator metadata is rejected; it is never reported as a passing evaluation.

The Docker boundary is service-owned and fixed: `DockerBackendRuntime` uses the
configured pinned image and entrypoint, creates an identity-labelled container
with `--network none`, and rejects caller image/command/mount overrides. Docker
or image unavailability is reported as `UNAVAILABLE`; no in-memory result is
substituted for RooFit. A proven-dead SQLite owner may be replaced only after
the operating system proves its recorded PID is gone; heartbeat age alone never
permits takeover.

## Upgrades enough — five gates

| Gate | Command | Honest result |
|------|---------|---------------|
| Reproducible | `./repro.sh scientific-workflow-reliability-v1` → `artifacts/.../<run-id>/`; `repro.sh --verify <run-id>` read-only | `UNAVAILABLE` when Docker/Guard absent |
| Correct under faults | `mvn -pl AgentRouter -am test -Dtest=ClaimContentionTest,ExpiryZeroExecTest` (real service + SQLite path, not helpers) | offline helper-only would FAIL gate |
| Auditable | `manifest.json` + `events.jsonl` + `final-state.json` + `frozen-input.json` (+ `report.txt`) per run, headlined numbers resolve to raw files + protocol | inventory/`*.sha256` scopes declared |
| Explainable | `docs/plans/2026-09-14-001-feat-upgrades-enough-gates-plan.md` U1-U4 + `SqliteJobStore.claim` `BEGIN IMMEDIATE` design | diagnose via `events.jsonl`/`final-state.json` |
| Honestly presented | tiers `PASS` (only PASS satisfies) / `UNAVAILABLE` / `EXPERIMENTAL` in this section | next run remains `NOT_RUN` until provisioned |

## Verification status (dated 2026-09-15)

Verified commit: `2a08636` on `main` (merge of `feat/e2e-demo`; CI: `.github/workflows/ci.yml` on GitHub Actions).
Test/report links: `https://github.com/rushdarshan/AgentRouter/actions` and `https://github.com/rushdarshan/AgentRouter/tree/main/artifacts`.

| Environment | Result |
|-------------|--------|
| CI (GitHub Actions, `mvn verify`) | per run badge/logs — only `PASS` satisfies |
| Local offline (JDK direct compile, focused JUnit) | `ClaimContentionTest`, `ExpiryZeroExecTest` patterns verified; no new coverage % claimed |
| Docker / external Guard importer / real RooFit tier | `UNAVAILABLE` here (reported, never substituted) |

Operating envelope: `artifacts/operating-envelope/ENVELOPE.md` (deterministic tier harness `OperatingEnvelopeTest` at 1/4/16 clients, 64 gated by machine capacity; Docker tier labeled separately). The acceptance matrix stays `NOT_RUN` for unexecuted rows; `UNAVAILABLE` for absent provisioned tiers. Tiers: `PASS` (only `PASS` satisfies) / `UNAVAILABLE` / `EXPERIMENTAL`.
