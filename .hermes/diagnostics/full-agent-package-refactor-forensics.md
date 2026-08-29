# Saturn agent package refactor — Phase 0 forensic inventory

**Scope:** analysis only of direct Java sources in `src/main/java/org/saturn/app/agent`; no production or test source was edited. Repository guidance (`AGENTS.md`) and the loaded refactoring/TDD/debugging procedures were followed. Analysis ran against the dirty `develop` worktree as found; unrelated artifacts were not touched.

## Executive findings

- The stated count has a source-tree discrepancy: there are **82 direct Java files excluding `package-info.java`**, but **83 top-level types**, because `AgentTurnPolicyInput.java` declares both `AgentTurnPolicyInput` and `AgentTurnPolicyResult`. The requested “82 types” is therefore not reproducible from the current tree; this report maps all 83 actual top-level declarations so none is silently omitted.
- Existing subpackages are `llm`, `llm/provider/openai`, `persistence`, `sql`, `tool`, and `moderation`; 46 existing subpackage declarations were inventoried (including the two `OpenAiCompatibleClient` declarations in the dirty worktree). The direct package is a mixed composition root, public API, routing pipeline, turn policies, tool engine, room automation, and configuration layer.
- The safest target is a layered split: stable contracts/models first, then configuration and leaf policies, then tool contracts/execution, then routing/room orchestration, while retaining `llm`, `persistence`, `sql`, `tool`, and `moderation` as integration-facing namespaces until callers/tests migrate.
- Two current declaration-level cycles are detected: `AgentConfig ↔ AgentConfigLoader` and `AgentResponseCorrector ↔ VerifiedQuoteCatalog`. The large router/tool graph is highly connected but is not a cycle under simple declaration-reference analysis; it is the principal migration risk.

## Inventory and existing subpackages

- Direct files examined: **82** (excluding `package-info.java`). Direct top-level types: **83**. `package-info.java` exists and is excluded.
- Existing recursive subpackage inventory:
  - `org.saturn.app.agent.llm`: LlmClient, LlmException, LlmMessage, LlmRequest, LlmResponse, LlmToolCall, OpenAiCompatibleClient, UnsupportedResponseFormatException
  - `org.saturn.app.agent.llm/provider/openai`: OpenAiCompatibleClient
  - `org.saturn.app.agent.persistence`: AgentDatabaseSchema, AgentPersistenceException, AgentQueryRepository, AgentSchemaRepository, AgentSqlRepository, AgentSqlResult, H2AgentMemoryStore, H2AgentQueryRepository, H2AgentSchemaRepository, H2AgentSqlRepository, H2ReadOnlyConnectionFactory, H2TransactionExecutor, RepositoryAgentConversationContextProvider
  - `org.saturn.app.agent.sql`: AgentSqlErrorCode, AgentSqlPolicy, AgentSqlPolicyException, JSqlParserAgentSqlPolicy, ValidatedAgentSql
  - `org.saturn.app.agent.tool`: AgentRoomDirectory, AgentToolArgumentReader, DatabaseQueryTool, DatabaseSchemaTool, DatabaseSqlTool, EngineAgentRoomDirectory, EngineSaturnCommandGateway, RoomUsersTool, RunCommandTool, SaturnCommandGateway, SaturnCommandTool, SaturnCommandToolCatalog, UserMessageHistoryTool
  - `org.saturn.app.agent.moderation`: AgentModerationConfig, EngineModerationActionExecutor, ModerationAction, ModerationActionExecutor, ModerationDecision, RoomModerationMonitor

### Direct declaration inventory

| Current FQN | Source file | Kind/visibility | Nested declarations |
|---|---|---|---|
| `org.saturn.app.agent.AgentCapability` | `src/main/java/org/saturn/app/agent/AgentCapability.java` | enum, public | — |
| `org.saturn.app.agent.AgentCommandChannelPolicy` | `src/main/java/org/saturn/app/agent/AgentCommandChannelPolicy.java` | class, package-private | `Result` |
| `org.saturn.app.agent.AgentCommandIntentPolicy` | `src/main/java/org/saturn/app/agent/AgentCommandIntentPolicy.java` | class, package-private | — |
| `org.saturn.app.agent.AgentCommandProseGuard` | `src/main/java/org/saturn/app/agent/AgentCommandProseGuard.java` | class, package-private | — |
| `org.saturn.app.agent.AgentConfig` | `src/main/java/org/saturn/app/agent/AgentConfig.java` | record, public | — |
| `org.saturn.app.agent.AgentConfigLoader` | `src/main/java/org/saturn/app/agent/AgentConfigLoader.java` | class, package-private | — |
| `org.saturn.app.agent.AgentConfigValueReader` | `src/main/java/org/saturn/app/agent/AgentConfigValueReader.java` | class, package-private | — |
| `org.saturn.app.agent.AgentContext` | `src/main/java/org/saturn/app/agent/AgentContext.java` | record, public | — |
| `org.saturn.app.agent.AgentConversationContextProvider` | `src/main/java/org/saturn/app/agent/AgentConversationContextProvider.java` | interface, public | — |
| `org.saturn.app.agent.AgentExecutionLimits` | `src/main/java/org/saturn/app/agent/AgentExecutionLimits.java` | record, public | — |
| `org.saturn.app.agent.AgentExecutionState` | `src/main/java/org/saturn/app/agent/AgentExecutionState.java` | class, package-private | — |
| `org.saturn.app.agent.AgentFreshDataCoordinator` | `src/main/java/org/saturn/app/agent/AgentFreshDataCoordinator.java` | class, package-private | `Result`, `ToolResultRenderer`, `DefinitionProvider` |
| `org.saturn.app.agent.AgentFreshDataFinalValidator` | `src/main/java/org/saturn/app/agent/AgentFreshDataFinalValidator.java` | class, package-private | — |
| `org.saturn.app.agent.AgentFreshDataPolicy` | `src/main/java/org/saturn/app/agent/AgentFreshDataPolicy.java` | class, package-private | — |
| `org.saturn.app.agent.AgentFreshDataTurnPolicy` | `src/main/java/org/saturn/app/agent/AgentFreshDataTurnPolicy.java` | class, package-private | — |
| `org.saturn.app.agent.AgentFreshnessPolicy` | `src/main/java/org/saturn/app/agent/AgentFreshnessPolicy.java` | class, package-private | — |
| `org.saturn.app.agent.AgentInfrastructure` | `src/main/java/org/saturn/app/agent/AgentInfrastructure.java` | record, package-private | — |
| `org.saturn.app.agent.AgentInfrastructureFactory` | `src/main/java/org/saturn/app/agent/AgentInfrastructureFactory.java` | class, package-private | — |
| `org.saturn.app.agent.AgentInvocation` | `src/main/java/org/saturn/app/agent/AgentInvocation.java` | record, public | — |
| `org.saturn.app.agent.AgentInvocationFactory` | `src/main/java/org/saturn/app/agent/AgentInvocationFactory.java` | class, package-private | — |
| `org.saturn.app.agent.AgentInvocationMode` | `src/main/java/org/saturn/app/agent/AgentInvocationMode.java` | enum, public | — |
| `org.saturn.app.agent.AgentMemoryStore` | `src/main/java/org/saturn/app/agent/AgentMemoryStore.java` | interface, public | — |
| `org.saturn.app.agent.AgentMentionParser` | `src/main/java/org/saturn/app/agent/AgentMentionParser.java` | class, package-private | — |
| `org.saturn.app.agent.AgentMessageHistory` | `src/main/java/org/saturn/app/agent/AgentMessageHistory.java` | class, package-private | — |
| `org.saturn.app.agent.AgentModelVisibleToolResultRenderer` | `src/main/java/org/saturn/app/agent/AgentModelVisibleToolResultRenderer.java` | class, package-private | — |
| `org.saturn.app.agent.AgentNickNormalizer` | `src/main/java/org/saturn/app/agent/AgentNickNormalizer.java` | class, package-private | — |
| `org.saturn.app.agent.AgentParticipationConfig` | `src/main/java/org/saturn/app/agent/AgentParticipationConfig.java` | record, public | — |
| `org.saturn.app.agent.AgentPreparedRequest` | `src/main/java/org/saturn/app/agent/AgentPreparedRequest.java` | record, package-private | — |
| `org.saturn.app.agent.AgentPromptCatalog` | `src/main/java/org/saturn/app/agent/AgentPromptCatalog.java` | class, package-private | `ResourceSource` |
| `org.saturn.app.agent.AgentQuietRegistry` | `src/main/java/org/saturn/app/agent/AgentQuietRegistry.java` | class, package-private | `QuietKey` |
| `org.saturn.app.agent.AgentRequestAssembler` | `src/main/java/org/saturn/app/agent/AgentRequestAssembler.java` | class, package-private | — |
| `org.saturn.app.agent.AgentResponseCorrector` | `src/main/java/org/saturn/app/agent/AgentResponseCorrector.java` | class, package-private | — |
| `org.saturn.app.agent.AgentResponseFinalizer` | `src/main/java/org/saturn/app/agent/AgentResponseFinalizer.java` | class, package-private | `Result` |
| `org.saturn.app.agent.AgentResponseSanitizer` | `src/main/java/org/saturn/app/agent/AgentResponseSanitizer.java` | class, package-private | — |
| `org.saturn.app.agent.AgentResult` | `src/main/java/org/saturn/app/agent/AgentResult.java` | record, public | — |
| `org.saturn.app.agent.AgentRoomAutomation` | `src/main/java/org/saturn/app/agent/AgentRoomAutomation.java` | interface, public | `Outcome` |
| `org.saturn.app.agent.AgentRoomAutomationFactory` | `src/main/java/org/saturn/app/agent/AgentRoomAutomationFactory.java` | class, package-private | — |
| `org.saturn.app.agent.AgentRoomMessagePipeline` | `src/main/java/org/saturn/app/agent/AgentRoomMessagePipeline.java` | class, package-private | `Handler`, `Decision`, `Turn` |
| `org.saturn.app.agent.AgentRouter` | `src/main/java/org/saturn/app/agent/AgentRouter.java` | interface, public | — |
| `org.saturn.app.agent.AgentRouterFactory` | `src/main/java/org/saturn/app/agent/AgentRouterFactory.java` | class, package-private | — |
| `org.saturn.app.agent.AgentRoutingException` | `src/main/java/org/saturn/app/agent/AgentRoutingException.java` | class, public | — |
| `org.saturn.app.agent.AgentRuntimeFactory` | `src/main/java/org/saturn/app/agent/AgentRuntimeFactory.java` | class, package-private | — |
| `org.saturn.app.agent.AgentScheduledToolCall` | `src/main/java/org/saturn/app/agent/AgentScheduledToolCall.java` | record, package-private | — |
| `org.saturn.app.agent.AgentSessionLockManager` | `src/main/java/org/saturn/app/agent/AgentSessionLockManager.java` | class, package-private | `LockedOperation` |
| `org.saturn.app.agent.AgentSqlConfig` | `src/main/java/org/saturn/app/agent/AgentSqlConfig.java` | record, public | — |
| `org.saturn.app.agent.AgentSystemPrompt` | `src/main/java/org/saturn/app/agent/AgentSystemPrompt.java` | class, package-private | — |
| `org.saturn.app.agent.AgentTextBounds` | `src/main/java/org/saturn/app/agent/AgentTextBounds.java` | class, package-private | — |
| `org.saturn.app.agent.AgentTool` | `src/main/java/org/saturn/app/agent/AgentTool.java` | interface, public | — |
| `org.saturn.app.agent.AgentToolBudgetPolicy` | `src/main/java/org/saturn/app/agent/AgentToolBudgetPolicy.java` | class, package-private | `Result` |
| `org.saturn.app.agent.AgentToolCallScheduler` | `src/main/java/org/saturn/app/agent/AgentToolCallScheduler.java` | class, package-private | `ToolCallExecution` |
| `org.saturn.app.agent.AgentToolCallValidator` | `src/main/java/org/saturn/app/agent/AgentToolCallValidator.java` | class, package-private | `Result` |
| `org.saturn.app.agent.AgentToolDefinitionFactory` | `src/main/java/org/saturn/app/agent/AgentToolDefinitionFactory.java` | class, package-private | — |
| `org.saturn.app.agent.AgentToolDefinitionJson` | `src/main/java/org/saturn/app/agent/AgentToolDefinitionJson.java` | class, package-private | — |
| `org.saturn.app.agent.AgentToolDescriptor` | `src/main/java/org/saturn/app/agent/AgentToolDescriptor.java` | record, public | — |
| `org.saturn.app.agent.AgentToolExecutionLedger` | `src/main/java/org/saturn/app/agent/AgentToolExecutionLedger.java` | class, package-private | `Reservation` |
| `org.saturn.app.agent.AgentToolExecutionMode` | `src/main/java/org/saturn/app/agent/AgentToolExecutionMode.java` | enum, package-private | — |
| `org.saturn.app.agent.AgentToolExecutionPolicy` | `src/main/java/org/saturn/app/agent/AgentToolExecutionPolicy.java` | class, package-private | — |
| `org.saturn.app.agent.AgentToolExecutor` | `src/main/java/org/saturn/app/agent/AgentToolExecutor.java` | class, package-private | `Classification` |
| `org.saturn.app.agent.AgentToolInvoker` | `src/main/java/org/saturn/app/agent/AgentToolInvoker.java` | class, package-private | — |
| `org.saturn.app.agent.AgentToolRegistry` | `src/main/java/org/saturn/app/agent/AgentToolRegistry.java` | class, package-private | — |
| `org.saturn.app.agent.AgentToolRegistryFactory` | `src/main/java/org/saturn/app/agent/AgentToolRegistryFactory.java` | class, package-private | — |
| `org.saturn.app.agent.AgentToolResult` | `src/main/java/org/saturn/app/agent/AgentToolResult.java` | record, public | — |
| `org.saturn.app.agent.AgentToolResultCoordinator` | `src/main/java/org/saturn/app/agent/AgentToolResultCoordinator.java` | class, package-private | `ToolResultRenderer` |
| `org.saturn.app.agent.AgentToolSchemaValidator` | `src/main/java/org/saturn/app/agent/AgentToolSchemaValidator.java` | class, package-private | — |
| `org.saturn.app.agent.AgentToolSchemas` | `src/main/java/org/saturn/app/agent/AgentToolSchemas.java` | class, package-private | — |
| `org.saturn.app.agent.AgentTurnMemory` | `src/main/java/org/saturn/app/agent/AgentTurnMemory.java` | class, package-private | — |
| `org.saturn.app.agent.AgentTurnPolicy` | `src/main/java/org/saturn/app/agent/AgentTurnPolicy.java` | interface, package-private | — |
| `org.saturn.app.agent.AgentTurnPolicyChain` | `src/main/java/org/saturn/app/agent/AgentTurnPolicyChain.java` | class, package-private | — |
| `org.saturn.app.agent.AgentTurnPolicyInput` | `src/main/java/org/saturn/app/agent/AgentTurnPolicyInput.java` | record, package-private | — |
| `org.saturn.app.agent.AgentTurnPolicyResult` | `src/main/java/org/saturn/app/agent/AgentTurnPolicyInput.java` | record, package-private | — |
| `org.saturn.app.agent.AgentTurnState` | `src/main/java/org/saturn/app/agent/AgentTurnState.java` | class, package-private | — |
| `org.saturn.app.agent.AgentUnverifiedActionPolicy` | `src/main/java/org/saturn/app/agent/AgentUnverifiedActionPolicy.java` | class, package-private | — |
| `org.saturn.app.agent.AgentUserIdentity` | `src/main/java/org/saturn/app/agent/AgentUserIdentity.java` | record, public | — |
| `org.saturn.app.agent.DefaultAgentRoomAutomation` | `src/main/java/org/saturn/app/agent/DefaultAgentRoomAutomation.java` | class, package-private | — |
| `org.saturn.app.agent.DefaultAgentRouter` | `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java` | class, package-private | — |
| `org.saturn.app.agent.ProtectedPrincipalPolicy` | `src/main/java/org/saturn/app/agent/ProtectedPrincipalPolicy.java` | class, package-private | — |
| `org.saturn.app.agent.ToolAccess` | `src/main/java/org/saturn/app/agent/ToolAccess.java` | enum, public | — |
| `org.saturn.app.agent.ToolEffect` | `src/main/java/org/saturn/app/agent/ToolEffect.java` | enum, public | — |
| `org.saturn.app.agent.ToolExample` | `src/main/java/org/saturn/app/agent/ToolExample.java` | record, public | — |
| `org.saturn.app.agent.ToolResponseEnvelope` | `src/main/java/org/saturn/app/agent/ToolResponseEnvelope.java` | record, public | `Error` |
| `org.saturn.app.agent.ToolResultMode` | `src/main/java/org/saturn/app/agent/ToolResultMode.java` | enum, public | — |
| `org.saturn.app.agent.ValidatedToolCall` | `src/main/java/org/saturn/app/agent/ValidatedToolCall.java` | record, package-private | — |
| `org.saturn.app.agent.VerifiedQuoteCatalog` | `src/main/java/org/saturn/app/agent/VerifiedQuoteCatalog.java` | class, package-private | `Entry` |

## Proposed target directory tree

```text
org/saturn/app/agent/
├── api/
├── config/
├── routing/
├── turn/
├── room/
├── tool/contract/
├── tool/execution/
├── llm/
├── llm/provider/openai/
├── persistence/
├── sql/
├── moderation/
├── tool/
```

The proposed package names below are intentionally conservative. `api` contains externally consumed contracts/value objects; `routing`, `turn`, `room`, and `tool/*` contain implementation seams. Existing persistence/SQL/LLM/moderation/tool packages are retained initially to avoid a simultaneous namespace and behavior change. `OpenAiCompatibleClient` appears in both current `agent.llm` and an untracked `agent.llm.provider.openai` source path in this dirty worktree; the mapping table below refers to the direct file only and recommends the provider-qualified destination.

## Exhaustive current-FQN → proposed-FQN mapping

| Current FQN | Proposed FQN | Responsibility / reason |
|---|---|---|
| `org.saturn.app.agent.AgentCapability` | `org.saturn.app.agent.api.AgentCapability` | public contracts/value objects; preserve as compatibility boundary |
| `org.saturn.app.agent.AgentContext` | `org.saturn.app.agent.api.AgentContext` | public contracts/value objects; preserve as compatibility boundary |
| `org.saturn.app.agent.AgentConversationContextProvider` | `org.saturn.app.agent.api.AgentConversationContextProvider` | public contracts/value objects; preserve as compatibility boundary |
| `org.saturn.app.agent.AgentExecutionLimits` | `org.saturn.app.agent.api.AgentExecutionLimits` | public contracts/value objects; preserve as compatibility boundary |
| `org.saturn.app.agent.AgentInvocation` | `org.saturn.app.agent.api.AgentInvocation` | public contracts/value objects; preserve as compatibility boundary |
| `org.saturn.app.agent.AgentInvocationMode` | `org.saturn.app.agent.api.AgentInvocationMode` | public contracts/value objects; preserve as compatibility boundary |
| `org.saturn.app.agent.AgentMemoryStore` | `org.saturn.app.agent.api.AgentMemoryStore` | public contracts/value objects; preserve as compatibility boundary |
| `org.saturn.app.agent.AgentParticipationConfig` | `org.saturn.app.agent.api.AgentParticipationConfig` | public contracts/value objects; preserve as compatibility boundary |
| `org.saturn.app.agent.AgentResult` | `org.saturn.app.agent.api.AgentResult` | public contracts/value objects; preserve as compatibility boundary |
| `org.saturn.app.agent.AgentRoomAutomation` | `org.saturn.app.agent.api.AgentRoomAutomation` | public contracts/value objects; preserve as compatibility boundary |
| `org.saturn.app.agent.AgentRouter` | `org.saturn.app.agent.api.AgentRouter` | public contracts/value objects; preserve as compatibility boundary |
| `org.saturn.app.agent.AgentRoutingException` | `org.saturn.app.agent.api.AgentRoutingException` | public contracts/value objects; preserve as compatibility boundary |
| `org.saturn.app.agent.AgentTool` | `org.saturn.app.agent.api.AgentTool` | public contracts/value objects; preserve as compatibility boundary |
| `org.saturn.app.agent.AgentToolDescriptor` | `org.saturn.app.agent.api.AgentToolDescriptor` | public contracts/value objects; preserve as compatibility boundary |
| `org.saturn.app.agent.AgentToolResult` | `org.saturn.app.agent.api.AgentToolResult` | public contracts/value objects; preserve as compatibility boundary |
| `org.saturn.app.agent.AgentUserIdentity` | `org.saturn.app.agent.api.AgentUserIdentity` | public contracts/value objects; preserve as compatibility boundary |
| `org.saturn.app.agent.ToolAccess` | `org.saturn.app.agent.api.ToolAccess` | public contracts/value objects; preserve as compatibility boundary |
| `org.saturn.app.agent.ToolEffect` | `org.saturn.app.agent.api.ToolEffect` | public contracts/value objects; preserve as compatibility boundary |
| `org.saturn.app.agent.ToolExample` | `org.saturn.app.agent.api.ToolExample` | public contracts/value objects; preserve as compatibility boundary |
| `org.saturn.app.agent.ToolResponseEnvelope` | `org.saturn.app.agent.api.ToolResponseEnvelope` | public contracts/value objects; preserve as compatibility boundary |
| `org.saturn.app.agent.ToolResultMode` | `org.saturn.app.agent.api.ToolResultMode` | public contracts/value objects; preserve as compatibility boundary |
| `org.saturn.app.agent.AgentConfig` | `org.saturn.app.agent.config.AgentConfig` | configuration loading and immutable configuration model |
| `org.saturn.app.agent.AgentConfigLoader` | `org.saturn.app.agent.config.AgentConfigLoader` | configuration loading and immutable configuration model |
| `org.saturn.app.agent.AgentConfigValueReader` | `org.saturn.app.agent.config.AgentConfigValueReader` | configuration loading and immutable configuration model |
| `org.saturn.app.agent.AgentSqlConfig` | `org.saturn.app.agent.config.AgentSqlConfig` | configuration loading and immutable configuration model |
| `org.saturn.app.agent.AgentRouterFactory` | `org.saturn.app.agent.routing.AgentRouterFactory` | request/response routing and orchestration |
| `org.saturn.app.agent.DefaultAgentRouter` | `org.saturn.app.agent.routing.DefaultAgentRouter` | request/response routing and orchestration |
| `org.saturn.app.agent.AgentRuntimeFactory` | `org.saturn.app.agent.routing.AgentRuntimeFactory` | request/response routing and orchestration |
| `org.saturn.app.agent.AgentInfrastructure` | `org.saturn.app.agent.routing.AgentInfrastructure` | request/response routing and orchestration |
| `org.saturn.app.agent.AgentInfrastructureFactory` | `org.saturn.app.agent.routing.AgentInfrastructureFactory` | request/response routing and orchestration |
| `org.saturn.app.agent.AgentInvocationFactory` | `org.saturn.app.agent.routing.AgentInvocationFactory` | request/response routing and orchestration |
| `org.saturn.app.agent.AgentRequestAssembler` | `org.saturn.app.agent.routing.AgentRequestAssembler` | request/response routing and orchestration |
| `org.saturn.app.agent.AgentPreparedRequest` | `org.saturn.app.agent.routing.AgentPreparedRequest` | request/response routing and orchestration |
| `org.saturn.app.agent.AgentResponseCorrector` | `org.saturn.app.agent.routing.AgentResponseCorrector` | request/response routing and orchestration |
| `org.saturn.app.agent.AgentResponseFinalizer` | `org.saturn.app.agent.routing.AgentResponseFinalizer` | request/response routing and orchestration |
| `org.saturn.app.agent.AgentResponseSanitizer` | `org.saturn.app.agent.routing.AgentResponseSanitizer` | request/response routing and orchestration |
| `org.saturn.app.agent.AgentPromptCatalog` | `org.saturn.app.agent.routing.AgentPromptCatalog` | request/response routing and orchestration |
| `org.saturn.app.agent.AgentSystemPrompt` | `org.saturn.app.agent.routing.AgentSystemPrompt` | request/response routing and orchestration |
| `org.saturn.app.agent.AgentTextBounds` | `org.saturn.app.agent.routing.AgentTextBounds` | request/response routing and orchestration |
| `org.saturn.app.agent.VerifiedQuoteCatalog` | `org.saturn.app.agent.routing.VerifiedQuoteCatalog` | request/response routing and orchestration |
| `org.saturn.app.agent.AgentCommandIntentPolicy` | `org.saturn.app.agent.routing.AgentCommandIntentPolicy` | request/response routing and orchestration |
| `org.saturn.app.agent.AgentCommandProseGuard` | `org.saturn.app.agent.routing.AgentCommandProseGuard` | request/response routing and orchestration |
| `org.saturn.app.agent.AgentCommandChannelPolicy` | `org.saturn.app.agent.routing.AgentCommandChannelPolicy` | request/response routing and orchestration |
| `org.saturn.app.agent.AgentExecutionState` | `org.saturn.app.agent.turn.AgentExecutionState` | turn state, policy chain, and freshness decisions |
| `org.saturn.app.agent.AgentTurnState` | `org.saturn.app.agent.turn.AgentTurnState` | turn state, policy chain, and freshness decisions |
| `org.saturn.app.agent.AgentTurnMemory` | `org.saturn.app.agent.turn.AgentTurnMemory` | turn state, policy chain, and freshness decisions |
| `org.saturn.app.agent.AgentTurnPolicy` | `org.saturn.app.agent.turn.AgentTurnPolicy` | turn state, policy chain, and freshness decisions |
| `org.saturn.app.agent.AgentTurnPolicyChain` | `org.saturn.app.agent.turn.AgentTurnPolicyChain` | turn state, policy chain, and freshness decisions |
| `org.saturn.app.agent.AgentTurnPolicyInput` | `org.saturn.app.agent.turn.AgentTurnPolicyInput` | turn state, policy chain, and freshness decisions |
| `org.saturn.app.agent.AgentTurnPolicyResult` | `org.saturn.app.agent.turn.AgentTurnPolicyResult` | turn state, policy chain, and freshness decisions |
| `org.saturn.app.agent.AgentFreshDataCoordinator` | `org.saturn.app.agent.turn.AgentFreshDataCoordinator` | turn state, policy chain, and freshness decisions |
| `org.saturn.app.agent.AgentFreshDataFinalValidator` | `org.saturn.app.agent.turn.AgentFreshDataFinalValidator` | turn state, policy chain, and freshness decisions |
| `org.saturn.app.agent.AgentFreshDataPolicy` | `org.saturn.app.agent.turn.AgentFreshDataPolicy` | turn state, policy chain, and freshness decisions |
| `org.saturn.app.agent.AgentFreshDataTurnPolicy` | `org.saturn.app.agent.turn.AgentFreshDataTurnPolicy` | turn state, policy chain, and freshness decisions |
| `org.saturn.app.agent.AgentFreshnessPolicy` | `org.saturn.app.agent.turn.AgentFreshnessPolicy` | turn state, policy chain, and freshness decisions |
| `org.saturn.app.agent.AgentUnverifiedActionPolicy` | `org.saturn.app.agent.turn.AgentUnverifiedActionPolicy` | turn state, policy chain, and freshness decisions |
| `org.saturn.app.agent.AgentMessageHistory` | `org.saturn.app.agent.turn.AgentMessageHistory` | turn state, policy chain, and freshness decisions |
| `org.saturn.app.agent.AgentNickNormalizer` | `org.saturn.app.agent.turn.AgentNickNormalizer` | turn state, policy chain, and freshness decisions |
| `org.saturn.app.agent.AgentMentionParser` | `org.saturn.app.agent.room.AgentMentionParser` | room admission, mention/quiet/session orchestration |
| `org.saturn.app.agent.AgentQuietRegistry` | `org.saturn.app.agent.room.AgentQuietRegistry` | room admission, mention/quiet/session orchestration |
| `org.saturn.app.agent.AgentRoomMessagePipeline` | `org.saturn.app.agent.room.AgentRoomMessagePipeline` | room admission, mention/quiet/session orchestration |
| `org.saturn.app.agent.AgentRoomAutomationFactory` | `org.saturn.app.agent.room.AgentRoomAutomationFactory` | room admission, mention/quiet/session orchestration |
| `org.saturn.app.agent.DefaultAgentRoomAutomation` | `org.saturn.app.agent.room.DefaultAgentRoomAutomation` | room admission, mention/quiet/session orchestration |
| `org.saturn.app.agent.AgentSessionLockManager` | `org.saturn.app.agent.room.AgentSessionLockManager` | room admission, mention/quiet/session orchestration |
| `org.saturn.app.agent.ProtectedPrincipalPolicy` | `org.saturn.app.agent.room.ProtectedPrincipalPolicy` | room admission, mention/quiet/session orchestration |
| `org.saturn.app.agent.AgentToolDefinitionJson` | `org.saturn.app.agent.tool.contract.AgentToolDefinitionJson` | tool schema/definition contracts |
| `org.saturn.app.agent.AgentToolSchemas` | `org.saturn.app.agent.tool.contract.AgentToolSchemas` | tool schema/definition contracts |
| `org.saturn.app.agent.AgentToolSchemaValidator` | `org.saturn.app.agent.tool.contract.AgentToolSchemaValidator` | tool schema/definition contracts |
| `org.saturn.app.agent.AgentToolDefinitionFactory` | `org.saturn.app.agent.tool.contract.AgentToolDefinitionFactory` | tool schema/definition contracts |
| `org.saturn.app.agent.AgentScheduledToolCall` | `org.saturn.app.agent.tool.execution.AgentScheduledToolCall` | tool registry, validation, scheduling, execution, and result coordination |
| `org.saturn.app.agent.AgentToolBudgetPolicy` | `org.saturn.app.agent.tool.execution.AgentToolBudgetPolicy` | tool registry, validation, scheduling, execution, and result coordination |
| `org.saturn.app.agent.AgentToolCallScheduler` | `org.saturn.app.agent.tool.execution.AgentToolCallScheduler` | tool registry, validation, scheduling, execution, and result coordination |
| `org.saturn.app.agent.AgentToolCallValidator` | `org.saturn.app.agent.tool.execution.AgentToolCallValidator` | tool registry, validation, scheduling, execution, and result coordination |
| `org.saturn.app.agent.AgentToolExecutionLedger` | `org.saturn.app.agent.tool.execution.AgentToolExecutionLedger` | tool registry, validation, scheduling, execution, and result coordination |
| `org.saturn.app.agent.AgentToolExecutionMode` | `org.saturn.app.agent.tool.execution.AgentToolExecutionMode` | tool registry, validation, scheduling, execution, and result coordination |
| `org.saturn.app.agent.AgentToolExecutionPolicy` | `org.saturn.app.agent.tool.execution.AgentToolExecutionPolicy` | tool registry, validation, scheduling, execution, and result coordination |
| `org.saturn.app.agent.AgentToolExecutor` | `org.saturn.app.agent.tool.execution.AgentToolExecutor` | tool registry, validation, scheduling, execution, and result coordination |
| `org.saturn.app.agent.AgentToolInvoker` | `org.saturn.app.agent.tool.execution.AgentToolInvoker` | tool registry, validation, scheduling, execution, and result coordination |
| `org.saturn.app.agent.AgentToolRegistry` | `org.saturn.app.agent.tool.execution.AgentToolRegistry` | tool registry, validation, scheduling, execution, and result coordination |
| `org.saturn.app.agent.AgentToolRegistryFactory` | `org.saturn.app.agent.tool.execution.AgentToolRegistryFactory` | tool registry, validation, scheduling, execution, and result coordination |
| `org.saturn.app.agent.AgentToolResultCoordinator` | `org.saturn.app.agent.tool.execution.AgentToolResultCoordinator` | tool registry, validation, scheduling, execution, and result coordination |
| `org.saturn.app.agent.AgentModelVisibleToolResultRenderer` | `org.saturn.app.agent.tool.execution.AgentModelVisibleToolResultRenderer` | tool registry, validation, scheduling, execution, and result coordination |
| `org.saturn.app.agent.ValidatedToolCall` | `org.saturn.app.agent.tool.execution.ValidatedToolCall` | tool registry, validation, scheduling, execution, and result coordination |

### Existing subpackage types: recommended unchanged in Stage 0

- `agent.llm` types: `LlmClient` → `org.saturn.app.agent.llm.LlmClient`, `LlmException` → `org.saturn.app.agent.llm.LlmException`, `LlmMessage` → `org.saturn.app.agent.llm.LlmMessage`, `LlmRequest` → `org.saturn.app.agent.llm.LlmRequest`, `LlmResponse` → `org.saturn.app.agent.llm.LlmResponse`, `LlmToolCall` → `org.saturn.app.agent.llm.LlmToolCall`, `location` → `org.saturn.app.agent.llm.location`, `UnsupportedResponseFormatException` → `org.saturn.app.agent.llm.UnsupportedResponseFormatException`, `OpenAiCompatibleClient` → `org.saturn.app.agent.llm.OpenAiCompatibleClient`
- `agent.llm/provider/openai` types: `OpenAiCompatibleClient` → `org.saturn.app.agent.llm/provider/openai.OpenAiCompatibleClient`
- `agent.persistence` types: `AgentDatabaseSchema` → `org.saturn.app.agent.persistence.AgentDatabaseSchema`, `AgentPersistenceException` → `org.saturn.app.agent.persistence.AgentPersistenceException`, `AgentQueryRepository` → `org.saturn.app.agent.persistence.AgentQueryRepository`, `AgentSchemaRepository` → `org.saturn.app.agent.persistence.AgentSchemaRepository`, `AgentSqlRepository` → `org.saturn.app.agent.persistence.AgentSqlRepository`, `AgentSqlResult` → `org.saturn.app.agent.persistence.AgentSqlResult`, `H2AgentMemoryStore` → `org.saturn.app.agent.persistence.H2AgentMemoryStore`, `H2AgentQueryRepository` → `org.saturn.app.agent.persistence.H2AgentQueryRepository`, `H2AgentSchemaRepository` → `org.saturn.app.agent.persistence.H2AgentSchemaRepository`, `H2AgentSqlRepository` → `org.saturn.app.agent.persistence.H2AgentSqlRepository`, `H2ReadOnlyConnectionFactory` → `org.saturn.app.agent.persistence.H2ReadOnlyConnectionFactory`, `H2TransactionExecutor` → `org.saturn.app.agent.persistence.H2TransactionExecutor`, `RepositoryAgentConversationContextProvider` → `org.saturn.app.agent.persistence.RepositoryAgentConversationContextProvider`
- `agent.sql` types: `AgentSqlErrorCode` → `org.saturn.app.agent.sql.AgentSqlErrorCode`, `AgentSqlPolicy` → `org.saturn.app.agent.sql.AgentSqlPolicy`, `AgentSqlPolicyException` → `org.saturn.app.agent.sql.AgentSqlPolicyException`, `JSqlParserAgentSqlPolicy` → `org.saturn.app.agent.sql.JSqlParserAgentSqlPolicy`, `ValidatedAgentSql` → `org.saturn.app.agent.sql.ValidatedAgentSql`
- `agent.tool` types: `AgentRoomDirectory` → `org.saturn.app.agent.tool.AgentRoomDirectory`, `AgentToolArgumentReader` → `org.saturn.app.agent.tool.AgentToolArgumentReader`, `DatabaseQueryTool` → `org.saturn.app.agent.tool.DatabaseQueryTool`, `DatabaseSchemaTool` → `org.saturn.app.agent.tool.DatabaseSchemaTool`, `DatabaseSqlTool` → `org.saturn.app.agent.tool.DatabaseSqlTool`, `EngineAgentRoomDirectory` → `org.saturn.app.agent.tool.EngineAgentRoomDirectory`, `EngineSaturnCommandGateway` → `org.saturn.app.agent.tool.EngineSaturnCommandGateway`, `RoomUsersTool` → `org.saturn.app.agent.tool.RoomUsersTool`, `RunCommandTool` → `org.saturn.app.agent.tool.RunCommandTool`, `SaturnCommandGateway` → `org.saturn.app.agent.tool.SaturnCommandGateway`, `SaturnCommandTool` → `org.saturn.app.agent.tool.SaturnCommandTool`, `SaturnCommandToolCatalog` → `org.saturn.app.agent.tool.SaturnCommandToolCatalog`, `UserMessageHistoryTool` → `org.saturn.app.agent.tool.UserMessageHistoryTool`
- `agent.moderation` types: `AgentModerationConfig` → `org.saturn.app.agent.moderation.AgentModerationConfig`, `EngineModerationActionExecutor` → `org.saturn.app.agent.moderation.EngineModerationActionExecutor`, `ModerationAction` → `org.saturn.app.agent.moderation.ModerationAction`, `ModerationActionExecutor` → `org.saturn.app.agent.moderation.ModerationActionExecutor`, `ModerationDecision` → `org.saturn.app.agent.moderation.ModerationDecision`, `RoomModerationMonitor` → `org.saturn.app.agent.moderation.RoomModerationMonitor`

## Dependency direction, usages, and cycles

### Observed high-fan-in boundaries
- `AgentContext`, `AgentToolResult`, `AgentToolDescriptor`, `AgentConfig`, `AgentInvocation`, `AgentParticipationConfig`, `AgentRoutingException`, `AgentExecutionLimits`, and `AgentTool` are shared by many direct types and tests. Move these only with import updates or temporary compatibility facades.
- `DefaultAgentRouter` is the central fan-out node: it references request assembly, prompt/response processing, turn policies, tool execution, memory, locks, config, and public API records. It should move last among implementation classes.
- `AgentToolRegistry`/`AgentToolExecutor`/`AgentToolCallValidator` form the tool-engine seam. They reference contracts, schemas, execution state, scheduling, and result recording; split contracts before moving execution.
- `AgentRuntimeFactory`/`AgentRouterFactory`/`AgentInfrastructureFactory`/`AgentToolRegistryFactory` form the composition-root seam and reference nearly every subsystem. Keep them in `routing` and migrate after leaf packages compile.

### Declaration cycles and structural cycles
| Cycle | Evidence | Refactor handling |
|---|---|---|
| `AgentConfig ↔ AgentConfigLoader` | config model references loader and loader constructs/returns config | Put parsing in `config`; make loader depend on model only. If source compatibility requires it, retain a deprecated delegating loader facade temporarily; do not make model depend on loader.
| `AgentResponseCorrector ↔ VerifiedQuoteCatalog` | corrector uses catalog and catalog references corrector’s response contract/type | Move catalog to `routing`/quote policy and invert through a narrow quote-validation interface or move shared response predicate into `api`; preserve exact quote strings and correction behavior.
| `DefaultAgentRouter` ↔ turn/tool/routing collaborators | high connectivity, but no SCC >1 in simple source-reference graph | Migrate leaves first; preserve constructor wiring through package-private factories and test after each edge reduction.

### Package dependency direction to enforce
`api` → no implementation packages; `config` → `api`; `llm`, `persistence`, `sql`, `moderation`, and concrete `tool` adapters → `api` plus their external libraries; `tool.contract` → `api`; `tool.execution` → `api`, `tool.contract`, persistence/sql/tool adapters; `turn` → `api` and tool result abstractions; `routing` → api/config/turn/tool execution/llm/persistence; `room` → api/routing and concrete gateways. Composition factories may depend inward on all layers, but lower layers must not depend on `routing` or factories.

## Compatibility hazards

### Public/package-private boundaries
- Public direct types: `AgentCapability`, `AgentConfig`, `AgentContext`, `AgentConversationContextProvider`, `AgentExecutionLimits`, `AgentInvocation`, `AgentInvocationMode`, `AgentMemoryStore`, `AgentParticipationConfig`, `AgentResult`, `AgentRoomAutomation`, `AgentRouter`, `AgentRoutingException`, `AgentSqlConfig`, `AgentTool`, `AgentToolDescriptor`, `AgentToolResult`, `AgentUserIdentity`, `ToolAccess`, `ToolEffect`, `ToolExample`, `ToolResponseEnvelope`, and `ToolResultMode` (23 actual public top-level types). Their FQNs are source/binary compatibility surfaces for services, listeners, commands, tests, Gson/record serialization, and tool contracts.
- 60 actual direct top-level types are package-private, but tests in `org.saturn.app.agent` instantiate or inspect them directly. Moving them changes access even if all production imports are corrected. Tests named in the evidence include `AgentConfigLoaderTest`, `AgentConfigValueReaderTest`, `AgentFreshDataCoordinatorTest`, `AgentFreshDataFinalValidatorTest`, `AgentToolExecutorTest`, `AgentToolRegistryTest`, `AgentTurnPolicyChainTest`, `AgentCommandChannelPolicyTest`, `DefaultAgentRouterTest`, and many others.
- `AgentTurnPolicyInput.java` has two package-private records. Moving only the file or splitting the records changes same-package access and requires explicit test/package migration. This is the count discrepancy source.
- Nested types are compatibility-sensitive: `AgentCommandChannelPolicy.Result`; `AgentFreshDataCoordinator.Result`, `.ToolResultRenderer`, `.DefinitionProvider`; `AgentPromptCatalog.ResourceSource`; `AgentQuietRegistry.QuietKey`; `AgentResponseFinalizer.Result`; `AgentRoomAutomation.Outcome`; `AgentRoomMessagePipeline.Handler`, `.Decision`, `.Turn`; `AgentSessionLockManager.LockedOperation`; `AgentToolBudgetPolicy.Result`; `AgentToolCallScheduler.ToolCallExecution`; `AgentToolCallValidator.Result`; `AgentToolExecutionLedger.Reservation`; `AgentToolExecutor.Classification`; `AgentToolResultCoordinator.ToolResultRenderer`; `ToolResponseEnvelope.Error`; `VerifiedQuoteCatalog.Entry`. Preserve nesting unless deliberately publishing a compatibility alias.
- Record component names, enum constants, annotations, constructors, exception causes/messages, JSON field names, tool schemas, and persistence timing are behavior contracts. Package migration must not “clean up” any of these.

### Tests and same-package access
- The test tree mirrors `org.saturn.app.agent` and currently relies extensively on package-private access; the inventory found direct test references for virtually every package-private direct type. Existing subpackage tests (`agent.persistence`, `agent.sql`, `agent.tool`, `agent.moderation`, `agent.llm`) similarly rely on package-local collaborators and constructors.
- `AgentServiceImpl`, `EngineImpl`, `Base`, `AgentParticipationHandler`, `LUserCommandImpl`, and service/listener tests consume public agent API types. These are the non-agent callers that must be compiled after every public move.

## Staged migration order

1. Baseline and guardrails: record exact direct-file/type counts, run the already-passing focused agent tests plus `./mvnw spotless:check`, `./mvnw test`, and `./mvnw package`; snapshot public signatures, record components, nested types, JSON/tool schema output, and persistence timing. Do not alter production/tests in Phase 0.
2. Contracts first: create `agent.api` destinations for public records/interfaces/enums and update imports in one atomic compile-safe slice. If compatibility is required, keep old package facades/deprecated forwarding types rather than changing signatures silently.
3. Configuration and leaf policy: move `AgentConfig`, loader/value reader, then text bounds/nick normalizer and pure prompt/quote/command policy leaves. Break the config cycle by making the model independent of the loader.
4. turn` package: move turn records/state/memory, policy interface/chain, freshness and unverified-action policies. Preserve nested policy result types and package-private constructors; migrate same-package tests with the source, or add narrowly scoped test fixtures only if explicitly approved.
5. tool.contract` then `tool.execution`: move schemas/definitions/descriptors first, then validation, registry, scheduling, ledger, executor, invoker, and result coordinator. Keep concrete adapters in existing `agent.tool`; verify tool call ordering, budgets, validation, result recording, and errors.
6. Room and routing: move mention/quiet/lock/room pipeline and automation, then request/response routing and `DefaultAgentRouter`. Keep factories/composition roots last within this slice.
7. Integration packages and cleanup: migrate existing `llm` provider collision deliberately, then persistence/sql/moderation only if there is a concrete dependency benefit. Remove temporary facades only after external callers and tests no longer reference old FQNs.

## Verification strategy

1. Before each move, run the smallest affected test class; after each move run all affected agent tests and compile. Use `git diff --check` and inspect `git status --short` to ensure only task-owned files changed.
2. For public API moves, compile production callers (`service`, `facade`, `listener`, `command`) and tests; use `jdeps`/IDE symbol search or `grep`-equivalent repository search to confirm no old FQN remains except intentional facades.
3. Snapshot and compare: public method/constructor signatures; record component order and annotations; enum constants; nested type binary names; exact exception messages/causes; Gson/raw JSON payloads; tool definitions/access/effect/result modes; SQL validation error codes; repository transaction/persistence timing; provider request/response serialization.
4. Focused behavioral gates: routing correction/finalization, fresh-data and unverified-action policy tests, turn-state/budget/scheduler/ledger tests, tool registry/executor/schema tests, room automation/quiet/mention tests, LLM compatibility tests, persistence repository/schema/transaction tests, SQL parser tests, and moderation tests.
5. Widen in the repository-prescribed order: `./mvnw -Dtest=<FocusedClass> test`, `./mvnw spotless:check`, `./mvnw test`, then `./mvnw package`. The known baseline is 600 tests, 0 failures, 0 errors, 5 skipped; any deviation must be investigated, not normalized.
6. Final structural gates: exactly the intended target tree; no duplicate `OpenAiCompatibleClient`; no direct-package implementation types except explicitly retained compatibility facades; no production/test edits outside migration scope; dependency graph has no inward edge from `api` to implementation or from lower layers to `routing` factories.

## Phase 0 conclusion

The refactor is feasible, but it is not a mechanical directory move. The direct package is currently both public API and a tightly coupled composition root. The count mismatch, package-private tests, nested types, public record/enum names, two declaration cycles, and the router/tool fan-out are the primary risks. The staged order above reduces those risks without changing behavior; implementation should begin only after this inventory is accepted.
