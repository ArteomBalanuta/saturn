# Slice 1 QA — public agent API package migration

## Scope

Moved exactly these 21 declarations from `org.saturn.app.agent` to `org.saturn.app.agent.api` (no compatibility facades):

AgentCapability, AgentContext, AgentConversationContextProvider, AgentExecutionLimits, AgentInvocation, AgentInvocationMode, AgentMemoryStore, AgentParticipationConfig, AgentResult, AgentRoomAutomation, AgentRouter, AgentRoutingException, AgentTool, AgentToolDescriptor, AgentToolResult, AgentUserIdentity, ToolAccess, ToolEffect, ToolExample, ToolResponseEnvelope, ToolResultMode

Preserved record component order/names, enum constants, constructors, nested `AgentRoomAutomation.Outcome` and `ToolResponseEnvelope.Error`, and behavior. Updated production callers and tests to the new FQNs.

## Verification

- `./mvnw -q -DskipTests compile` — PASS
- `./mvnw -q -Dtest=AgentContextTest,AgentExecutionLimitsTest,AgentInvocationTest,AgentParticipationConfigTest,AgentToolDescriptorTest,AgentToolResultTest,AgentToolTest,ToolResponseEnvelopeTest test` — PASS
- `./mvnw -q spotless:apply` — PASS (formatting applied to migration edits)
- `./mvnw -q spotless:check` — PASS
- `./mvnw -q test` — PASS; 600 tests, 0 failures, 0 errors, 5 skipped
- `git diff --check` — PASS
- stale old-FQN search for all 21 names under `src/` — 0 matches

## Touched source/test paths

The following are the exact Java paths reported modified/added/deleted in the working tree for this slice (pre-existing unrelated ignored/runtime artifacts are not included):

- `src/main/java/org/saturn/app/agent/AgentCapability.java`
- `src/main/java/org/saturn/app/agent/AgentCommandChannelPolicy.java`
- `src/main/java/org/saturn/app/agent/AgentCommandIntentPolicy.java`
- `src/main/java/org/saturn/app/agent/AgentContext.java`
- `src/main/java/org/saturn/app/agent/AgentConversationContextProvider.java`
- `src/main/java/org/saturn/app/agent/AgentExecutionLimits.java`
- `src/main/java/org/saturn/app/agent/AgentExecutionState.java`
- `src/main/java/org/saturn/app/agent/AgentFreshDataCoordinator.java`
- `src/main/java/org/saturn/app/agent/AgentFreshDataFinalValidator.java`
- `src/main/java/org/saturn/app/agent/AgentFreshDataPolicy.java`
- `src/main/java/org/saturn/app/agent/AgentInvocation.java`
- `src/main/java/org/saturn/app/agent/AgentInvocationFactory.java`
- `src/main/java/org/saturn/app/agent/AgentInvocationMode.java`
- `src/main/java/org/saturn/app/agent/AgentMemoryStore.java`
- `src/main/java/org/saturn/app/agent/AgentModelVisibleToolResultRenderer.java`
- `src/main/java/org/saturn/app/agent/AgentParticipationConfig.java`
- `src/main/java/org/saturn/app/agent/AgentQuietRegistry.java`
- `src/main/java/org/saturn/app/agent/AgentRequestAssembler.java`
- `src/main/java/org/saturn/app/agent/AgentResponseCorrector.java`
- `src/main/java/org/saturn/app/agent/AgentResponseFinalizer.java`
- `src/main/java/org/saturn/app/agent/AgentResult.java`
- `src/main/java/org/saturn/app/agent/AgentRoomAutomation.java`
- `src/main/java/org/saturn/app/agent/AgentRoomAutomationFactory.java`
- `src/main/java/org/saturn/app/agent/AgentRoomMessagePipeline.java`
- `src/main/java/org/saturn/app/agent/AgentRouter.java`
- `src/main/java/org/saturn/app/agent/AgentRouterFactory.java`
- `src/main/java/org/saturn/app/agent/AgentRoutingException.java`
- `src/main/java/org/saturn/app/agent/AgentRuntimeFactory.java`
- `src/main/java/org/saturn/app/agent/AgentSessionLockManager.java`
- `src/main/java/org/saturn/app/agent/AgentSystemPrompt.java`
- `src/main/java/org/saturn/app/agent/AgentTool.java`
- `src/main/java/org/saturn/app/agent/AgentToolCallScheduler.java`
- `src/main/java/org/saturn/app/agent/AgentToolCallValidator.java`
- `src/main/java/org/saturn/app/agent/AgentToolDefinitionFactory.java`
- `src/main/java/org/saturn/app/agent/AgentToolDescriptor.java`
- `src/main/java/org/saturn/app/agent/AgentToolExecutionLedger.java`
- `src/main/java/org/saturn/app/agent/AgentToolExecutionPolicy.java`
- `src/main/java/org/saturn/app/agent/AgentToolExecutor.java`
- `src/main/java/org/saturn/app/agent/AgentToolInvoker.java`
- `src/main/java/org/saturn/app/agent/AgentToolRegistry.java`
- `src/main/java/org/saturn/app/agent/AgentToolResult.java`
- `src/main/java/org/saturn/app/agent/AgentToolResultCoordinator.java`
- `src/main/java/org/saturn/app/agent/AgentToolSchemas.java`
- `src/main/java/org/saturn/app/agent/AgentTurnMemory.java`
- `src/main/java/org/saturn/app/agent/AgentTurnPolicy.java`
- `src/main/java/org/saturn/app/agent/AgentTurnPolicyChain.java`
- `src/main/java/org/saturn/app/agent/AgentTurnState.java`
- `src/main/java/org/saturn/app/agent/AgentUnverifiedActionPolicy.java`
- `src/main/java/org/saturn/app/agent/AgentUserIdentity.java`
- `src/main/java/org/saturn/app/agent/DefaultAgentRoomAutomation.java`
- `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java`
- `src/main/java/org/saturn/app/agent/ToolAccess.java`
- `src/main/java/org/saturn/app/agent/ToolEffect.java`
- `src/main/java/org/saturn/app/agent/ToolExample.java`
- `src/main/java/org/saturn/app/agent/ToolResponseEnvelope.java`
- `src/main/java/org/saturn/app/agent/ToolResultMode.java`
- `src/main/java/org/saturn/app/agent/ValidatedToolCall.java`
- `src/main/java/org/saturn/app/agent/api/AgentCapability.java`
- `src/main/java/org/saturn/app/agent/api/AgentContext.java`
- `src/main/java/org/saturn/app/agent/api/AgentConversationContextProvider.java`
- `src/main/java/org/saturn/app/agent/api/AgentExecutionLimits.java`
- `src/main/java/org/saturn/app/agent/api/AgentInvocation.java`
- `src/main/java/org/saturn/app/agent/api/AgentInvocationMode.java`
- `src/main/java/org/saturn/app/agent/api/AgentMemoryStore.java`
- `src/main/java/org/saturn/app/agent/api/AgentParticipationConfig.java`
- `src/main/java/org/saturn/app/agent/api/AgentResult.java`
- `src/main/java/org/saturn/app/agent/api/AgentRoomAutomation.java`
- `src/main/java/org/saturn/app/agent/api/AgentRouter.java`
- `src/main/java/org/saturn/app/agent/api/AgentRoutingException.java`
- `src/main/java/org/saturn/app/agent/api/AgentTool.java`
- `src/main/java/org/saturn/app/agent/api/AgentToolDescriptor.java`
- `src/main/java/org/saturn/app/agent/api/AgentToolResult.java`
- `src/main/java/org/saturn/app/agent/api/AgentUserIdentity.java`
- `src/main/java/org/saturn/app/agent/api/ToolAccess.java`
- `src/main/java/org/saturn/app/agent/api/ToolEffect.java`
- `src/main/java/org/saturn/app/agent/api/ToolExample.java`
- `src/main/java/org/saturn/app/agent/api/ToolResponseEnvelope.java`
- `src/main/java/org/saturn/app/agent/api/ToolResultMode.java`
- `src/main/java/org/saturn/app/agent/llm/OpenAiCompatibleClient.java`
- `src/main/java/org/saturn/app/agent/llm/provider/openai/OpenAiCompatibleClient.java`
- `src/main/java/org/saturn/app/agent/moderation/EngineModerationActionExecutor.java`
- `src/main/java/org/saturn/app/agent/moderation/RoomModerationMonitor.java`
- `src/main/java/org/saturn/app/agent/package-info.java`
- `src/main/java/org/saturn/app/agent/persistence/AgentQueryRepository.java`
- `src/main/java/org/saturn/app/agent/persistence/H2AgentMemoryStore.java`
- `src/main/java/org/saturn/app/agent/persistence/H2AgentQueryRepository.java`
- `src/main/java/org/saturn/app/agent/persistence/RepositoryAgentConversationContextProvider.java`
- `src/main/java/org/saturn/app/agent/tool/DatabaseQueryTool.java`
- `src/main/java/org/saturn/app/agent/tool/DatabaseSchemaTool.java`
- `src/main/java/org/saturn/app/agent/tool/DatabaseSqlTool.java`
- `src/main/java/org/saturn/app/agent/tool/EngineSaturnCommandGateway.java`
- `src/main/java/org/saturn/app/agent/tool/RoomUsersTool.java`
- `src/main/java/org/saturn/app/agent/tool/RunCommandTool.java`
- `src/main/java/org/saturn/app/agent/tool/SaturnCommandGateway.java`
- `src/main/java/org/saturn/app/agent/tool/SaturnCommandTool.java`
- `src/main/java/org/saturn/app/agent/tool/SaturnCommandToolCatalog.java`
- `src/main/java/org/saturn/app/agent/tool/UserMessageHistoryTool.java`
- `src/main/java/org/saturn/app/command/impl/user/LUserCommandImpl.java`
- `src/main/java/org/saturn/app/facade/Base.java`
- `src/main/java/org/saturn/app/listener/message/handler/AgentParticipationHandler.java`
- `src/main/java/org/saturn/app/service/AgentService.java`
- `src/main/java/org/saturn/app/service/impl/AgentServiceImpl.java`
- `src/test/java/org/saturn/app/agent/AgentCommandChannelPolicyTest.java`
- `src/test/java/org/saturn/app/agent/AgentCommandProseGuardTest.java`
- `src/test/java/org/saturn/app/agent/AgentContextTest.java`
- `src/test/java/org/saturn/app/agent/AgentExecutionLimitsTest.java`
- `src/test/java/org/saturn/app/agent/AgentExecutionStateTest.java`
- `src/test/java/org/saturn/app/agent/AgentFreshDataCoordinatorTest.java`
- `src/test/java/org/saturn/app/agent/AgentFreshDataFinalValidatorTest.java`
- `src/test/java/org/saturn/app/agent/AgentFreshDataPolicyCorrectionTest.java`
- `src/test/java/org/saturn/app/agent/AgentFreshDataPolicyTest.java`
- `src/test/java/org/saturn/app/agent/AgentFreshDataTurnPolicyTest.java`
- `src/test/java/org/saturn/app/agent/AgentInvocationFactoryTest.java`
- `src/test/java/org/saturn/app/agent/AgentInvocationTest.java`
- `src/test/java/org/saturn/app/agent/AgentModelVisibleToolResultRendererTest.java`
- `src/test/java/org/saturn/app/agent/AgentParticipationConfigTest.java`
- `src/test/java/org/saturn/app/agent/AgentQuietRegistryTest.java`
- `src/test/java/org/saturn/app/agent/AgentRequestAssemblerTest.java`
- `src/test/java/org/saturn/app/agent/AgentResponseCorrectorTest.java`
- `src/test/java/org/saturn/app/agent/AgentResponseFinalizerTest.java`
- `src/test/java/org/saturn/app/agent/AgentRouterFactoryTest.java`
- `src/test/java/org/saturn/app/agent/AgentRuntimeFactoryTest.java`
- `src/test/java/org/saturn/app/agent/AgentSessionLockManagerTest.java`
- `src/test/java/org/saturn/app/agent/AgentSystemPromptTest.java`
- `src/test/java/org/saturn/app/agent/AgentToolBudgetPolicyTest.java`
- `src/test/java/org/saturn/app/agent/AgentToolCallSchedulerTest.java`
- `src/test/java/org/saturn/app/agent/AgentToolCallValidatorTest.java`
- `src/test/java/org/saturn/app/agent/AgentToolDefinitionFactoryTest.java`
- `src/test/java/org/saturn/app/agent/AgentToolDescriptorTest.java`
- `src/test/java/org/saturn/app/agent/AgentToolExecutionLedgerTest.java`
- `src/test/java/org/saturn/app/agent/AgentToolExecutionPolicyTest.java`
- `src/test/java/org/saturn/app/agent/AgentToolExecutorTest.java`
- `src/test/java/org/saturn/app/agent/AgentToolRegistryTest.java`
- `src/test/java/org/saturn/app/agent/AgentToolResultCoordinatorTest.java`
- `src/test/java/org/saturn/app/agent/AgentToolResultTest.java`
- `src/test/java/org/saturn/app/agent/AgentToolTest.java`
- `src/test/java/org/saturn/app/agent/AgentTurnMemoryTest.java`
- `src/test/java/org/saturn/app/agent/AgentTurnPolicyChainTest.java`
- `src/test/java/org/saturn/app/agent/AgentTurnStateTest.java`
- `src/test/java/org/saturn/app/agent/AgentUnverifiedActionPolicyTest.java`
- `src/test/java/org/saturn/app/agent/DefaultAgentRoomAutomationTest.java`
- `src/test/java/org/saturn/app/agent/DefaultAgentRouterTest.java`
- `src/test/java/org/saturn/app/agent/ToolResponseEnvelopeTest.java`
- `src/test/java/org/saturn/app/agent/llm/OpenAiCompatibleClientCompatibilityTest.java`
- `src/test/java/org/saturn/app/agent/moderation/EngineModerationActionExecutorTest.java`
- `src/test/java/org/saturn/app/agent/persistence/H2AgentMemoryStoreTest.java`
- `src/test/java/org/saturn/app/agent/persistence/H2AgentQueryRepositoryTest.java`
- `src/test/java/org/saturn/app/agent/persistence/RepositoryAgentConversationContextProviderTest.java`
- `src/test/java/org/saturn/app/agent/tool/DatabaseSchemaToolTest.java`
- `src/test/java/org/saturn/app/agent/tool/EngineSaturnCommandGatewayTest.java`
- `src/test/java/org/saturn/app/agent/tool/RunCommandToolTest.java`
- `src/test/java/org/saturn/app/agent/tool/SaturnAgentToolsTest.java`
- `src/test/java/org/saturn/app/agent/tool/SaturnCommandToolTest.java`
- `src/test/java/org/saturn/app/agent/tool/UserMessageHistoryToolTest.java`
- `src/test/java/org/saturn/app/command/impl/user/LUserCommandImplTest.java`
- `src/test/java/org/saturn/app/listener/message/handler/AgentParticipationHandlerTest.java`
- `src/test/java/org/saturn/app/service/impl/AgentServiceImplTest.java`

- `.hermes/qa/full-agent-package-refactor-slice1-api.md`

No commit or push was performed.
