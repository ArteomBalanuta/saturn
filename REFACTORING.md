# Agentic Package Refactoring

> This file summarizes the responsibility boundaries produced by the completed refactor. Use
> [`AGENTIC_ARCHITECTURE.md`](AGENTIC_ARCHITECTURE.md) for the current maintainer guide and
> [`AGENT_REFACTOR.md`](AGENT_REFACTOR.md) for the stage-by-stage plan, verification evidence, and
> coverage history.

## Design Changes

The agentic package now separates provider-facing payload construction from session orchestration.
`DefaultAgentRouter` remains the public `AgentRouter` implementation and the owner of per-session
locking, tool-loop state, retry/correction policy, and persistence boundaries.

- `AgentToolDefinitionFactory` is the Factory for OpenAI-compatible function definitions. It
  converts an already-validated `AgentToolDescriptor` into provider payloads, so
  `AgentToolRegistry` now owns only registration, availability, and canonical-name checks.
- `AgentRequestAssembler` is the request-assembly strategy. It derives fresh-data requirements,
  applies mode-specific tool visibility, contextualizes prompts, and bounds history before a
  provider request starts.
- `AgentPreparedRequest` is the immutable hand-off between request assembly and the session tool
  loop. It replaces parallel local variables and prevents request construction details from
  leaking across routing stages.
- `AgentResponseSanitizer` is the response-normalization strategy. It contains legacy persona
  migration, stage-direction removal, and Saturn thin-space list formatting independently of
  provider retries and tool execution.
- `AgentResponseCorrector` owns bounded provider-response recovery. It detects stale cached
  responses, retries once with prompt caching bypassed, and corrects failure placeholders or
  narrated actions without leaking recovery prompts into the router.

## SDK Contract Boundary

`AgentToolDescriptor` remains the canonical SDK contract. `AgentToolDefinitionFactory` serializes
that contract, and `AgentToolSchemaValidator` enforces it before a tool executes. Legacy tools
using the default descriptor remain source-compatible through an explicitly open default schema;
tools publishing `additionalProperties: false` retain strict validation.

## Intentional Router Boundary

The tool loop remains in `DefaultAgentRouter` because it coordinates mutable per-invocation facts:
tool call budget, successful/failing command sets, fresh-data satisfaction, correction attempts,
and accumulated evidence. Splitting it into independently stateful handlers would obscure those
ordering guarantees. The extracted collaborators keep the loop focused on that single job.

`AgentCommandChannelPolicy` owns structured command correction, `AgentFreshDataPolicy` validates
required lookup targets and evidence, and `AgentTurnState` owns request-local facts. The ordered
`AgentTurnPolicyChain` applies the fresh-data gate, unverified-action correction, and command-channel
enforcement; it short-circuits later policies until required fresh-tool evidence exists.

## Tool Execution Pipeline

`AgentToolExecutor` is a request-scoped facade over `AgentToolCallValidator`, immutable
`ValidatedToolCall` values, `AgentToolExecutionLedger`, `AgentToolExecutionPolicy`,
`AgentToolInvoker`, and `AgentToolCallScheduler`. Validation precedes accounting; only contiguous
independent read calls fan out; observations retain provider order.

## Room And Runtime Composition

`AgentRoomMessagePipeline` is an ordered Chain of Responsibility for moderation, filtering, quiet
requests, mentions, semantic moderation, and ambient participation. Runtime construction delegates
to infrastructure, registry, router, and automation factories. `ProtectedPrincipalPolicy` is the
single source for creator, admin, host, replica, and bot exemptions.

## Compatibility

No public `AgentRouter`, `AgentTool`, `AgentToolRegistry`, or command-gateway method signature
changed. Existing callers retain the same tool payloads, response formatting, memory behavior,
and command-delivery semantics.

## Verification

Direct tests cover definition serialization, request assembly, and response normalization.
`DefaultAgentRouterTest` continues to cover the stateful orchestration behavior end to end.
