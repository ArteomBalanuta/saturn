# Saturn Agent Runtime Design

## Objective

Build a lightweight, production-shaped agent SDK for Saturn. The `*l <prompt>` command routes a request to an OpenAI-compatible endpoint, lets the model inspect bounded room and database state, permits explicitly allowlisted Saturn command execution under the original user's authorization, and returns a safe chat response.

## Architecture Ledger

| Decision | Choice | Reason |
|---|---|---|
| Entry point | `AgentService.submit(AgentInvocation)` | Chat commands should not know transport or tool-loop details. |
| Router | `AgentRouter` orchestrates provider calls and tools | Mirrors ACE's executor while remaining Saturn-sized. |
| Provider boundary | `LlmClient` port plus OpenAI adapter | Provider JSON and HTTP do not leak into orchestration. |
| Endpoint model | Omit `model` unless configured | The supplied endpoint is the routing source of truth. |
| Context | Immutable `AgentContext` with minimum capabilities | Avoid exposing the full mutable engine to every tool. |
| Tools | Immutable registry plus executor/policy layer | Definition, lookup, validation, limits, and invocation are separate. |
| Database access | Named prepared read-only queries | No model-generated SQL and no shared JDBC connection on virtual threads. |
| Saturn commands | Explicit allowlist and original authorization path | Prevent privilege escalation and recursive `l` execution. |
| Memory | SQLite conversation history keyed by trip, then hash, with TTL and turn limit | Useful continuity without trusting nicknames as identity. |
| Limits | Prompt/output/tool-call/per-tool/duplicate/time limits | Stop loops and resource exhaustion. |
| Failure behavior | Typed errors internally, short stable chat messages externally | Logs retain detail without leaking infrastructure data. |
| Limit exhaustion | Final provider call without tools | Give the user a coherent answer from accumulated results. |
| Concurrency | Bounded executor with virtual worker threads | Do not block websocket dispatch or admit unbounded requests. |
| Observability | Correlation ID, durations, tool outcomes, no prompt/key logging | Debuggable without leaking content or credentials. |

## Components

`AgentService` accepts an invocation and schedules it. `DefaultAgentRouter` builds the conversation from system prompt, memory, and current prompt; calls `LlmClient`; delegates tool calls to `AgentToolExecutor`; persists the completed turn; and returns `AgentResult`.

`OpenAiCompatibleClient` owns request/response JSON, HTTP headers, status validation, timeout, and bounded retry with backoff for I/O, 429, and 5xx failures. Malformed successful responses are protocol errors and are not retried indefinitely.

`AgentToolRegistry` contains immutable `AgentTool` definitions. `AgentToolExecutor` validates JSON arguments, rejects unknown tools, deduplicates identical calls, limits calls per tool and per invocation, disables repeatedly failing tools for the invocation, and produces structured tool result messages.

Concrete tools are `room_users`, `database_query`, and `run_command`. `database_query` accepts only a query identifier and typed parameters. Initial named queries cover message count, registered-user count, recent messages by trip, and known nicknames by trip. Sensitive columns are not exposed. `run_command` is limited to non-destructive commands and reuses Saturn's existing authorization dispatch.

`SqliteAgentMemoryStore` and `SqliteAgentQueryRepository` open dedicated SQLite connections from the configured database path. Memory rows use a schema migration and are pruned by expiry. The memory store limits messages loaded into any request.

## Request Flow

1. `LUserCommandImpl` validates a nonblank prompt and creates an invocation from the incoming message.
2. `AgentService` admits the request to a bounded executor or returns a busy response.
3. The router assigns a correlation ID, loads memory, builds a bounded request, and calls the provider.
4. Tool calls are validated and executed; assistant and tool messages are appended.
5. Duplicate/error-only loops or call exhaustion trigger one final call with tools removed.
6. Output is validated and truncated to the configured chat-safe maximum.
7. The user and assistant turn are persisted, then the result is queued through `OutService`.

## Configuration

`[agent]` supports `enabled`, `endpoint`, optional `model`, `apiKeyEnv`, `timeoutSeconds`, `maxConcurrentRequests`, `maxToolCalls`, `maxCallsPerTool`, `maxToolFailures`, `maxPromptChars`, `maxOutputChars`, `memoryTurns`, `memoryTtlHours`, `maxRetries`, and `retryBackoffMillis`. API keys are read from the named environment variable; committed TOML does not contain secret values.

## Error Handling

Configuration is validated at startup. Busy, timeout, provider, protocol, tool, and persistence failures are distinct internally. A tool failure is returned to the model as data and does not abort the invocation unless policy limits are exceeded. Memory persistence failure is logged but does not discard a successful answer.

## Testing

Unit tests cover configuration defaults/validation, registry immutability, tool argument validation and limits, duplicate suppression, router tool-loop termination, no-tools finalization, OpenAI request/response mapping, retry classification, memory TTL/turn limits, named-query restrictions, and command authorization. Integration tests use an in-process HTTP server and temporary SQLite database; no external endpoint is called by tests.

## Explicitly Deferred

ACE DSL parsing, UI surfaces, MCP/A2A servers, multi-provider fallback, vector memory, human elicitation, and token streaming are outside Saturn's PoC. Their ports can be added later without changing the command or tool contracts.
