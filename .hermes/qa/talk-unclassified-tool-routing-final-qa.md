# TALK / UNCLASSIFIED / TOOL_CALL Routing — Phase 3 Final QA

Date: 2026-08-19
Repository: `/Users/ab/workspace/projects/saturn`

## Verdict

**PASS**

All requested focused and repository gates completed successfully. No task-owned defect was found, so no production or test source was changed during Phase 3.

## Verification performed

| Check | Exact command | Result |
|---|---|---|
| Focused classifier/evidence/prompt/router/finalizer/coordinator tests | `./mvnw -q -Dtest=org.saturn.app.agent.routing.AgentRequestClassifierTest,org.saturn.app.agent.turn.AgentToolEvidenceTest,org.saturn.app.agent.turn.AgentTurnStateTest,org.saturn.app.agent.routing.AgentRequestAssemblerTest,org.saturn.app.agent.routing.AgentSystemPromptTest,org.saturn.app.agent.routing.AgentResponseFinalizerTest,org.saturn.app.agent.routing.DefaultAgentRouterTest,org.saturn.app.agent.routing.AgentInvocationFactoryTest,org.saturn.app.agent.AgentInvocationTest,org.saturn.app.command.impl.user.LUserCommandImplTest test` | PASS |
| Clean compile | `./mvnw -q clean compile` | PASS |
| Full test suite | `./mvnw -q test` | PASS — 615 tests, 0 failures, 0 errors, 5 skipped, 134 report files |
| Package | `./mvnw -q package` | PASS |
| Formatting | `./mvnw -q spotless:check` | PASS |
| Whitespace | `git diff --check` | PASS |
| Added-line secret scan | scan of added tracked diff lines for API keys/secrets/passwords/tokens | PASS — 0 matches |

## Contract checks

- Tool attempts are recorded before result validation/result processing in `AgentToolResultCoordinator`; fresh-data calls record attempts before execution.
- Failed tool attempts remain evidence of `TOOL_CALL`, and quote-only correction is skipped.
- A final response with empty `toolCalls` after an earlier attempt remains `TOOL_CALL` through turn evidence and final-kind calculation.
- `TALK` and `UNCLASSIFIED` candidate metadata is rendered as trusted system-prompt metadata, with request phase and tool evidence.
- Tool-free `TALK`/`UNCLASSIFIED` responses retain exact catalog quote-only correction; command-originated and moderation invocations bypass quote-only as required.
- History, recent room context, caller/whisper/room-user context, assistant tool-call messages, and tool-result messages remain propagated on follow-up provider requests.
- Command-origin propagation is preserved through `AgentInvocationFactory` and `LUserCommandImpl`; moderation behavior remains covered by the full suite.
- No task-owned secret-like additions or unrelated task-owned artifacts were found.

## Task-owned touched paths

Production:

- `src/main/java/org/saturn/app/agent/api/AgentInvocation.java`
- `src/main/java/org/saturn/app/agent/routing/AgentInvocationFactory.java`
- `src/main/java/org/saturn/app/agent/routing/AgentPreparedRequest.java`
- `src/main/java/org/saturn/app/agent/routing/AgentRequestAssembler.java`
- `src/main/java/org/saturn/app/agent/routing/AgentRequestClassifier.java`
- `src/main/java/org/saturn/app/agent/routing/AgentRequestInput.java`
- `src/main/java/org/saturn/app/agent/routing/AgentRequestKind.java`
- `src/main/java/org/saturn/app/agent/routing/AgentResponseFinalizer.java`
- `src/main/java/org/saturn/app/agent/routing/AgentSystemPrompt.java`
- `src/main/java/org/saturn/app/agent/routing/DefaultAgentRouter.java`
- `src/main/java/org/saturn/app/agent/tool/execution/AgentToolResultCoordinator.java`
- `src/main/java/org/saturn/app/agent/turn/AgentFreshDataCoordinator.java`
- `src/main/java/org/saturn/app/agent/turn/AgentToolEvidence.java`
- `src/main/java/org/saturn/app/agent/turn/AgentTurnState.java`
- `src/main/java/org/saturn/app/command/impl/user/LUserCommandImpl.java`

Tests:

- `src/test/java/org/saturn/app/agent/AgentInvocationTest.java`
- `src/test/java/org/saturn/app/agent/routing/AgentInvocationFactoryTest.java`
- `src/test/java/org/saturn/app/agent/routing/AgentRequestAssemblerTest.java`
- `src/test/java/org/saturn/app/agent/routing/AgentRequestClassifierTest.java`
- `src/test/java/org/saturn/app/agent/routing/AgentResponseFinalizerTest.java`
- `src/test/java/org/saturn/app/agent/routing/AgentSystemPromptTest.java`
- `src/test/java/org/saturn/app/agent/routing/DefaultAgentRouterTest.java`
- `src/test/java/org/saturn/app/agent/turn/AgentToolEvidenceTest.java`
- `src/test/java/org/saturn/app/agent/turn/AgentTurnStateTest.java`
- `src/test/java/org/saturn/app/command/impl/user/LUserCommandImplTest.java`

QA artifact created:

- `.hermes/qa/talk-unclassified-tool-routing-final-qa.md`

## Working-tree note

Pre-existing unrelated untracked artifacts (including `.idea/`, `.aider.tags.cache.v4/`, local config/database/build files, and prior `.hermes/diagnostics`, `.hermes/specs`, and `.hermes/qa` documents) were preserved and not modified or claimed as task-owned.
