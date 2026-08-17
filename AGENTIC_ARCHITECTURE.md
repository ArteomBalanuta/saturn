# Saturn Agentic Architecture

## Executive Overview

Saturn's agentic layer turns direct `*l` requests, exact mentions, approved ambient turns, and
autonomous moderation signals into bounded OpenAI-compatible tool-calling sessions. It orchestrates
existing Saturn commands, room state, and read-only persistence queries; it is not a second command
framework.

The package root is `org.saturn.app.agent`. Provider integration lives in `agent.llm`, concrete
tools in `agent.tool`, H2-backed context and memory in `agent.persistence`, and ambient abuse
monitoring in `agent.moderation`. `AgentRuntimeFactory` constructs the runtime.

The provider selects from advertised tools. Saturn remains authoritative for capability checks,
schema validation, ordering, timeouts, result validation, room delivery, and persistence.

## Core Components

### Router And Planning

`DefaultAgentRouter` implements `AgentRouter` and owns a complete agent turn.

1. It validates prompt size and locks a striped session key. Public conversations share a room
   session; whispers use private user-and-room sessions.
2. `AgentRequestAssembler` combines the system policy, bounded memory, room context, invocation
   metadata, and the tool definitions available to the caller.
3. The provider may return zero, one, or many function calls in one response. The system policy
   asks it to decompose compound requests and emit independent lookups together.
4. The router adds results as tool observations, requests the next action or final synthesis, and
   persists successful public replies and tool evidence.

`AgentFreshDataPolicy`, `AgentCommandChannelPolicy`, and `AgentResponseCorrector` enforce fresh
grounding, structured commands, stale-response recovery, internal evidence isolation, and truthful
action claims. Persisted tool evidence is supplied as system context rather than assistant speech;
the final response boundary rejects a repeated evidence envelope before room delivery.
`AgentTurnState` owns request-local bounds; `AgentResponseSanitizer` owns presentation.

`AgentTurnPolicy` is the package-private boundary for ordered response enforcement. The router
injects `AgentFreshDataTurnPolicy`, `AgentUnverifiedActionPolicy`, and
`AgentCommandChannelPolicy` in that order. Immutable `AgentTurnPolicyInput` and
`AgentTurnPolicyResult` values make policy inputs, short-circuit decisions, and correction outcomes
explicit without allowing a policy to execute tools or persist memory. The fresh-data gate stops
later policies until the required tool succeeds.
`AgentToolBudgetPolicy` separately owns tool-call reservation and the deterministic transition to a
single no-tools finalization when the per-turn budget is exhausted.

### Execution Engine

`AgentToolExecutor` is created and closed per routed request. `AgentToolCallValidator` resolves
contextual contracts into immutable `ValidatedToolCall` values. `AgentToolExecutionLedger` owns
completed and in-flight keys, limits, failures, disabled tools, and prerequisites.
`AgentToolInvoker` owns timeout-bound virtual-thread execution, while `AgentToolCallScheduler`
owns ordered selective fan-out.

For each call the facade validates, reserves, invokes, validates the result schema, and records the
outcome. Malformed arguments, timeouts, and tool exceptions become coded observations rather than
crashing the router.
Closing the request-local executor invokes `shutdownNow()` on both scheduling and invocation
executors, interrupting in-flight tool work; `AgentToolExecutorTest` locks this cancellation
contract down.

### Room Automation And Composition

`AgentRoomMessagePipeline` applies deterministic moderation, eligibility filtering, quiet requests,
mentions, semantic moderation, and ambient participation in a fixed order.
`DefaultAgentRoomAutomation` is its public compatibility facade. `AgentRuntimeFactory` delegates to
dedicated infrastructure, tool-registry, router, and automation factories;
`ProtectedPrincipalPolicy` centralizes creator, admin, host, bot, and replica exemptions.

`AgentExecutionState` separately limits model/tool loop steps and total calls. `AgentConfig`
supplies `maxSteps`, `maxToolCallsPerTurn`, `maxCallsPerTool`, and `toolTimeoutMillis`.

## Configuration

`AgentConfig` is the typed source for provider and router settings. TOML under `[agent]` provides
checked-in defaults; non-blank `SATURN_AGENT_*` environment values take precedence. The agent
defaults to disabled and uses `http://localhost:16261` only as a safe local fallback. Enable it
only after setting `SATURN_AGENT_ENDPOINT` for the target environment.

`AgentConfigValueReader` is the shared scalar-reading boundary used by the provider, SQL,
participation, and moderation configuration records. It owns TOML fallback, non-blank environment
precedence, strict boolean parsing, long parsing, and checked integer conversion. Each configuration
record retains responsibility for domain-level positivity and cross-field invariants.

Sensitive credentials never belong in TOML. `apiKeyEnv` names the environment variable holding the
token and defaults to `SATURN_AGENT_API_KEY`. The normal deployment variables are:

`OpenAiCompatibleClient` is currently the sole provider implementation, so no provider transport
abstraction is introduced merely for symmetry. Its injectable constructor validates configuration,
serialization, and HTTP transport dependencies at the boundary; retry, timeout, interruption, and
response parsing behavior remains provider-specific and directly tested.

| Environment variable | Overrides |
| --- | --- |
| `SATURN_AGENT_ENABLED`, `SATURN_AGENT_ENDPOINT`, `SATURN_AGENT_MODEL` | Agent activation and provider selection. |
| `SATURN_AGENT_API_KEY` | Bearer token named by `apiKeyEnv`. |
| `SATURN_AGENT_TIMEOUT_SECONDS`, `SATURN_AGENT_MAX_COMPLETION_TOKENS`, `SATURN_AGENT_THINKING_ENABLED` | Provider request behavior. |
| `SATURN_AGENT_MAX_STEPS`, `SATURN_AGENT_MAX_TOOL_CALLS_PER_TURN`, `SATURN_AGENT_MAX_CALLS_PER_TOOL`, `SATURN_AGENT_MAX_TOOL_FAILURES`, `SATURN_AGENT_TOOL_TIMEOUT_MILLIS` | Bounded execution behavior. |
| `SATURN_AGENT_MAX_CONCURRENT_REQUESTS`, `SATURN_AGENT_MAX_PROMPT_CHARS`, `SATURN_AGENT_MAX_OUTPUT_CHARS`, `SATURN_AGENT_MEMORY_TURNS`, `SATURN_AGENT_MEMORY_TTL_HOURS`, `SATURN_AGENT_MAX_RETRIES`, `SATURN_AGENT_RETRY_BACKOFF_MILLIS` | Queue, payload, memory, and retry limits. |
| `SATURN_AGENT_DYNAMIC_SQL_*` | Dynamic-SQL enablement and every SQL size/time bound. |

Use [`.env.example`](.env.example) as the complete sanitized template. The conservative defaults
are five model/tool steps and a 10-second tool timeout. `AgentSqlConfig` applies the same
environment-first pattern to dynamic-SQL limits.

### Tool Contracts And Metadata

Every `AgentTool` publishes an `AgentToolDescriptor`; `AgentToolDefinitionFactory` serializes it
as an OpenAI function definition. The descriptor includes stable identity, capability/access
requirements, side effect, result-delivery mode, JSON parameter and result schemas, positive and
negative routing guidance, examples, prerequisite tools, idempotency, and timeout.

`AgentToolSchemas` centralizes the common JSON-object contract shape. Legacy SDK-compatible tools
use `object()` with `additionalProperties: true`; strict built-in contracts use `closedObject()` and
then add their declared properties and required fields. Tool-specific schema constraints remain in
the owning tool.
`AgentToolArgumentReader` centralizes trimmed, non-blank JSON string extraction for tools while
leaving required versus optional argument policy and user-facing error text with each tool.

Read-only H2 agent repositories depend on `H2ReadOnlyConnectionFactory` for connection acquisition.
The path-based constructors remain compatibility facades, while the factory-based constructors keep
connection setup centralized and make the persistence boundary injectable for focused tests.

`H2AgentMemoryStore` delegates append and tool-evidence writes to `H2TransactionExecutor`. The
executor owns auto-commit transitions, commit, rollback with suppressed rollback failures, and
restoration of the caller's original connection state; SQL binding and memory-specific cleanup stay
in the store.

Persistence error translation remains repository-specific: named queries preserve operation-specific
messages and their `SQLException` causes, memory writes include database diagnostics, schema
inspection uses a stable schema-boundary message, and validated SQL maps failures to
`AgentSqlErrorCode`. These contracts are covered independently rather than merged into a generic
mapper.

`read_only` is derived from `ToolEffect.READ_ONLY`. Legacy read-only descriptors are idempotent by
default, but new tools should declare their metadata explicitly. The model-visible result protocol
is always one of:

```json
{"status":"success","data":{}}
{"status":"error","data":null,"error":{"code":"...","message":"..."}}
```

## Tool Execution Policy

### Sequential First

Side effects and ordering are Saturn's default. `run_command` always runs sequentially, including
weather and time, because commands may deliver room output. Moderation, room messaging, writes,
and any descriptor with prerequisites retain provider order.

### Selective Read Parallelism

The executor fans out a *contiguous* batch only when every call is read-only, idempotent, and has
no prerequisite. `room_users`, `user_message_history`, and eligible named read-only queries can
qualify. Results are collected and appended to the model context in the original provider-call
order. A command before or after a read batch is an ordering barrier.

### Latency Strategy

The main optimization is allowing several independent calls in one provider response, avoiding a
provider round trip. Local virtual-thread fan-out is a safe secondary optimization; it never
relaxes action ordering.

## Multi-Tool Lifecycle

```mermaid
sequenceDiagram
    participant U as User
    participant R as DefaultAgentRouter
    participant L as LLM Provider
    participant E as AgentToolExecutor
    participant T as Saturn Tools

    U->>R: *l compound request
    R->>R: Assemble context, memory, policy, and tool definitions
    R->>L: One OpenAI-compatible request
    L-->>R: Calls [room_users, user_message_history]
    R->>E: executeAll(context, calls)
    par Independent read-only calls
        E->>T: room_users
    and
        E->>T: user_message_history
    end
    T-->>E: Results
    E-->>R: Ordered ToolResponseEnvelope observations
    R->>L: Observations in original call order
    L-->>R: Final answer or next dependent call
    R-->>U: Sanitized Saturn reply
```

For weather plus two room counts, the model can emit weather and the first lookup together. Weather
uses `run_command` and stays sequential; only an independent read-only batch can fan out. A second
room lookup is a later turn when it depends on prior observations.

## Built-In Tools

| Tool | Role | Execution Rule |
| --- | --- | --- |
| `room_users` | Live user list and count for a managed room. | Independent read-only calls may fan out. |
| `user_message_history` | Up to 500 public messages for one user with evidence metadata. | Independent read-only calls may fan out. |
| `database_query` | Named read-only application queries. | Read-only, subject to descriptor constraints. |
| `database_schema` | Admin schema inspection for dynamic SQL. | Ordered prerequisite for `database_sql`. |
| `database_sql` | Admin-only, AST-validated generated `SELECT`. | Sequential; requires schema inspection. |
| `saturn_<alias>` | One reflected Saturn command handler per named tool contract. | Always sequential; may deliver room output. |
| `run_command` | Compatibility bridge for existing routing correction and moderation automation. | Always sequential; may deliver room output. |

Tool visibility is contextual. The registry omits unavailable tools, and `RunCommandTool` publishes
only commands permitted by the current capabilities. The executor enforces the same rules again at
runtime, so provider payloads are never authorization.

## Extension Guide

For a new tool, follow [the command skill](.skills/adding-saturn-command/SKILL.md) when it invokes
Saturn commands, then implement `AgentTool`, publish a closed parameter schema and result schema,
declare effect/idempotency/timeout/capabilities, register it in `AgentRuntimeFactory`, and add
validation, ordering, timeout, and capability tests. Update resource-based tool copy under
`src/main/resources/agent/` as part of the same change.

Never mark an action tool read-only or idempotent merely because it is commonly informational. Only
a successful tool result, never model prose, proves that a lookup or command occurred.

## Hardening Coverage

The agent package uses focused contract tests for configuration parsing, tool descriptors, tool-call
validation, execution limits, fresh-data enforcement, response finalization, memory persistence, and
room automation. `AgentToolCallValidatorTest` specifically protects authorization-before-parsing
ordering and canonical invocation identity, while `AgentConfigValueReaderTest` protects shared
configuration semantics. These are additive to the router and full-suite regression tests.

Coverage claims are intentionally expressed as behavioral contracts rather than a percentage: the
Maven build currently has no JaCoCo or equivalent coverage gate configured. A future coverage gate
should be introduced separately with agreed thresholds and exclusions for generated/framework glue.

## Related Documentation

- [Tool routing rules](TOOL_ROUTING_ARCHITECTURE.md)
- [SDK hardening checklist](HARDENING_CHECKLIST.md)
- [Agentic refactoring notes](REFACTORING.md)
- [Runtime configuration](README.md#vaelen-agent)
