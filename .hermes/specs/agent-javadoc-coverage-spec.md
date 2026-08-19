# Agent Javadoc Coverage Specification

## Scope and audit basis

This is a Phase 1 architecture/audit specification only. The audit covers the current
working-tree contents recursively below:

`src/main/java/org/saturn/app/agent`

The audit was performed against the filesystem on 2026-08-19, with `AGENTS.md`,
`AGENTIC_ARCHITECTURE.md`, and existing Java source documentation read for conventions.
The repository has substantial unrelated/parallel dirty and untracked work; this phase
must not normalize, revert, stage, or otherwise alter it.

`package-info.java` is included in scope. It has a package Javadoc at line 1 and therefore
satisfies the package-documentation requirement.

## Inventory counts

| Measure | Count |
|---|---:|
| Java source files | 129 |
| Files containing a named type | 128 |
| Named types total | 166 |
| Top-level named types | 128 |
| Nested named types | 38 |
| Classes | 81 |
| Interfaces | 23 |
| Enums | 12 |
| Records | 50 |
| Types with a directly associated Javadoc | 73 |
| Types missing a directly associated Javadoc | 93 |
| Package-info files | 1 |
| Package-info files with Javadoc | 1 |

The top-level/nested total is 128 + 38 = 166. The nested count includes private and
package-private helper types; it is not limited to public API. The scanner recognizes
named `class`, `interface`, `enum`, and `record` declarations while ignoring comments and
literals, and associates a Javadoc block with a declaration when a `/** ... */` block is
the immediately preceding documentation (allowing blank lines and annotations).

### Types by package

| Package below `org.saturn.app.agent` | Types |
|---|---:|
| `api` | 23 |
| `config` | 4 |
| `llm` | 7 |
| `llm.provider.openai` | 1 |
| `moderation` | 12 |
| `persistence` | 20 |
| `room` | 12 |
| `routing` | 22 |
| `sql` | 6 |
| `tool` | 17 |
| `tool.contract` | 4 |
| `tool.execution` | 20 |
| `turn` | 18 |

## Exhaustive missing-Javadoc inventory

Each entry is `repository-relative-file:line — declaration`. These are the 93 type
declarations that the implementation phase must document.

### `api` (17)

- `src/main/java/org/saturn/app/agent/api/AgentCapability.java:3` — `enum AgentCapability`
- `src/main/java/org/saturn/app/agent/api/AgentConversationContextProvider.java:4` — `interface AgentConversationContextProvider`
- `src/main/java/org/saturn/app/agent/api/AgentInvocation.java:6` — `record AgentInvocation`
- `src/main/java/org/saturn/app/agent/api/AgentInvocationMode.java:3` — `enum AgentInvocationMode`
- `src/main/java/org/saturn/app/agent/api/AgentMemoryStore.java:7` — `interface AgentMemoryStore`
- `src/main/java/org/saturn/app/agent/api/AgentParticipationConfig.java:8` — `record AgentParticipationConfig`
- `src/main/java/org/saturn/app/agent/api/AgentResult.java:5` — `record AgentResult`
- `src/main/java/org/saturn/app/agent/api/AgentRoomAutomation.java:7` — `interface AgentRoomAutomation`
- `src/main/java/org/saturn/app/agent/api/AgentRoomAutomation.java:8` — nested `enum Outcome`
- `src/main/java/org/saturn/app/agent/api/AgentRoutingException.java:3` — `class AgentRoutingException`
- `src/main/java/org/saturn/app/agent/api/AgentToolResult.java:5` — `record AgentToolResult`
- `src/main/java/org/saturn/app/agent/api/AgentUserIdentity.java:8` — `record AgentUserIdentity`
- `src/main/java/org/saturn/app/agent/api/ToolAccess.java:3` — `enum ToolAccess`
- `src/main/java/org/saturn/app/agent/api/ToolEffect.java:3` — `enum ToolEffect`
- `src/main/java/org/saturn/app/agent/api/ToolExample.java:5` — `record ToolExample`
- `src/main/java/org/saturn/app/agent/api/ToolResponseEnvelope.java:61` — nested `record Error`
- `src/main/java/org/saturn/app/agent/api/ToolResultMode.java:3` — `enum ToolResultMode`

### `llm` and provider (8)

- `src/main/java/org/saturn/app/agent/llm/LlmClient.java:3` — `interface LlmClient`
- `src/main/java/org/saturn/app/agent/llm/LlmException.java:3` — `class LlmException`
- `src/main/java/org/saturn/app/agent/llm/LlmMessage.java:5` — `record LlmMessage`
- `src/main/java/org/saturn/app/agent/llm/LlmRequest.java:6` — `record LlmRequest`
- `src/main/java/org/saturn/app/agent/llm/LlmResponse.java:5` — `record LlmResponse`
- `src/main/java/org/saturn/app/agent/llm/LlmToolCall.java:3` — `record LlmToolCall`
- `src/main/java/org/saturn/app/agent/llm/UnsupportedResponseFormatException.java:3` — `class UnsupportedResponseFormatException`
- `src/main/java/org/saturn/app/agent/llm/provider/openai/OpenAiCompatibleClient.java:26` — `class OpenAiCompatibleClient`

### `moderation` (12)

- `src/main/java/org/saturn/app/agent/moderation/AgentModerationConfig.java:8` — `record AgentModerationConfig`
- `src/main/java/org/saturn/app/agent/moderation/EngineModerationActionExecutor.java:10` — `class EngineModerationActionExecutor`
- `src/main/java/org/saturn/app/agent/moderation/ModerationAction.java:3` — `enum ModerationAction`
- `src/main/java/org/saturn/app/agent/moderation/ModerationActionExecutor.java:4` — `interface ModerationActionExecutor`
- `src/main/java/org/saturn/app/agent/moderation/ModerationDecision.java:6` — `record ModerationDecision`
- `src/main/java/org/saturn/app/agent/moderation/RoomModerationMonitor.java:19` — `class RoomModerationMonitor`
- `src/main/java/org/saturn/app/agent/moderation/RoomModerationMonitor.java:240` — nested `interface TimedEvent`
- `src/main/java/org/saturn/app/agent/moderation/RoomModerationMonitor.java:244` — nested `record TimedMessage`
- `src/main/java/org/saturn/app/agent/moderation/RoomModerationMonitor.java:246` — nested `record TimedJoin`
- `src/main/java/org/saturn/app/agent/moderation/RoomModerationMonitor.java:248` — nested `record ActionKey`
- `src/main/java/org/saturn/app/agent/moderation/RoomModerationMonitor.java:250` — nested `record OffenceState`
- `src/main/java/org/saturn/app/agent/moderation/RoomModerationMonitor.java:252` — nested `enum OffenceStage`

### `persistence` (19)

- `src/main/java/org/saturn/app/agent/persistence/AgentDatabaseSchema.java:10` — `record AgentDatabaseSchema`
- `src/main/java/org/saturn/app/agent/persistence/AgentDatabaseSchema.java:30` — nested `record Table`
- `src/main/java/org/saturn/app/agent/persistence/AgentDatabaseSchema.java:40` — nested `record Column`
- `src/main/java/org/saturn/app/agent/persistence/AgentDatabaseSchema.java:48` — nested `record Index`
- `src/main/java/org/saturn/app/agent/persistence/AgentDatabaseSchema.java:55` — nested `record ForeignKey`
- `src/main/java/org/saturn/app/agent/persistence/AgentPersistenceException.java:6` — `class AgentPersistenceException`
- `src/main/java/org/saturn/app/agent/persistence/AgentQueryRepository.java:6` — `interface AgentQueryRepository`
- `src/main/java/org/saturn/app/agent/persistence/AgentSchemaRepository.java:4` — `interface AgentSchemaRepository`
- `src/main/java/org/saturn/app/agent/persistence/AgentSqlRepository.java:7` — `interface AgentSqlRepository`
- `src/main/java/org/saturn/app/agent/persistence/AgentSqlResult.java:8` — `record AgentSqlResult`
- `src/main/java/org/saturn/app/agent/persistence/H2AgentMemoryStore.java:17` — `class H2AgentMemoryStore`
- `src/main/java/org/saturn/app/agent/persistence/H2AgentQueryRepository.java:12` — `class H2AgentQueryRepository`
- `src/main/java/org/saturn/app/agent/persistence/H2AgentSchemaRepository.java:15` — `class H2AgentSchemaRepository`
- `src/main/java/org/saturn/app/agent/persistence/H2AgentSchemaRepository.java:138` — nested `record IndexHeader`
- `src/main/java/org/saturn/app/agent/persistence/H2AgentSqlRepository.java:23` — `class H2AgentSqlRepository`
- `src/main/java/org/saturn/app/agent/persistence/H2AgentSqlRepository.java:211` — nested `record BoundedValue`
- `src/main/java/org/saturn/app/agent/persistence/H2ReadOnlyConnectionFactory.java:8` — `class H2ReadOnlyConnectionFactory`
- `src/main/java/org/saturn/app/agent/persistence/H2TransactionExecutor.java:7` — `class H2TransactionExecutor`
- `src/main/java/org/saturn/app/agent/persistence/H2TransactionExecutor.java:33` — nested `interface SqlWork`
- `src/main/java/org/saturn/app/agent/persistence/RepositoryAgentConversationContextProvider.java:8` — `class RepositoryAgentConversationContextProvider`

### `room` (10)

- `src/main/java/org/saturn/app/agent/room/AgentMentionParser.java:7` — `class AgentMentionParser`
- `src/main/java/org/saturn/app/agent/room/AgentQuietRegistry.java:14` — `class AgentQuietRegistry`
- `src/main/java/org/saturn/app/agent/room/AgentQuietRegistry.java:65` — nested `record QuietKey`
- `src/main/java/org/saturn/app/agent/room/AgentRoomMessagePipeline.java:182` — nested `interface Handler`
- `src/main/java/org/saturn/app/agent/room/AgentRoomMessagePipeline.java:186` — nested `enum Decision`
- `src/main/java/org/saturn/app/agent/room/AgentRoomMessagePipeline.java:192` — nested `class Turn`
- `src/main/java/org/saturn/app/agent/room/AgentSessionLockManager.java:28` — nested `interface LockedOperation`
- `src/main/java/org/saturn/app/agent/room/DefaultAgentRoomAutomation.java:15` — `class DefaultAgentRoomAutomation`

### `routing` (10)

- `src/main/java/org/saturn/app/agent/routing/AgentCommandChannelPolicy.java:169` — nested `record Result`
- `src/main/java/org/saturn/app/agent/routing/AgentCommandProseGuard.java:17` — `class AgentCommandProseGuard`
- `src/main/java/org/saturn/app/agent/routing/AgentInvocationFactory.java:16` — `class AgentInvocationFactory`
- `src/main/java/org/saturn/app/agent/routing/AgentPromptCatalog.java:92` — nested `interface ResourceSource`
- `src/main/java/org/saturn/app/agent/routing/AgentResponseFinalizer.java:120` — nested `record Result`
- `src/main/java/org/saturn/app/agent/routing/AgentRuntimeFactory.java:21` — `class AgentRuntimeFactory`
- `src/main/java/org/saturn/app/agent/routing/AgentSystemPrompt.java:11` — `class AgentSystemPrompt`
- `src/main/java/org/saturn/app/agent/routing/VerifiedQuoteCatalog.java:90` — nested `record Entry`

### `sql` (6)

- `src/main/java/org/saturn/app/agent/sql/AgentSqlErrorCode.java:3` — `enum AgentSqlErrorCode`
- `src/main/java/org/saturn/app/agent/sql/AgentSqlPolicy.java:6` — `interface AgentSqlPolicy`
- `src/main/java/org/saturn/app/agent/sql/AgentSqlPolicyException.java:5` — `class AgentSqlPolicyException`
- `src/main/java/org/saturn/app/agent/sql/JSqlParserAgentSqlPolicy.java:26` — `class JSqlParserAgentSqlPolicy`
- `src/main/java/org/saturn/app/agent/sql/JSqlParserAgentSqlPolicy.java:141` — nested `class PolicyTablesNamesFinder`
- `src/main/java/org/saturn/app/agent/sql/ValidatedAgentSql.java:5` — `record ValidatedAgentSql`

### `tool` (5)

- `src/main/java/org/saturn/app/agent/tool/AgentRoomDirectory.java:12` — nested `record RoomSnapshot`
- `src/main/java/org/saturn/app/agent/tool/SaturnCommandGateway.java:26` — nested `record CommandExecution`
- `src/main/java/org/saturn/app/agent/tool/SaturnCommandToolCatalog.java:188` — nested `record CommandProfile`
- `src/main/java/org/saturn/app/agent/tool/contract/AgentToolSchemaValidator.java:7` — `class AgentToolSchemaValidator`

### `tool.execution` (10)

- `src/main/java/org/saturn/app/agent/tool/execution/AgentToolBudgetPolicy.java:18` — nested `record Result`
- `src/main/java/org/saturn/app/agent/tool/execution/AgentToolCallScheduler.java:131` — nested `interface ToolCallExecution`
- `src/main/java/org/saturn/app/agent/tool/execution/AgentToolCallValidator.java:105` — nested `record Result`
- `src/main/java/org/saturn/app/agent/tool/execution/AgentToolExecutionLedger.java:62` — nested `enum Reservation`
- `src/main/java/org/saturn/app/agent/tool/execution/AgentToolExecutor.java:191` — nested `record Classification`
- `src/main/java/org/saturn/app/agent/tool/execution/AgentToolResultCoordinator.java:79` — nested `interface ToolResultRenderer`

### `turn` (4)

- `src/main/java/org/saturn/app/agent/turn/AgentFreshDataCoordinator.java:168` — nested `record Result`
- `src/main/java/org/saturn/app/agent/turn/AgentFreshDataCoordinator.java:171` — nested `interface ToolResultRenderer`
- `src/main/java/org/saturn/app/agent/turn/AgentFreshDataCoordinator.java:176` — nested `interface DefinitionProvider`
- `src/main/java/org/saturn/app/agent/turn/AgentFreshnessPolicy.java:9` — `class AgentFreshnessPolicy`

## Documentation rules

1. Add one `/** ... */` block immediately before every missing named type. The rule applies
   equally to public, protected, package-private, and private types, including nested types.
2. Keep descriptions concise and factual, matching the neighboring style: a one-sentence
   summary for small contracts/value types, with a second paragraph only when lifecycle,
   ordering, persistence, security, or provider behavior needs clarification.
3. Public `api` types must describe the extension contract or observable value semantics;
   interfaces should state what implementers/consumers provide or receive. Do not document
   private implementation details as public guarantees.
4. Records must explain the value represented and any important normalization, identity,
   nullability, serialization, or security meaning. Enums must describe the domain and the
   meaning of the constants as a set. Exceptions must state the failure condition.
5. Package-private/private implementation types and nested helpers still receive a useful
   purpose/invariant sentence, but should not gain speculative API promises. Functional
   interfaces should say what operation the function performs.
6. Link to nearby agent types with `{@link ...}` when that improves navigation; use `{@code}`
   for literal package names, protocol tokens, SQL concepts, or configuration keys. Follow
   existing Google Java Format layout and plain repository terminology.
7. Preserve behavior and all signatures, annotations, visibility, record components, enum
   constants, serialization shape, and nesting. This phase specifies documentation only;
   no production or test source is to be changed here.
8. Do not broaden the task into missing method/field/record-component documentation unless
   a later acceptance criterion explicitly requires it. The 100% target in this spec is all
   named types plus the package-info Javadoc.

## Minimal implementation plan

1. Work package-by-package through the exhaustive list above, adding only the missing type
   Javadocs.
2. Start with `api`, then `llm`, `config`, `routing`, `turn`, `room`, `tool*`, `persistence`,
   `sql`, and `moderation`; this puts externally meaningful contracts first while keeping
   internal helpers tied to their owning type.
3. Re-run the mechanical inventory after each package group and at the end. Confirm exactly
   166 named types, exactly 166 documented types, and one documented `package-info.java`.
4. Run formatting and the normal project verification after implementation. Do not include or
   alter unrelated dirty/untracked artifacts.

## Mechanical verification

Use a small source scanner (or an equivalent build plugin/check) that recursively reads only
`src/main/java/org/saturn/app/agent/**/*.java` and:

- masks comments and string/character literals before finding named `class`, `interface`,
  `enum`, and `record` declarations;
- tracks brace depth so nested declarations cannot be skipped;
- requires a `/** ... */` block immediately preceding each declaration, allowing only blank
  lines and annotations between the block and declaration;
- separately requires `src/main/java/org/saturn/app/agent/package-info.java` to contain a
  package-level `/** ... */` block;
- reports file, line, kind, and name for every failure; and
- fails if totals differ from 166 declarations, 128 top-level declarations, 38 nested
  declarations, or if any missing list remains.

A practical independent check can be run from the repository root with a Python scanner using
`pathlib.Path.rglob`, a comment/string masker, the declaration regex
`\b(class|interface|enum|record)\s+([A-Za-z_$][\w$]*)`, and brace-depth tracking. The final
quality gate should also run `./mvnw spotless:check` and `./mvnw package`; Javadoc generation
may be used as an additional check if the project later adds/configures a Maven Javadoc
plugin, but it must not replace the declaration-coverage scanner because compiler/doclint
configuration and external imports can obscure private nested-type coverage.
