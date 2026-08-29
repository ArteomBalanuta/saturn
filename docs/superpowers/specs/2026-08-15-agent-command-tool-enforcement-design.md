# Agent Command Tool Enforcement

## Problem

Vaelen sometimes writes a Saturn command as Markdown, such as `` `weather charlotte` ``, instead
of returning an OpenAI-compatible `run_command` tool call. The chat client then renders a gray code
pill, but Saturn never executes the command. Runtime logs confirm these turns contain no completed
tool call. The configured endpoint does support structured tool calls when explicitly instructed.

## Decision

Saturn will treat command-shaped assistant prose as an invalid routing response. It will never
strip Markdown and execute the remaining text because model-authored prose is not a trusted command
channel.

The router will use a small command-prose guard derived from the `run_command` definition exposed
for the current caller. When a completion has no tool calls but contains an inline or fenced code
snippet whose first token is an allowed Saturn command, the router will:

1. Keep the invalid response out of chat and conversation memory.
2. Add a corrective message asking the provider to classify the wrapped command as immediate intent
   or a reference, report, example, conditional, or future action.
3. Request one more completion with only the caller-filtered `run_command` definition and an
   internal, non-executable `respond_without_command` response tool.
4. Continue the normal tool loop only for exactly one matching `run_command` call.
5. Return clean prose without execution only for exactly one valid `respond_without_command` call.
6. Fail the turn for ordinary prose, a different command, extra tool calls, malformed arguments, or
   another wrapped command.

The runtime system policy will also explicitly require `run_command` for live weather and time
requests and prohibit printing, quoting, or fencing a command as a substitute for a tool call.
Conditional and future requests are not immediate commands, and the agent must not claim a watcher,
rule, or scheduled action exists unless an exposed tool created it.

## Execution Priority

Saturn's runtime policy will put task completion ahead of Vaelen's persona. For a clear request
that an exposed tool can fulfill, the agent must resolve references from shared history, call the
tool immediately, and report the actual result briefly. It must not replace the action with
mockery, a lecture, philosophy, roleplay, a promise to act later, or an unnecessary confirmation.

The Vaelen persona resource will reinforce the same hierarchy: calm mentor styling is optional and
secondary to execution. The agent may ask one concise question only when a required argument cannot
be recovered from the request, shared history, recent room messages, or prior tool results. It must
not re-ask a question that the available context already answers.

## Boundaries

- Only command names present in the current invocation's `run_command` enum are recognized.
- Single- or multi-backtick inline spans and backtick- or tilde-fenced blocks are inspected.
- Plain code examples that do not begin with an exposed Saturn command remain unchanged.
- No assistant text is parsed and executed directly.
- `respond_without_command` exists only in the corrective request and is never registered with the
  executable Saturn tool registry.
- Existing authorization, tool-call budgets, duplicate detection, and command gateway behavior
  remain authoritative.
- Post-tool cleanup is tied to the exact successful command name; one command's success cannot make
  a different command appear to have executed.
- The corrective retry is limited to one completion per routed request.

## Shared Context And Memory

Public `DIRECT`, `MENTION`, and `AMBIENT` invocations receive recent public room-message context.
Private whispers retain their separate memory key and never hydrate from the public room transcript.
Public memory remains keyed by room, so all participants share the same persisted conversation.

Memory load or append failures abort the routed request rather than answering statelessly or
returning an answer that was not persisted. Operational logs identify failures by correlation ID
without logging memory keys or conversation content.

## Stale Endpoint Responses

If a different user prompt receives the exact previous assistant answer, Saturn treats the result
as a stale llama.cpp slot-cache completion. It retries once with an explicit newest-message
correction and `cache_prompt` disabled. The normal request remains fully OpenAI-compatible and does
not send the llama.cpp-specific option. A repeated stale response fails the route and is neither
published nor persisted; repeating the same user prompt may legitimately receive the same answer.

## Error Handling

If correction fails, the router raises an `AgentRoutingException`. Required invocations receive the
existing generic failure message; ambient invocations remain silent. The rejected prose is neither
sent nor persisted.

## Verification

Tests will prove that:

- A Markdown-wrapped allowed command triggers correction and then executes only through
  `run_command`.
- The wrapped pseudo-command is never returned or persisted.
- A second invalid completion fails without executing text.
- A matching correction with any piggybacked tool call fails before either call executes.
- A wrapped command reference can be rewritten without executing any Saturn command.
- Corrective calls must match their published field sets and string types exactly.
- A later wrapped command cannot inherit an earlier command's successful execution state.
- A failed command status remains a tool failure, and post-failure cleanup cannot claim execution.
- Multi-backtick inline spans and tilde-fenced commands cannot bypass correction.
- Unrelated inline code remains valid assistant content.
- Capability-restricted commands cannot be introduced through corrective routing.
- Public direct invocations include recent room messages, while private direct invocations do not.
- A new prompt cannot publish the exact previous answer without one cache-bypassing retry.
- A stale retry that repeats the old answer fails without appending agent memory.
- A completion cannot claim a lookup or command execution without a real tool call; one correction
  pass must produce the tool call or an honest non-action answer.
- Memory read and append failures cannot produce an apparently successful stateless turn.
- The action-first runtime policy appears before the persona and explicitly forbids substituting
  dialogue, mockery, or repeated questions for tool execution.
- The complete Maven suite and formatting checks pass.
