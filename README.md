# AgentRouter

AgentRouter is a Java agent tool orchestration framework that routes user intents to appropriate tools, executes them with fault tolerance, and reports results with comprehensive observability. The architecture directly mirrors Salesforce Agentforce's orchestration model.

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
*   **Testing & CI/CD**: Delivered a production-ready codebase with 85%+ test coverage across 35+ unit and integration tests, automated via GitHub Actions and enforced by JaCoCo.
