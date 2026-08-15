# Agent SDK Hardening Checklist

- [x] Tool execution state is instantiated per router invocation and released through `AutoCloseable`.
- [x] Per-turn tool-call, execution-step, and per-tool failure limits are enforced.
- [x] Tool parameters are validated against the published JSON schema before invocation.
- [x] Runtime tool failures are contained at the SDK boundary.
- [x] Model-visible tool results use the standard `ToolResponseEnvelope` JSON contract, including room-delivery acknowledgements.
- [x] Tool invocations run in virtual threads with a cancellable configured timeout.
- [x] Independent `maxSteps`, `maxToolCallsPerTurn`, and `toolTimeoutMillis` configuration is available.
- [x] Descriptor metadata includes idempotency, a timeout override, and a result schema while retaining the legacy constructor.
- [x] Tool result data is validated against the descriptor result schema before it becomes an observation.
- [x] All execution exceptions and validation failures are converted to coded error observations.

## Envelope

Successful observations serialize as `{"status":"success","data":...}`. Failures serialize as
`{"status":"error","data":null,"error":{"code":"...","message":"..."}}`.

## Execution Model

`DefaultAgentRouter` creates an `AgentExecutionState` and `AgentToolExecutor` for every request.
The state limits model/tool iterations and calls, while the executor owns a request-local virtual-thread
executor and cancels timed-out work. Future parallel execution may only schedule descriptors marked
`isIdempotent`; Saturn currently retains deterministic sequential tool execution.
