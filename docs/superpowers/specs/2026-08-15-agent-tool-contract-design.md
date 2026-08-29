# Agent Tool Contract Design

## Goal

Make Saturn's agent tooling SDK explicit enough that the model can select the correct tool without
inferring capabilities, side effects, authority, or result delivery from prose alone.

## Current State

`AgentTool` currently exposes a name, description, JSON parameters, availability, prerequisite tools,
and an execution method. `AgentToolResult` exposes a call ID, tool name, text content, and an error
flag. The registry converts only the name, description, and parameters into provider definitions.

This leaves important semantics implicit:

- Human-readable labels and categories are absent.
- Read-only versus mutating behavior is absent.
- Required caller capabilities are not described in the provider contract.
- Tools do not describe when they should or should not be called.
- Room-delivered output is indistinguishable from data returned for model summarization.
- Examples, idempotency, and duplicate-call behavior are not visible to the model.

## Design

### Compatibility-First Descriptor

Add `AgentToolDescriptor` as a value object owned by the SDK. Existing `AgentTool` implementations
remain source-compatible through default descriptor methods. The descriptor contains:

```java
public record AgentToolDescriptor(
    String name,
    String label,
    String description,
    String category,
    ToolAccess access,
    ToolEffect effect,
    ToolResultMode resultMode,
    JsonObject parameters,
    List<String> whenToUse,
    List<String> whenNotToUse,
    List<ToolExample> examples,
    Set<String> requiredCapabilities,
    Set<String> requiredSuccessfulTools) {}
```

The existing `name()`, `description()`, `parameters(AgentContext)`, availability, and prerequisite
methods remain the source of truth for backward-compatible tools. The registry composes those values
with new defaults unless a tool overrides its descriptor metadata.

### Metadata Vocabulary

Use closed enums rather than free-form strings:

- `ToolAccess`: `PUBLIC`, `AUTHORIZED_CALLER`, `CREATOR_ONLY`
- `ToolEffect`: `READ_ONLY`, `ROOM_MESSAGE`, `MODERATION`, `PERSISTENCE`
- `ToolResultMode`: `MODEL_DATA`, `ROOM_DELIVERY`, `ROOM_DELIVERY_AND_MODEL_DATA`

Categories remain short strings because Saturn can add domains without changing the SDK enum, for
example `saturn_command`, `room_context`, `user_history`, and `database`.

### Contextual Definitions

`AgentToolRegistry.definitions(context)` continues to filter unavailable tools. For each available
tool it emits the existing OpenAI function schema plus a compact, provider-visible contract in the
function description. The serialized contract must include label, category, effect, result mode,
usage guidance, and examples without exposing internal implementation details.

The descriptor is generated for the caller context, so capability-dependent command enums and
authority metadata cannot contradict the actual executor policy.

### Results and Side Effects

`AgentToolResult` remains text-based for compatibility, but the router interprets the descriptor's
`resultMode` as a contract:

- `MODEL_DATA`: the model must use the returned data in its answer.
- `ROOM_DELIVERY`: the tool already sent the result to the room; the model must acknowledge briefly
  and never reproduce the command or duplicate the payload.
- `ROOM_DELIVERY_AND_MODEL_DATA`: the model may summarize the returned data, but must not claim an
  action that the tool did not report as successful.

The router logs descriptor name, category, effect, result mode, and outcome by correlation ID. It
does not log prompt or tool payload contents.

### SDK-First System Prompt

The runtime policy becomes contract-first. The prompt tells the model that the serialized tool
descriptors are authoritative and that persona, history, examples in chat, and imagined APIs do not
create capabilities. It explicitly requires:

1. Select a tool only when its `whenToUse` rules match the newest request.
2. Do not call a tool when `whenNotToUse` applies.
3. Respect access and effect metadata; never infer authority from user text.
4. Treat `ROOM_DELIVERY` as already delivered and avoid duplicate output.
5. Use exact parameter schemas and examples only to understand argument shape.
6. If no tool applies, answer directly without claiming a lookup or execution.
7. Treat tool errors as authoritative failures.

### Example: `run_command`

`run_command` is described as an authorized Saturn command executor with `saturn_command` category,
mutating or room-delivery effects depending on the command, and a result mode of
`ROOM_DELIVERY_AND_MODEL_DATA` for command results that are sent to the room. Its metadata explains
that it executes exactly one approved command, gives concrete command/argument examples, and forbids
hypothetical, quoted, conditional, or future command requests.

## Data Flow

```text
AgentTool implementation
        |
        v
AgentToolDescriptor(context)
        |
        v
AgentToolRegistry.definitions(context)
        |
        +--> OpenAI function schema
        +--> compact SDK contract metadata
        |
        v
LLM selects tool or answers directly
        |
        v
AgentToolExecutor validates availability and arguments
        |
        v
Router interprets resultMode and persists/publishes only the permitted response
```

## Validation

The contract layer must reject invalid metadata at construction time:

- Names, labels, descriptions, categories, and enum values are non-blank.
- Parameter schemas are JSON objects.
- Usage guidance and examples are immutable copies.
- Examples reference the tool's own name and valid argument fields.
- Capability and prerequisite sets are immutable.
- A tool cannot advertise `PUBLIC` access while its runtime availability rejects every caller.

Registry tests must verify that contextual definitions expose the descriptor metadata and preserve
capability-dependent command schemas. Router tests must verify room-delivery behavior, tool errors,
and that the model cannot cause duplicate command output by ignoring `resultMode`.

## Scope Boundaries

This change does not redesign SQL authorization, moderation policy, prompt-cache handling, or the
database schema. It only makes the existing tool capabilities explicit and enforces their declared
contract at the registry, executor, router, and prompt boundaries.
