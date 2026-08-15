# Agentic Architecture Design

## Goal

Reduce router complexity without changing Saturn's public agent, tool, or command-delivery behavior.

## Architecture

`DefaultAgentRouter` remains the public session boundary and lock owner. It delegates pure or
single-purpose work to collaborators:

- `AgentRequestAssembler` builds bounded provider messages and selects mode-specific tools.
- `AgentToolDefinitionFactory` serializes a validated `AgentToolDescriptor` into the
  OpenAI-compatible function definition.
- `AgentResponseSanitizer` owns legacy-persona removal and Saturn list formatting.

The existing router tool loop remains stateful because it coordinates tool calls, corrections,
and accumulated evidence in one session. It will consume the extracted collaborators rather than
leaking serialization or output-formatting policy into orchestration code.

## Compatibility

`AgentRouter`, `AgentTool`, `AgentToolRegistry`, `SaturnCommandGateway`, and persisted memory
contracts remain source-compatible. The new classes are package-private implementation details
except for the definition factory, which is public to support SDK extensions without exposing
registry internals.

## Validation

Focused tests cover definition serialization and response normalization. Existing router tests
protect tool-loop behavior; the complete Maven suite validates integration.
