# Phase 2 QA — TALK / UNCLASSIFIED / TOOL_CALL routing

Date: 2026-08-19
Base: `428fa3becee1877368ee920075889d9844efcf0a`

## TDD RED

Focused command run before the new production seam:

```text
./mvnw -q -Dtest=AgentRequestClassifierTest,AgentToolEvidenceTest,AgentTurnStateTest,AgentSystemPromptTest,AgentRequestAssemblerTest test
```

Result: **RED** — Maven test compilation failed because `AgentRequestClassifier` did not exist (`cannot find symbol`). This was the expected missing-production-contract failure.

## GREEN / verification

Focused routing and evidence tests:

```text
./mvnw -q -Dtest=AgentRequestClassifierTest,AgentToolEvidenceTest,AgentTurnStateTest,AgentSystemPromptTest,AgentRequestAssemblerTest,AgentResponseFinalizerTest,AgentToolResultCoordinatorTest,DefaultAgentRouterTest test
```

Result: **GREEN** — exit code 0.

Additional gates:

- `./mvnw spotless:check`: **GREEN** after `spotless:apply` formatted six touched files.
- `./mvnw -q -DskipTests compile`: **GREEN**.
- `./mvnw -q test`: **GREEN**.
- `./mvnw -q package -DskipTests`: **GREEN**.
- `git diff --check`: **GREEN**.

## Covered behavior

- Deterministic TALK and UNCLASSIFIED candidate classification.
- Actual tool-attempt precedence over candidate/final prose, including failed attempts.
- Immutable evidence invariants and exact attempted/success/failure counts.
- Trusted request-kind, phase, evidence, room, caller, whisper, and room-user metadata propagation.
- Tool-loop evidence preservation and final TOOL_CALL classification; second provider request retains protocol messages and receives final metadata.
- Explicit finalizer kind/evidence quote gate; tool-backed and failed-tool paths skip quote-only correction.
- Tool-free TALK and UNCLASSIFIED remain quote-correction eligible under the locked policy.
- Existing command-originated behavior remains quote-correction exempt.
- Existing moderation, ambient, fresh-data, validation, catalog, and command behavior remains green.

## Touched paths

Production:

- `src/main/java/org/saturn/app/agent/routing/AgentRequestKind.java`
- `src/main/java/org/saturn/app/agent/routing/AgentRequestInput.java`
- `src/main/java/org/saturn/app/agent/routing/AgentRequestClassifier.java`
- `src/main/java/org/saturn/app/agent/turn/AgentToolEvidence.java`
- `src/main/java/org/saturn/app/agent/turn/AgentTurnState.java`
- `src/main/java/org/saturn/app/agent/turn/AgentFreshDataCoordinator.java`
- `src/main/java/org/saturn/app/agent/tool/execution/AgentToolResultCoordinator.java`
- `src/main/java/org/saturn/app/agent/routing/AgentRequestAssembler.java`
- `src/main/java/org/saturn/app/agent/routing/AgentPreparedRequest.java`
- `src/main/java/org/saturn/app/agent/routing/AgentSystemPrompt.java`
- `src/main/java/org/saturn/app/agent/routing/DefaultAgentRouter.java`
- `src/main/java/org/saturn/app/agent/routing/AgentResponseFinalizer.java`

Tests:

- `src/test/java/org/saturn/app/agent/routing/AgentRequestClassifierTest.java`
- `src/test/java/org/saturn/app/agent/routing/AgentToolEvidenceTest.java`
- `src/test/java/org/saturn/app/agent/turn/AgentTurnStateTest.java`
- `src/test/java/org/saturn/app/agent/routing/AgentRequestAssemblerTest.java`
- `src/test/java/org/saturn/app/agent/routing/AgentSystemPromptTest.java`
- `src/test/java/org/saturn/app/agent/routing/AgentResponseFinalizerTest.java`
- `src/test/java/org/saturn/app/agent/routing/DefaultAgentRouterTest.java`
- Existing command-origin contract tests under `AgentInvocationTest`, `AgentInvocationFactoryTest`, and `LUserCommandImplTest`.

No commit or push was performed. Pre-existing unrelated dirty/untracked files were preserved.
