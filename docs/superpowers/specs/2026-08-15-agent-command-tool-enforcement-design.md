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
2. Add a corrective message explaining that commands must be emitted through `run_command`.
3. Request one more completion with the same capability-filtered tool catalog.
4. Continue the normal tool loop if the model returns a structured call.
5. Fail the turn if the corrective completion still contains command-shaped prose.

The runtime system policy will also explicitly require `run_command` for live weather and time
requests and prohibit printing, quoting, or fencing a command as a substitute for a tool call.

## Boundaries

- Only command names present in the current invocation's `run_command` enum are recognized.
- Plain code examples that do not begin with an exposed Saturn command remain unchanged.
- No assistant text is parsed and executed directly.
- Existing authorization, tool-call budgets, duplicate detection, and command gateway behavior
  remain authoritative.
- The corrective retry is limited to one completion per routed request.

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
- Unrelated inline code remains valid assistant content.
- Capability-restricted commands cannot be introduced through corrective routing.
- The complete Maven suite and formatting checks pass.
