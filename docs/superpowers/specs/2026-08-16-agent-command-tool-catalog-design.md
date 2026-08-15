# Saturn Agent Command Tool Catalog Design

## Goal

Expose every Saturn command handler to the agent as a separately named, strictly validated tool
without duplicating Saturn's command-dispatch, authorization, or output behavior.

## Scope

This change covers command handlers discovered through `@CommandAliases` under
`org.saturn.app.command.impl`, the agent tool registry, tool contracts, command dispatch bridge,
agent prompts, tests, and agent documentation. It does not change the behavior of a command when a
human invokes it directly.

## Decisions

- The catalog contains one entry for every command handler, not one entry for every alias. Each
  entry exposes the handler's primary/canonical alias to the LLM and documents every human alias in
  the inventory.
- Every catalog entry becomes one provider-visible `AgentTool` with an explicit JSON parameter
  schema, usage guidance, negative guidance, effect metadata, idempotency declaration, and timeout.
- A shared `SaturnCommandTool` implementation validates structured arguments, renders a canonical
  command text, and invokes `SaturnCommandGateway`. It is not a second command parser.
- Command tools are action tools and execute sequentially, including informational commands, since
  Saturn commands may write a room or whisper response. Existing persistence and directory tools
  retain their current read-only parallelism behavior.
- `ADMIN_COMMANDS` is a new capability. It is granted only to a direct invocation whose `trip`
  equals `[agent-participation].creatorTrip`. It is never granted to moderators, ambient turns, or
  autonomous moderation turns.
- Existing `MODERATION_COMMANDS` and `PERMANENT_BAN` gates remain in force. A command's catalog
  capability must be present before the tool is advertised and before it can execute.
- Saturn's existing command authorization remains the final execution authority. A tool contract
  never elevates a caller beyond the command's `getAuthorizedRole()` behavior.

## Architecture

### Catalog

`SaturnCommandToolCatalog` is the single source of truth for command-tool metadata. Each immutable
entry includes:

- tool name, canonical command alias, handler class, and all user-facing aliases;
- required `AgentCapability` values;
- a closed JSON-object parameter schema with required properties;
- argument rendering order, including optional and free-text fields;
- `ToolEffect`, `ToolResultMode`, idempotency, and timeout;
- description, usage constraints, negative constraints, and at least one structured example.

The catalog validates at startup that every reflected `@CommandAliases` handler has exactly one
entry and that no catalog entry references an unknown canonical alias. This makes missing commands
a deterministic startup/test failure rather than an LLM-routing gap.

### Shared Command Tool

`SaturnCommandTool` wraps one catalog entry. Its descriptor is built solely from catalog metadata.
The executor performs generic schema validation first; the tool performs catalog-specific semantic
checks such as nonblank required text, allowed mode values, and moderation-target restrictions.
It renders only the canonical command plus validated arguments before calling
`SaturnCommandGateway.executeWithResult`.

The tool maps success to the standard model observation envelope and maps rejected dispatch to a
coded error. It never manufactures command output, alters output queues, or bypasses
`UserCommandBaseImpl`.

### Registration And Visibility

`AgentRuntimeFactory` registers all catalog-derived tools alongside the existing database and room
tools. `AgentToolRegistry.definitions(context)` naturally filters entries through
`isAvailableTo(context)`, so a non-creator never receives admin definitions and cannot invoke one
through a provider-crafted request.

`AgentInvocationFactory` is the only component that grants `ADMIN_COMMANDS`; the condition is
creator trip plus `DIRECT` mode. No caller can receive it through an alias, room state, or model
instruction.

## Tool Categories

| Category | Examples | Capability | Execution |
| --- | --- | --- | --- |
| User and information | `help`, `weather`, `notes`, `mail` | None | Sequential action |
| Moderator | `captcha`, `kick`, `mute`, `register` | `MODERATION_COMMANDS` | Sequential action |
| Permanent moderation | `ban`, `unban`, `unbanall` | `PERMANENT_BAN` | Sequential action |
| Creator administration | `restart`, `shutdown`, `prefix`, replica, access, memory, SQL | `ADMIN_COMMANDS` | Sequential action |
| DBZ | `dbzhelp`, `dbzregister`, `dfight` | None unless command authorization requires more | Sequential action |

Commands requiring a target nick will use a named `target` property. Commands with a text tail use
a named `message`, `reason`, `query`, or `arguments` property rather than a generic unstructured
string. The catalog records command-specific schemas so the provider can choose the right contract.

## Error Handling

- Missing or wrong-type fields are rejected by `AgentToolSchemaValidator` before dispatch.
- Semantic invalidity is returned by `SaturnCommandTool` as a standard error result.
- Capability denial keeps the tool out of provider definitions and returns `UNKNOWN_TOOL` at the
  executor boundary for an injected call.
- Command dispatch failure is returned as an error observation; it does not throw out of the agent
  loop.
- Tool calls remain subject to the existing request-level step, call, duplicate, failure, and
  timeout limits.

## Testing

- Add a catalog coverage test that reflects every command handler and asserts one catalog entry per
  handler, canonical alias membership, and no duplicate tool names.
- Add schema tests for every catalog entry, including closed-object, required-field, and example
  validation.
- Add capability-visibility tests proving user, moderator, creator, ambient, and moderation
  contexts receive only their permitted definitions.
- Add dispatch tests for representative argument shapes in each category and exhaustive dispatch
  rendering tests for every catalog entry using a recording gateway.
- Retain gateway integration tests to prove successful command results and rejected commands produce
  the standard response envelope.

## Documentation

`COMMAND_TOOL_INVENTORY.md` will list every handler, canonical alias, human aliases, agent tool,
parameter schema synopsis, capability, effect, idempotency, and execution rule. The agentic
architecture and routing documents will explain that command coverage is catalog-driven, all command
tools are ordered, and creator administration is direct-invocation-only.

## Non-Goals

- Refactoring existing command handlers or changing their aliases, authorization, output, or
  persistence semantics.
- Granting moderators creator administration authority.
- Making command tools parallel or idempotent.
- Replacing the existing database, room-directory, or dynamic-SQL tools.
