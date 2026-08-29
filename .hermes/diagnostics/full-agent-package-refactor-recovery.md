# Full agent package refactor — Slice 3 recovery diagnostics

_Date: 2026-08-19; branch: `develop`; investigation only (no production or test files edited)._

## Executive summary

The worktree is a hybrid, not a clean partial package migration:

- **Slice 1 (API) and Slice 2 (configuration) are materially present and compile/test successfully.** Their source files exist under `agent/api` and `agent/config`, the old tracked root files are deleted, and callers/tests have been updated to the new API/config FQNs.
- **The remaining 58 direct declarations are physically present in 57 untracked root-package source files** (`AgentTurnPolicyInput.java` contains the two turn records). Including `package-info.java`, the current root directory has 58 Java files. Their package declarations still say `org.saturn.app.agent`, so they are not Slice 3/4/5/6/7/8 moves despite the Git rename/deletion noise.
- Many of those root files and tests contain broad `import org.saturn.app.agent.api.*;` additions. They are scaffolding/cleanup debt, not a finished migration.
- The current Maven build passes only because the old-package root copies are still compiled. `mvn` without `clean` initially reported “Nothing to compile”; a clean compile and the full test suite also pass against this hybrid source set. This does **not** demonstrate compliance with the target package tree.
- The prior reported missing-symbol/package-private failures are consistent with attempting to move routing before moving its turn/tool/room dependencies and with broad import edits that did not move the declaring types. They are not reproduced by the current hybrid tree.

## Evidence captured

`git status --short` shows:

- tracked deletions for the old API/config files;
- tracked `RD` rename/deletion pairs for the attempted routing destinations;
- modified root implementation files with API imports;
- untracked `agent/api`, `agent/config`, and `agent/llm/provider/openai` trees;
- untracked root copies for all remaining direct declarations;
- many unrelated untracked artifacts (`.idea`, `database`, `config.toml`, logs, proxy/trip files, etc.). These must remain untouched.

Commands run:

- `git diff --stat`, `git diff --name-status`, `git diff --check`
- `mvn clean compile` → **BUILD SUCCESS**, 300 production source files
- `mvn test` → **BUILD SUCCESS**, 600 tests, 0 failures, 0 errors, 5 skipped

The passing build is a false structural green: the root copies make old-package references resolve. Do not use reset/checkout or delete untracked artifacts to obtain a cleaner status.

## Slice 1/2 classification

### Safe/complete enough to preserve

The following target files exist and are package-correct:

- `src/main/java/org/saturn/app/agent/api/` — 21 declarations:
  `AgentCapability`, `AgentContext`, `AgentConversationContextProvider`,
  `AgentExecutionLimits`, `AgentInvocation`, `AgentInvocationMode`,
  `AgentMemoryStore`, `AgentParticipationConfig`, `AgentResult`,
  `AgentRoomAutomation`, `AgentRouter`, `AgentRoutingException`, `AgentTool`,
  `AgentToolDescriptor`, `AgentToolResult`, `AgentUserIdentity`, `ToolAccess`,
  `ToolEffect`, `ToolExample`, `ToolResponseEnvelope`, `ToolResultMode`.
- `src/main/java/org/saturn/app/agent/config/` — 4 declarations:
  `AgentConfig`, `AgentConfigLoader`, `AgentConfigValueReader`, `AgentSqlConfig`.

Most API files are exact package-only moves. The few intentional dependency edits are:

- `api.AgentExecutionLimits` imports `config.AgentConfig`;
- `api.AgentMemoryStore` imports `config.AgentConfig`;
- `api.AgentParticipationConfig` imports `config.AgentConfigValueReader`;
- `api.AgentTool` imports the still-root `AgentToolSchemas` (must be repaired when Slice 5 moves tool contracts);
- `api.AgentToolDescriptor` currently imports root `AgentToolSchemas` and calls it instead of the old validator (must be repaired with the contract move).

The config model/loader cycle was partially broken correctly: `config.AgentConfig` no longer loads via the loader, while `config.AgentConfigLoader` owns loading. However, `AgentConfigLoader` was broadened from package-private to `public final` with public `load`; this is an **unnecessary visibility change** unless an external caller has been proven. Preserve package-private access where the final callers/tests permit it, per the specification.

The old config tests are deleted from the old directory and replacement files exist under `src/test/java/org/saturn/app/agent/config/`. This is consistent with Slice 2. API tests remain in the old test package but import the public API types; that is acceptable for public contracts, though implementation tests must move with their package-private implementation slices.

### Not Slice 1/2 and must not be mistaken for verified work

- `src/main/java/org/saturn/app/agent/llm/provider/openai/OpenAiCompatibleClient.java` is present as the canonical provider client, but the old `agent/llm/OpenAiCompatibleClient.java` is also retained as a new compatibility facade. The full-refactor spec explicitly says the duplicate must not survive final cleanup. Defer this to integration cleanup: migrate callers to the provider-qualified class, then remove the facade and its untracked `OpenAiCompatibleClientCompatibilityTest.java` unless an independently approved compatibility requirement exists.
- `package-info.java` still links `org.saturn.app.agent.AgentRuntimeFactory`; after the routing move this Javadoc must point to `org.saturn.app.agent.routing.AgentRuntimeFactory`.

## Partial Slice 3+ state and exact files to move

All of the following files are currently at `src/main/java/org/saturn/app/agent/<Name>.java`, are untracked working-tree copies, and still declare `package org.saturn.app.agent;`. They must be moved (not duplicated) to the stated destination, then imports and same-package tests must be updated in dependency order.

### Routing (18)

`AgentCommandChannelPolicy`, `AgentCommandIntentPolicy`, `AgentCommandProseGuard`,
`AgentInfrastructure`, `AgentInfrastructureFactory`, `AgentInvocationFactory`,
`AgentPreparedRequest`, `AgentPromptCatalog`, `AgentRequestAssembler`,
`AgentResponseCorrector`, `AgentResponseFinalizer`, `AgentResponseSanitizer`,
`AgentRouterFactory`, `AgentRuntimeFactory`, `AgentSystemPrompt`, `AgentTextBounds`,
`DefaultAgentRouter`, `VerifiedQuoteCatalog` → `agent/routing/`.

### Turn (15 declarations in 14 files)

`AgentExecutionState`, `AgentTurnState`, `AgentTurnMemory`, `AgentTurnPolicy`,
`AgentTurnPolicyChain`, `AgentTurnPolicyInput` (split into separate
`AgentTurnPolicyInput.java` and `AgentTurnPolicyResult.java`),
`AgentFreshDataCoordinator`, `AgentFreshDataFinalValidator`, `AgentFreshDataPolicy`,
`AgentFreshDataTurnPolicy`, `AgentFreshnessPolicy`, `AgentUnverifiedActionPolicy`,
`AgentMessageHistory`, `AgentNickNormalizer` → `agent/turn/`.

### Room (7)

`AgentMentionParser`, `AgentQuietRegistry`, `AgentRoomMessagePipeline`,
`AgentRoomAutomationFactory`, `DefaultAgentRoomAutomation`,
`AgentSessionLockManager`, `ProtectedPrincipalPolicy` → `agent/room/`.

### Tool contracts (4)

`AgentToolDefinitionJson`, `AgentToolSchemas`, `AgentToolSchemaValidator`,
`AgentToolDefinitionFactory` → `agent/tool/contract/`.

### Tool execution (14)

`AgentScheduledToolCall`, `AgentToolBudgetPolicy`, `AgentToolCallScheduler`,
`AgentToolCallValidator`, `AgentToolExecutionLedger`, `AgentToolExecutionMode`,
`AgentToolExecutionPolicy`, `AgentToolExecutor`, `AgentToolInvoker`,
`AgentToolRegistry`, `AgentToolRegistryFactory`, `AgentToolResultCoordinator`,
`AgentModelVisibleToolResultRenderer`, `ValidatedToolCall` →
`agent/tool/execution/`.

The remaining root direct source set is therefore not safe to delete until each file is moved and its callers compile. Do not remove the root copies first: they are currently the only definitions making the worktree compile.

## Accidental broad imports and visibility changes

### Wildcard imports

There are broad `import org.saturn.app.agent.api.*;` additions in the following modified production files:

`AgentCommandChannelPolicy`, `AgentCommandIntentPolicy`, `AgentExecutionState`,
`AgentFreshDataCoordinator`, `AgentFreshDataFinalValidator`, `AgentFreshDataPolicy`,
`AgentInvocationFactory`, `AgentModelVisibleToolResultRenderer`, `AgentQuietRegistry`,
`AgentRequestAssembler`, `AgentResponseCorrector`, `AgentResponseFinalizer`,
`AgentRoomAutomationFactory`, `AgentRoomMessagePipeline`, `AgentRouterFactory`,
`AgentRuntimeFactory`, `AgentSessionLockManager`, `AgentSystemPrompt`,
`AgentToolCallScheduler`, `AgentToolCallValidator`, `AgentToolDefinitionFactory`,
`AgentToolExecutionLedger`, `AgentToolExecutionPolicy`, `AgentToolExecutor`,
`AgentToolInvoker`, `AgentToolRegistry`, `AgentToolResultCoordinator`,
`AgentTurnMemory`, `AgentTurnPolicy`, `AgentTurnPolicyChain`, `AgentTurnState`,
`AgentUnverifiedActionPolicy`, `DefaultAgentRoomAutomation`, `DefaultAgentRouter`,
`ValidatedToolCall`.

The same wildcard was added to numerous root-package agent tests, including the command-policy, context, execution-state/limits, fresh-data, invocation, response, room, router, tool, turn, and envelope tests. Replace these with explicit imports during each package move; do not carry wildcard imports into the final result. The concrete adapters also still have mixed imports (`AgentPromptCatalog`, `AgentToolSchemas`, `AgentToolRegistry`, and `AgentNickNormalizer` remain root imports), proving the migration is incomplete.

### Visibility

The current diff adds exactly one accidental implementation visibility broadening:

- `AgentFreshDataCoordinator`: `final class` → `public final class`.

The moved config copy also broadens:

- `AgentConfigLoader`: package-private `final class`/package-private `load` → `public final class`/`public static load`.

`AgentToolRegistry` is currently correctly spelled `public final class AgentToolRegistry`; there is **no `public public final` text in the current worktree** and no such change in the current `git diff`. The previously reported double-public compiler error was therefore either from an earlier transient version or from a stopped agent's intermediate buffer, not current on-disk source. Remove any recurrence rather than treating it as a required API change.

## Why the failed Slice 3 produced missing symbols/access failures

The source move order violated the spec's high-fan-in rule. Routing files were prepared/moved while turn, room, and tool-contract/execution declarations remained in the root package. Once package declarations change:

1. Every unqualified cross-slice reference needs an explicit import.
2. Package-private classes, nested records, constructors, methods, and nested result types stop being accessible across `routing`, `turn`, `room`, and `tool.*`.
3. `AgentTool`/`AgentToolDescriptor` currently point at root `AgentToolSchemas`; moving only one side creates missing symbols.
4. `AgentFreshDataCoordinator`, `AgentTurnPolicyInput/Result`, `AgentToolBudgetPolicy.Result`, and similar package-private implementation seams are used by the router; they need a reviewed public boundary or a package-local adapter, not blanket public modifiers.
5. The root `package-info.java` Javadoc reference becomes unresolved when `AgentRuntimeFactory` is finally moved.
6. The old OpenAI facade/provider split adds another source of duplicate or stale FQNs.

The repair must therefore be staged and compiled after each slice, rather than attempting a broad import substitution.

## Concrete repair plan (no behavior changes)

1. **Freeze the good worktree state.** Preserve the API/config target files and all unrelated untracked artifacts. Do not reset, checkout, clean, or delete root copies yet. Record this diagnostics file separately.
2. **Normalize Slice 1/2 only.** Verify API/config target contents against the old tracked versions plus intentional package/import changes. Revert only unjustified visibility widening (`AgentConfigLoader`) if no non-config caller requires it. Replace broad API wildcards in files being touched with explicit imports; do not mix this with behavior edits.
3. **Move tool contracts first.** Move the four contract files and update `api.AgentTool` and `api.AgentToolDescriptor` to `agent.tool.contract.*`. Move their tests with package-private access preserved. Compile and run contract-focused tests.
4. **Move API-dependent execution.** Move the 14 execution declarations to `agent.tool.execution`; explicitly import API, contract, LLM, config, and turn types. Review each package-private seam; expose only the smallest needed API or retain a package-local adapter. Compile and run executor/registry/scheduler/ledger tests.
5. **Move turn.** Move the 14 files and split `AgentTurnPolicyInput`/`AgentTurnPolicyResult`. Move turn tests to `agent.turn`; preserve package-private records unless a routing caller genuinely requires a public boundary. Add a narrow public method/type only where the compiler proves it is needed.
6. **Move room.** Move the seven room files and their same-package tests. Update listener/service integration imports. Compile room and integration tests.
7. **Move routing leaves, then composition roots.** Move routing policies/leaves first, invert the quote-catalog/corrector dependency as specified, then move `AgentInfrastructure`, `AgentInvocationFactory`, request assembly, router/runtime factories, and `DefaultAgentRouter` last. Update `package-info.java` to the routing FQN.
8. **Clean integration boundaries.** Migrate concrete tools, persistence, SQL, moderation, services, facade, listener, and command callers to explicit final FQNs. Resolve the duplicate OpenAI client in favor of `agent.llm.provider.openai`; remove the compatibility facade/test unless compatibility is explicitly approved.
9. **Verify structure and behavior.** Run `mvn clean compile`, focused tests after every slice, then `mvn spotless:check`, `mvn test`, `mvn package`, `git diff --check`, and mechanical checks for exactly 83 final declarations, no direct root declarations other than `package-info.java`, no duplicate OpenAI client, no stale old-FQN imports, and no wildcard imports.

## Files that are unrelated and must remain untouched

The status output contains unrelated untracked/user artifacts: `.aider.chat.history.md`, `.aider.input.history`, `.aider.tags.cache.v4/`, `.idea/`, `config.toml`, `database/`, `dependency-reduced-pom.xml`, `log.txt`, `nicetrips.txt`, `proxy.txt`, `saturn.iml`, `trips.txt`, `working.txt`, and the broader `.hermes/` tree except this diagnostics report. This investigation did not modify them.
