# Full agent package refactor — Phase 1 architecture and migration specification

## 1. Decision and scope

This is an **intentional source-package migration**, not a binary-compatible relocation. All repository production callers and tests are in scope and will be updated to the new fully qualified names. The old `org.saturn.app.agent` declarations are removed once their slice is migrated; no old-package production facades are required for this repository.

This decision is deliberate for three reasons:

1. The repository controls the callers (`service`, `facade`, `listener`, command code, and tests), so retaining two names would prolong the mixed-package architecture.
2. Java has no package alias. In particular, a record cannot be aliased while preserving its record identity, canonical constructor, accessors, annotations, serialization name, and nested/binary name. A wrapper record would be a different public type and would change signatures and serialization.
3. The package-private implementation types are already test-visible through same-package tests; migrating those tests with their source is safer than manufacturing public compatibility surfaces.

If an independently shipped external consumer later requires compatibility, that is a separate compatibility-module decision. A deprecated forwarding **class/interface/exception** facade may be considered there, but records, enums, and value-type signatures must be migrated rather than aliased. This phase does not add facades.

### Scope and validated count

The current direct source directory contains 82 Java files excluding `package-info.java`, but 83 top-level declarations: `AgentTurnPolicyInput.java` contains both `AgentTurnPolicyInput` and `AgentTurnPolicyResult`. This specification migrates all 83 declarations. Existing subpackages (`llm`, `persistence`, `sql`, `tool`, `moderation`) are not silently omitted; they remain integration namespaces in the first pass and are listed in the integration section.

Behavior is invariant: preserve public signatures other than the explicitly changed package names, constructors, annotations, record component order/names, enum constants, exception behavior, JSON/Gson names, tool schemas, SQL error codes, persistence timing, and provider request/response behavior.

## 2. Final package tree

The final tree for the direct agent declarations is:

```text
org/saturn/app/agent/
├── package-info.java                         # package documentation only
├── api/                                      # public contracts and value objects
├── config/                                   # config models and loading
├── routing/                                  # request/response and composition
├── turn/                                     # turn state and turn policies
├── room/                                     # room admission and automation
├── tool/
│   ├── contract/                             # tool schemas and definitions
│   └── execution/                            # validation, scheduling, execution
├── llm/                                      # retained integration boundary
│   └── provider/openai/                      # canonical OpenAI provider
├── persistence/                              # retained persistence integration boundary
├── sql/                                      # retained SQL integration boundary
├── moderation/                               # retained moderation integration boundary
└── tool/                                     # retained concrete tool adapters/gateways
```

The root package must contain no direct implementation/type declarations after migration (only `package-info.java`, if still useful). Existing integration packages remain until a later, separately scoped integration cleanup. `agent.llm.provider.openai.OpenAiCompatibleClient` is the canonical OpenAI client; the duplicate `agent.llm.OpenAiCompatibleClient` source in the dirty worktree must not survive the final cleanup. Update its callers to the provider-qualified class and delete the duplicate, or use a clearly temporary deprecated class facade only if an external compatibility requirement is discovered and approved.

## 3. Exhaustive declaration mapping

Every current direct top-level declaration has exactly one destination. The two records in one source file are intentionally split into two files in `turn`.

### 3.1 API contracts and value objects (21 declarations)

| Current FQN | Final FQN |
|---|---|
| `org.saturn.app.agent.AgentCapability` | `org.saturn.app.agent.api.AgentCapability` |
| `org.saturn.app.agent.AgentContext` | `org.saturn.app.agent.api.AgentContext` |
| `org.saturn.app.agent.AgentConversationContextProvider` | `org.saturn.app.agent.api.AgentConversationContextProvider` |
| `org.saturn.app.agent.AgentExecutionLimits` | `org.saturn.app.agent.api.AgentExecutionLimits` |
| `org.saturn.app.agent.AgentInvocation` | `org.saturn.app.agent.api.AgentInvocation` |
| `org.saturn.app.agent.AgentInvocationMode` | `org.saturn.app.agent.api.AgentInvocationMode` |
| `org.saturn.app.agent.AgentMemoryStore` | `org.saturn.app.agent.api.AgentMemoryStore` |
| `org.saturn.app.agent.AgentParticipationConfig` | `org.saturn.app.agent.api.AgentParticipationConfig` |
| `org.saturn.app.agent.AgentResult` | `org.saturn.app.agent.api.AgentResult` |
| `org.saturn.app.agent.AgentRoomAutomation` | `org.saturn.app.agent.api.AgentRoomAutomation` |
| `org.saturn.app.agent.AgentRouter` | `org.saturn.app.agent.api.AgentRouter` |
| `org.saturn.app.agent.AgentRoutingException` | `org.saturn.app.agent.api.AgentRoutingException` |
| `org.saturn.app.agent.AgentTool` | `org.saturn.app.agent.api.AgentTool` |
| `org.saturn.app.agent.AgentToolDescriptor` | `org.saturn.app.agent.api.AgentToolDescriptor` |
| `org.saturn.app.agent.AgentToolResult` | `org.saturn.app.agent.api.AgentToolResult` |
| `org.saturn.app.agent.AgentUserIdentity` | `org.saturn.app.agent.api.AgentUserIdentity` |
| `org.saturn.app.agent.ToolAccess` | `org.saturn.app.agent.api.ToolAccess` |
| `org.saturn.app.agent.ToolEffect` | `org.saturn.app.agent.api.ToolEffect` |
| `org.saturn.app.agent.ToolExample` | `org.saturn.app.agent.api.ToolExample` |
| `org.saturn.app.agent.ToolResponseEnvelope` | `org.saturn.app.agent.api.ToolResponseEnvelope` |
| `org.saturn.app.agent.ToolResultMode` | `org.saturn.app.agent.api.ToolResultMode` |

`AgentSqlConfig` is intentionally not in `api`; it is an implementation/configuration concern and maps to `config` below. The repository has 23 public direct declarations in total: 21 in `api`, plus public `AgentConfig` and `AgentSqlConfig` in `config`. The category mapping is authoritative; no public type is omitted.

### 3.2 Configuration (4 declarations)

| Current FQN | Final FQN |
|---|---|
| `org.saturn.app.agent.AgentConfig` | `org.saturn.app.agent.config.AgentConfig` |
| `org.saturn.app.agent.AgentConfigLoader` | `org.saturn.app.agent.config.AgentConfigLoader` |
| `org.saturn.app.agent.AgentConfigValueReader` | `org.saturn.app.agent.config.AgentConfigValueReader` |
| `org.saturn.app.agent.AgentSqlConfig` | `org.saturn.app.agent.config.AgentSqlConfig` |

### 3.3 Routing (18 declarations)

| Current FQN | Final FQN |
|---|---|
| `AgentRouterFactory` | `routing.AgentRouterFactory` |
| `DefaultAgentRouter` | `routing.DefaultAgentRouter` |
| `AgentRuntimeFactory` | `routing.AgentRuntimeFactory` |
| `AgentInfrastructure` | `routing.AgentInfrastructure` |
| `AgentInfrastructureFactory` | `routing.AgentInfrastructureFactory` |
| `AgentInvocationFactory` | `routing.AgentInvocationFactory` |
| `AgentRequestAssembler` | `routing.AgentRequestAssembler` |
| `AgentPreparedRequest` | `routing.AgentPreparedRequest` |
| `AgentResponseCorrector` | `routing.AgentResponseCorrector` |
| `AgentResponseFinalizer` | `routing.AgentResponseFinalizer` |
| `AgentResponseSanitizer` | `routing.AgentResponseSanitizer` |
| `AgentPromptCatalog` | `routing.AgentPromptCatalog` |
| `AgentSystemPrompt` | `routing.AgentSystemPrompt` |
| `AgentTextBounds` | `routing.AgentTextBounds` |
| `VerifiedQuoteCatalog` | `routing.VerifiedQuoteCatalog` |
| `AgentCommandIntentPolicy` | `routing.AgentCommandIntentPolicy` |
| `AgentCommandProseGuard` | `routing.AgentCommandProseGuard` |
| `AgentCommandChannelPolicy` | `routing.AgentCommandChannelPolicy` |

For compactness in this and the following tables, an unqualified name means the current FQN is `org.saturn.app.agent.<name>` and the destination is `org.saturn.app.agent.<package>.<name>`.

### 3.4 Turn (15 declarations)

| Current name | Final FQN |
|---|---|
| `AgentExecutionState` | `org.saturn.app.agent.turn.AgentExecutionState` |
| `AgentTurnState` | `org.saturn.app.agent.turn.AgentTurnState` |
| `AgentTurnMemory` | `org.saturn.app.agent.turn.AgentTurnMemory` |
| `AgentTurnPolicy` | `org.saturn.app.agent.turn.AgentTurnPolicy` |
| `AgentTurnPolicyChain` | `org.saturn.app.agent.turn.AgentTurnPolicyChain` |
| `AgentTurnPolicyInput` | `org.saturn.app.agent.turn.AgentTurnPolicyInput` |
| `AgentTurnPolicyResult` | `org.saturn.app.agent.turn.AgentTurnPolicyResult` |
| `AgentFreshDataCoordinator` | `org.saturn.app.agent.turn.AgentFreshDataCoordinator` |
| `AgentFreshDataFinalValidator` | `org.saturn.app.agent.turn.AgentFreshDataFinalValidator` |
| `AgentFreshDataPolicy` | `org.saturn.app.agent.turn.AgentFreshDataPolicy` |
| `AgentFreshDataTurnPolicy` | `org.saturn.app.agent.turn.AgentFreshDataTurnPolicy` |
| `AgentFreshnessPolicy` | `org.saturn.app.agent.turn.AgentFreshnessPolicy` |
| `AgentUnverifiedActionPolicy` | `org.saturn.app.agent.turn.AgentUnverifiedActionPolicy` |
| `AgentMessageHistory` | `org.saturn.app.agent.turn.AgentMessageHistory` |
| `AgentNickNormalizer` | `org.saturn.app.agent.turn.AgentNickNormalizer` |

The source-file count is 14 because the two turn-policy records share one current file. The declaration count is 15.

### 3.5 Room (7 declarations)

| Current name | Final FQN |
|---|---|
| `AgentMentionParser` | `org.saturn.app.agent.room.AgentMentionParser` |
| `AgentQuietRegistry` | `org.saturn.app.agent.room.AgentQuietRegistry` |
| `AgentRoomMessagePipeline` | `org.saturn.app.agent.room.AgentRoomMessagePipeline` |
| `AgentRoomAutomationFactory` | `org.saturn.app.agent.room.AgentRoomAutomationFactory` |
| `DefaultAgentRoomAutomation` | `org.saturn.app.agent.room.DefaultAgentRoomAutomation` |
| `AgentSessionLockManager` | `org.saturn.app.agent.room.AgentSessionLockManager` |
| `ProtectedPrincipalPolicy` | `org.saturn.app.agent.room.ProtectedPrincipalPolicy` |

### 3.6 Tool contracts (4 declarations)

| Current name | Final FQN |
|---|---|
| `AgentToolDefinitionJson` | `org.saturn.app.agent.tool.contract.AgentToolDefinitionJson` |
| `AgentToolSchemas` | `org.saturn.app.agent.tool.contract.AgentToolSchemas` |
| `AgentToolSchemaValidator` | `org.saturn.app.agent.tool.contract.AgentToolSchemaValidator` |
| `AgentToolDefinitionFactory` | `org.saturn.app.agent.tool.contract.AgentToolDefinitionFactory` |

### 3.7 Tool execution (14 declarations)

| Current name | Final FQN |
|---|---|
| `AgentScheduledToolCall` | `org.saturn.app.agent.tool.execution.AgentScheduledToolCall` |
| `AgentToolBudgetPolicy` | `org.saturn.app.agent.tool.execution.AgentToolBudgetPolicy` |
| `AgentToolCallScheduler` | `org.saturn.app.agent.tool.execution.AgentToolCallScheduler` |
| `AgentToolCallValidator` | `org.saturn.app.agent.tool.execution.AgentToolCallValidator` |
| `AgentToolExecutionLedger` | `org.saturn.app.agent.tool.execution.AgentToolExecutionLedger` |
| `AgentToolExecutionMode` | `org.saturn.app.agent.tool.execution.AgentToolExecutionMode` |
| `AgentToolExecutionPolicy` | `org.saturn.app.agent.tool.execution.AgentToolExecutionPolicy` |
| `AgentToolExecutor` | `org.saturn.app.agent.tool.execution.AgentToolExecutor` |
| `AgentToolInvoker` | `org.saturn.app.agent.tool.execution.AgentToolInvoker` |
| `AgentToolRegistry` | `org.saturn.app.agent.tool.execution.AgentToolRegistry` |
| `AgentToolRegistryFactory` | `org.saturn.app.agent.tool.execution.AgentToolRegistryFactory` |
| `AgentToolResultCoordinator` | `org.saturn.app.agent.tool.execution.AgentToolResultCoordinator` |
| `AgentModelVisibleToolResultRenderer` | `org.saturn.app.agent.tool.execution.AgentModelVisibleToolResultRenderer` |
| `ValidatedToolCall` | `org.saturn.app.agent.tool.execution.ValidatedToolCall` |

The category totals are **21 + 4 + 18 + 15 + 7 + 4 + 14 = 83 declarations**. The 82-file/83-declaration discrepancy is solely the two top-level records in `AgentTurnPolicyInput.java`; it is not a duplicate mapping. No declaration may be moved twice or omitted.

### 3.8 Authoritative 83-name migration checklist

The following exact set is the acceptance checklist (23 public declarations plus 60 package-private declarations, with the two records counted separately):

```text
AgentCapability, AgentCommandChannelPolicy, AgentCommandIntentPolicy,
AgentCommandProseGuard, AgentConfig, AgentConfigLoader, AgentConfigValueReader,
AgentContext, AgentConversationContextProvider, AgentExecutionLimits,
AgentExecutionState, AgentFreshDataCoordinator, AgentFreshDataFinalValidator,
AgentFreshDataPolicy, AgentFreshDataTurnPolicy, AgentFreshnessPolicy,
AgentInfrastructure, AgentInfrastructureFactory, AgentInvocation,
AgentInvocationFactory, AgentInvocationMode, AgentMemoryStore, AgentMentionParser,
AgentMessageHistory, AgentModelVisibleToolResultRenderer, AgentNickNormalizer,
AgentParticipationConfig, AgentPreparedRequest, AgentPromptCatalog,
AgentQuietRegistry, AgentRequestAssembler, AgentResponseCorrector,
AgentResponseFinalizer, AgentResponseSanitizer, AgentResult, AgentRoomAutomation,
AgentRoomAutomationFactory, AgentRoomMessagePipeline, AgentRouter,
AgentRouterFactory, AgentRoutingException, AgentRuntimeFactory,
AgentScheduledToolCall, AgentSessionLockManager, AgentSqlConfig, AgentSystemPrompt,
AgentTextBounds, AgentTool, AgentToolBudgetPolicy, AgentToolCallScheduler,
AgentToolCallValidator, AgentToolDefinitionFactory, AgentToolDefinitionJson,
AgentToolDescriptor, AgentToolExecutionLedger, AgentToolExecutionMode,
AgentToolExecutionPolicy, AgentToolExecutor, AgentToolInvoker, AgentToolRegistry,
AgentToolRegistryFactory, AgentToolResult, AgentToolResultCoordinator,
AgentToolSchemaValidator, AgentToolSchemas, AgentTurnMemory, AgentTurnPolicy,
AgentTurnPolicyChain, AgentTurnPolicyInput, AgentTurnPolicyResult, AgentTurnState,
AgentUnverifiedActionPolicy, AgentUserIdentity, DefaultAgentRoomAutomation,
DefaultAgentRouter, ProtectedPrincipalPolicy, ToolAccess, ToolEffect, ToolExample,
ToolResponseEnvelope, ToolResultMode, ValidatedToolCall, VerifiedQuoteCatalog
```

Implementation must generate/verify the mapping mechanically from this checklist and the source declarations; the checklist is intentionally included to prevent a table-count typo from becoming an omitted type.

## 4. Nested declarations and source-file rules

Preserve nested declarations and their enclosing relationship and visibility unless a compiler constraint forces an explicitly reviewed change. The nested inventory is:

- `routing.AgentCommandChannelPolicy.Result`
- `turn.AgentFreshDataCoordinator.Result`, `.ToolResultRenderer`, `.DefinitionProvider`
- `routing.AgentPromptCatalog.ResourceSource`
- `room.AgentQuietRegistry.QuietKey`
- `routing.AgentResponseFinalizer.Result`
- `api.AgentRoomAutomation.Outcome`
- `room.AgentRoomMessagePipeline.Handler`, `.Decision`, `.Turn`
- `room.AgentSessionLockManager.LockedOperation`
- `tool.execution.AgentToolBudgetPolicy.Result`
- `tool.execution.AgentToolCallScheduler.ToolCallExecution`
- `tool.execution.AgentToolCallValidator.Result`
- `tool.execution.AgentToolExecutionLedger.Reservation`
- `tool.execution.AgentToolExecutor.Classification`
- `tool.execution.AgentToolResultCoordinator.ToolResultRenderer`
- `api.ToolResponseEnvelope.Error`
- `routing.VerifiedQuoteCatalog.Entry`

`AgentTurnPolicyInput` and `AgentTurnPolicyResult` must become separate files in `turn`; both remain package-private unless existing callers prove public visibility is required. Do not turn nested result types into top-level records merely to simplify imports. If a moved nested type is accessed outside its new package, expose the smallest existing enclosing API or add a package-private adapter in the owning implementation package; do not broaden visibility gratuitously.

## 5. Dependency direction and cycle breaking

The dependency rule is inward and acyclic:

```text
api
  ↑
config, llm, persistence, sql, moderation, tool adapters
  ↑
tool.contract
  ↑
tool.execution
  ↑
turn
  ↑
routing
  ↑
room
```

Composition-root factories in `routing` may assemble all lower layers. `room` may depend on `routing` and adapter interfaces, but no lower layer may depend on `room` or routing factories. `api` must not import any implementation package. Existing integration packages may depend on `api`, `config`, and their external libraries; they must not import `routing` merely to obtain a value object.

### Cycle A: `AgentConfig` ↔ `AgentConfigLoader`

`config.AgentConfig` is the immutable model and has no dependency on the loader. `config.AgentConfigLoader` depends on `AgentConfig` and `AgentConfigValueReader`; parsing, defaults, and environment/property lookup stay in the loader/reader. Any current static helper on the model that loads configuration must move to the loader or be reduced to a model-only factory. Verify that `AgentConfig` can compile and be unit-tested without `AgentConfigLoader`.

### Cycle B: `AgentResponseCorrector` ↔ `VerifiedQuoteCatalog`

Keep quote data/policy in `routing.VerifiedQuoteCatalog`, but invert the back-reference: `AgentResponseCorrector` consumes a narrow quote lookup/validation abstraction (or a package-private routing predicate) rather than the catalog depending on the corrector's response type. The catalog must contain only quote entries and matching/lookup behavior. Preserve exact quote strings, correction ordering, and output text. Verify both classes independently, then run correction/finalization tests.

### High-fan-in seams

Move `api` contracts first. Split `tool.contract` before `tool.execution`; do not move `AgentToolRegistry`, `AgentToolExecutor`, or `AgentToolCallValidator` until their contract imports resolve. Keep `DefaultAgentRouter`, `AgentRuntimeFactory`, `AgentRouterFactory`, and `AgentInfrastructureFactory` last; they are composition roots, not shared utilities.

## 6. Tests and caller migration

Tests migrate with the production slice:

- A test that tests a moved package-private class changes its package declaration to the class's final package and moves to the corresponding directory. This preserves package-private access.
- A test that tests a public API type may remain in a dedicated test package only if it needs no package-private access; otherwise it moves with the implementation.
- Update imports in all non-agent callers, including `AgentServiceImpl`, `EngineImpl`, `Base`, `AgentParticipationHandler`, `LUserCommandImpl`, and listener/facade/service tests.
- Do not add test-only public accessors or make implementation constructors public just to avoid moving tests.
- Split `AgentTurnPolicyInputTest` fixtures if needed so each record's package-private access is tested from `turn`.
- Keep existing subpackage tests in their corresponding packages, updating imports when a direct declaration moves to `api`, `turn`, `room`, or `tool.*`.

At each slice, repository search must show no stale `org.saturn.app.agent.<moved-name>` reference except in migration notes. The final search must show no direct-package declaration other than `package-info.java` and no old-FQN facade.

## 7. Executable slice order and verification gates

Each slice is atomic: move source and its tests, update imports/package declarations, compile, then run the focused gate before starting the next slice. Do not combine a namespace move with behavior cleanup.

### Gate 0 — baseline and inventory

Record direct-file count 82, declaration count 83, public-signature/record/enum/nested-type inventory, and snapshots of JSON/tool schemas, SQL error codes, persistence timing, and LLM payloads. Run:

```text
./mvnw -Dtest=<existing focused agent tests> test
./mvnw spotless:check
./mvnw test
./mvnw package
```

Known baseline from the forensic run: 600 tests, 0 failures, 0 errors, 5 skipped. Any change is investigated.

### Slice 1 — API contracts

Move the 22 API declarations listed in §3.1 and update all callers/tests. Gate: API compilation, public signature/record-component diff, Gson/JSON and tool-envelope tests, `AgentInvocationTest`, and full agent test set.

### Slice 2 — configuration

Move the four config declarations. Break the config cycle before import cleanup. Gate: `AgentConfigLoaderTest`, `AgentConfigValueReaderTest`, config serialization/default tests, then compile all production callers.

### Slice 3 — routing leaves and pure policies

Move text bounds, prompt/system-prompt/catalog, quote catalog/correction/finalization/sanitization, and command policies according to the mapping. Apply the quote-cycle inversion. Gate: prompt, response-correction/finalization, command-channel, and sanitizer tests.

### Slice 4 — turn

Move state, memory, policy interface/chain, freshness, unverified-action, message history, and the two split records. Migrate same-package tests in the same change. Gate: turn policy chain, fresh-data coordinator/final-validator/turn-policy, unverified-action, state, budget interaction tests.

### Slice 5 — tool contracts

Move definitions, schema JSON, schema validator, and definition factory. Keep concrete tools in `agent.tool`. Gate: exact tool definition JSON/schema snapshots, access/effect/result-mode checks, and schema validation tests.

### Slice 6 — tool execution

Move scheduled calls, budget, validation, ledger, execution mode/policy, executor, invoker, registry/factory, result coordinator, renderer, and validated call. Gate: `AgentToolExecutorTest`, `AgentToolRegistryTest`, scheduler/ledger/budget tests, validation/error/order tests, and concrete adapter integration tests.

### Slice 7 — room

Move mention parsing, quiet registry, session locks, protected-principal policy, room pipeline, automation interface implementation/factory. Gate: mention/quiet/session/room automation tests and listener/service integration tests.

### Slice 8 — routing composition root

Move infrastructure, invocation/request assembly, router factories, runtime factory, and `DefaultAgentRouter` last. Gate: `DefaultAgentRouterTest`, router/runtime/infrastructure factory tests, all service/facade/listener callers, and end-to-end command/room behavior.

### Slice 9 — integration cleanup and final removal

Resolve the duplicate OpenAI client in favor of `agent.llm.provider.openai`, then verify retained `llm`, `persistence`, `sql`, `moderation`, and concrete `tool` packages. Remove any temporary compatibility artifact only after search proves no caller uses it. Gate:

```text
./mvnw spotless:check
./mvnw test
./mvnw package
git diff --check
```

Final structural checks must prove: exactly 83 moved direct declarations, no direct root implementation types, no duplicate `OpenAiCompatibleClient`, no API-to-implementation edge, no lower-layer edge to routing factories, and no behavior snapshot drift.

## 8. Acceptance criteria and non-goals

Acceptance requires all 83 declarations to have the final FQN specified above, all repository callers/tests to compile against those FQNs, same-package tests to retain intended access, and focused plus full Maven verification to pass with the baseline test count unless an independently explained test change is approved.

This phase does not rename types, alter behavior, redesign the public API, migrate all existing integration packages into new domains, or add binary-compatibility facades. It produces the architecture and executable migration contract only; implementation changes belong to subsequent slices.
