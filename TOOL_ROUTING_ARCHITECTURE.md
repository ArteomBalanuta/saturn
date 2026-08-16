# Tool Routing Architecture

> This is the concise execution-policy reference. See `AGENTIC_ARCHITECTURE.md` for the complete
> component, context, and tool-contract guide.

## Planning Boundary

Saturn uses the provider's existing array of function calls as one planning turn. The system policy
asks the model to decompose compound requests, emit independent lookups together, observe all
results, then request dependent work on a later turn. This removes unnecessary LLM round trips
without exposing hidden reasoning or adding a separate planner protocol.

## Tool Contract Rules

Every `AgentToolDescriptor` publishes its JSON parameter schema, examples, negative guidance,
effect, result mode, timeout, prerequisites, `read_only`, and `idempotent` metadata. `read_only`
is derived from `ToolEffect.READ_ONLY`. Legacy read-only descriptors are idempotent by default;
tools with an action effect remain non-idempotent unless explicitly declared otherwise.

Every `saturn_*` command tool and the legacy `run_command` compatibility bridge are ordered action
tools. Weather and time are not treated as read-only because command execution can deliver room
output. Database SQL also remains ordered because its contract requires a successful schema
inspection first.

## Execution Pipeline

1. `DefaultAgentRouter` receives an LLM response containing zero or more tool calls.
2. `AgentToolCallValidator` resolves contextual contracts and validates parameters.
3. `AgentToolExecutionLedger` reserves invocation keys and checks limits and prerequisites.
4. `AgentToolExecutor.executeAll` delegates batching to `AgentToolCallScheduler`.
5. A batch fans out only when every call is read-only, idempotent, and has no prerequisite.
6. All other calls execute one at a time in the original LLM order.
7. Results are collected in original call order and serialized as `ToolResponseEnvelope`
   observations before the next LLM turn.

This preserves command ordering. A sequence of `room_users`, `room_users`, and `run_command`
may run the two room lookups concurrently, but `run_command` starts only after both observations
complete. A sequence containing `run_command`, `room_users`, and `room_users` runs the command
first, then fans out the two read-only lookups.

## Safety Bounds

The router enforces `maxSteps` and `maxToolCallsPerTurn`. The executor validates arguments before
dispatch, applies each tool's timeout, deduplicates in-flight and completed calls, validates result
schemas, and converts every failure into a coded observation. Failed calls do not crash the loop;
the next model turn can retry with different arguments or produce a degraded answer.
