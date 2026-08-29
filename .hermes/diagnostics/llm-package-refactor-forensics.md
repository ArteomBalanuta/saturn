# LLM Package Refactor — Phase 0 Forensic Architecture Report

**Repository:** `/Users/ab/workspace/projects/saturn`
**Baseline:** `22ef9d9 fix(agent): verify quotes and gate command tools`
**Scope:** read-only investigation; no production or test files were changed.

## Executive findings

- The current package contains three coupled concerns: provider-neutral value/contracts, provider transport, and provider/transport exceptions.
- The type graph is small, but the public value records are used pervasively throughout orchestration, policy, persistence, and tests. There are 36 production files and 25 test files with at least one reference to the eight classes (including their definitions).
- All eight classes are public. The records are final by language definition, so they cannot be subclassed as source-compatible compatibility facades. Generic signatures such as `List<LlmMessage>` and lambda signatures involving `LlmClient` make “type aliases” impossible in Java.
- A transparent relocation of all eight public types is therefore a breaking source and binary API change. The lowest-risk solution is staged: keep the existing `org.saturn.app.agent.llm` public contract types stable, move only the concrete provider implementation behind a provider/infrastructure subpackage, and introduce any new domain packages only with explicit adapters and a deprecation/migration window.

## Current package and dependency graph

Current files:

```text
org.saturn.app.agent.llm
├── LlmClient                         interface
├── OpenAiCompatibleClient             final HTTP implementation
├── LlmRequest                         record
├── LlmResponse                        record
├── LlmMessage                         record
├── LlmToolCall                        record
├── LlmException                       checked exception
└── UnsupportedResponseFormatException final checked-exception subtype
```

Type-level graph:

```text
LlmClient
 ├─ complete(LlmRequest)
 ├─ returns LlmResponse
 └─ throws LlmException

LlmRequest
 ├─ List<LlmMessage>
 └─ List<JsonObject> tools + cache/response-format flags

LlmMessage
 └─ List<LlmToolCall>

LlmResponse
 └─ List<LlmToolCall>

OpenAiCompatibleClient
 ├─ implements LlmClient
 ├─ consumes AgentConfig
 ├─ serializes LlmRequest/LlmMessage/LlmToolCall using Gson
 ├─ parses LlmResponse/LlmToolCall
 ├─ throws LlmException
 └─ throws UnsupportedResponseFormatException for recognized 400/422 format failures

UnsupportedResponseFormatException ──extends──> LlmException
```

Runtime dependency flow:

```text
AgentRouterFactory
  -> OpenAiCompatibleClient(AgentConfig)
  -> DefaultAgentRouter / policies
       -> LlmClient.complete(LlmRequest)
       -> LlmResponse
       -> LlmMessage / LlmToolCall for history and tool execution
  -> H2AgentMemoryStore
       -> persists/loads List<LlmMessage>
```

The client has no provider interface beyond `LlmClient`; the package architecture document explicitly describes `agent.llm` as holding both provider-neutral records and the OpenAI-compatible HTTP client. There are no `package-info.java` files under `agent.llm`, `agent.tool`, `agent.persistence`, `agent.sql`, or `agent.moderation`; only the root `agent/package-info.java` documents package ownership. Existing conventions therefore favor domain subpackages without requiring package descriptors.

## Class responsibilities and contract details

### `LlmClient`

- Public functional-looking interface (one abstract method): `LlmResponse complete(LlmRequest) throws LlmException`.
- Used for production dependency injection and extensively replaced with lambdas/scripted clients in tests.
- Its exact method parameter, return, and checked-exception types are part of the source contract.

### `OpenAiCompatibleClient`

- Public `final` implementation; public constructor accepts `AgentConfig`.
- Package-private injectable constructor accepts `(AgentConfig, Gson, HttpClient)` and is exercised by tests in the same `org.saturn.app.agent.llm` package.
- Builds `/v1/chat/completions` requests, sets model/stream/max tokens/template/cache/tools/response format fields, adds optional Bearer auth, retries transient HTTP statuses, handles timeout/IO/interruption, and applies bounded exponential backoff.
- Parses content, tool calls, and finish reason; recognizes structured-output rejection and raises `UnsupportedResponseFormatException`.
- Moving it changes package-private test access even if the public constructor is preserved.

### `LlmRequest`

- Public record with canonical components `(List<LlmMessage>, List<JsonObject>, boolean bypassPromptCache, JsonObject responseFormat)`.
- Two convenience constructors and `withoutPromptCache(...)` factory are contract surface.
- Compact constructor performs shallow `List.copyOf` on messages/tools; JSON objects are not deep-copied.
- Used as the principal request seam for client mocks and request assertions.

### `LlmResponse`

- Public record `(String content, List<LlmToolCall> toolCalls, String finishReason)`.
- Normalizes null content to `""` and shallow-copies tool calls with `List.copyOf`.
- Finish-reason preservation is observable and covered by provider tests/documentation.

### `LlmMessage`

- Public record `(String role, String content, List<LlmToolCall> toolCalls, String toolCallId)`.
- Static factories `system`, `user`, `assistant`, and `tool` encode protocol roles and null/content/tool-call conventions.
- Forms the persisted/reloaded conversation-memory type and is used in request assembly, sanitization, freshness policies, and response correction.
- The canonical constructor itself remains publicly callable, including null content cases.

### `LlmToolCall`

- Public record `(String id, String name, String arguments)`.
- Shared by provider parsing, response policy validation, scheduling, execution, rendering, and tests.
- Its record equality and exact component types are relied on by collection assertions and tool orchestration.

### `LlmException`

- Public checked exception with message-only and message-plus-cause constructors.
- Propagates through router/policy interfaces and is explicitly instantiated by tests and scripted clients.

### `UnsupportedResponseFormatException`

- Public final checked subtype of `LlmException`, message-only constructor.
- Used by `OpenAiCompatibleClient` and caught by `AgentResponseCorrector` to trigger the unstructured fallback path.

## All affected production files

The following production files contain definitions or references to at least one of the eight types:

```text
src/main/java/org/saturn/app/agent/AgentCommandChannelPolicy.java
src/main/java/org/saturn/app/agent/AgentCommandProseGuard.java
src/main/java/org/saturn/app/agent/AgentFreshDataCoordinator.java
src/main/java/org/saturn/app/agent/AgentFreshDataFinalValidator.java
src/main/java/org/saturn/app/agent/AgentFreshDataPolicy.java
src/main/java/org/saturn/app/agent/AgentFreshnessPolicy.java
src/main/java/org/saturn/app/agent/AgentMemoryStore.java
src/main/java/org/saturn/app/agent/AgentMessageHistory.java
src/main/java/org/saturn/app/agent/AgentModelVisibleToolResultRenderer.java
src/main/java/org/saturn/app/agent/AgentPreparedRequest.java
src/main/java/org/saturn/app/agent/AgentRequestAssembler.java
src/main/java/org/saturn/app/agent/AgentResponseCorrector.java
src/main/java/org/saturn/app/agent/AgentResponseFinalizer.java
src/main/java/org/saturn/app/agent/AgentResponseSanitizer.java
src/main/java/org/saturn/app/agent/AgentRouterFactory.java
src/main/java/org/saturn/app/agent/AgentScheduledToolCall.java
src/main/java/org/saturn/app/agent/AgentToolCallScheduler.java
src/main/java/org/saturn/app/agent/AgentToolCallValidator.java
src/main/java/org/saturn/app/agent/AgentToolExecutor.java
src/main/java/org/saturn/app/agent/AgentToolResultCoordinator.java
src/main/java/org/saturn/app/agent/AgentTurnMemory.java
src/main/java/org/saturn/app/agent/AgentTurnPolicy.java
src/main/java/org/saturn/app/agent/AgentTurnPolicyChain.java
src/main/java/org/saturn/app/agent/AgentTurnPolicyInput.java
src/main/java/org/saturn/app/agent/AgentUnverifiedActionPolicy.java
src/main/java/org/saturn/app/agent/DefaultAgentRouter.java
src/main/java/org/saturn/app/agent/ValidatedToolCall.java
src/main/java/org/saturn/app/agent/llm/LlmClient.java
src/main/java/org/saturn/app/agent/llm/LlmException.java
src/main/java/org/saturn/app/agent/llm/LlmMessage.java
src/main/java/org/saturn/app/agent/llm/LlmRequest.java
src/main/java/org/saturn/app/agent/llm/LlmResponse.java
src/main/java/org/saturn/app/agent/llm/LlmToolCall.java
src/main/java/org/saturn/app/agent/llm/OpenAiCompatibleClient.java
src/main/java/org/saturn/app/agent/llm/UnsupportedResponseFormatException.java
src/main/java/org/saturn/app/agent/persistence/H2AgentMemoryStore.java
```

Production usage clusters:

- **Client seam and request/response correction:** `AgentCommandChannelPolicy`, `AgentFreshDataCoordinator`, `AgentResponseCorrector`, `DefaultAgentRouter`.
- **Response policies and lifecycle:** `AgentFreshDataFinalValidator`, `AgentFreshDataPolicy`, `AgentResponseFinalizer`, `AgentTurnPolicy`, `AgentTurnPolicyChain`, `AgentTurnPolicyInput`, `AgentUnverifiedActionPolicy`.
- **Message/history/memory:** `AgentFreshnessPolicy`, `AgentMemoryStore`, `AgentMessageHistory`, `AgentPreparedRequest`, `AgentRequestAssembler`, `AgentResponseSanitizer`, `AgentTurnMemory`, `H2AgentMemoryStore`.
- **Tool-call domain:** `AgentCommandProseGuard`, `AgentModelVisibleToolResultRenderer`, `AgentScheduledToolCall`, `AgentToolCallScheduler`, `AgentToolCallValidator`, `AgentToolExecutor`, `AgentToolResultCoordinator`, `ValidatedToolCall`.
- **Composition/transport:** `AgentRouterFactory`, `OpenAiCompatibleClient`.

## All affected test files

```text
src/test/java/org/saturn/app/agent/AgentCommandChannelPolicyTest.java
src/test/java/org/saturn/app/agent/AgentCommandProseGuardTest.java
src/test/java/org/saturn/app/agent/AgentFreshDataCoordinatorTest.java
src/test/java/org/saturn/app/agent/AgentFreshDataFinalValidatorTest.java
src/test/java/org/saturn/app/agent/AgentFreshDataPolicyCorrectionTest.java
src/test/java/org/saturn/app/agent/AgentFreshDataPolicyTest.java
src/test/java/org/saturn/app/agent/AgentFreshDataTurnPolicyTest.java
src/test/java/org/saturn/app/agent/AgentFreshnessPolicyTest.java
src/test/java/org/saturn/app/agent/AgentMessageHistoryTest.java
src/test/java/org/saturn/app/agent/AgentModelVisibleToolResultRendererTest.java
src/test/java/org/saturn/app/agent/AgentPreparedRequestTest.java
src/test/java/org/saturn/app/agent/AgentRequestAssemblerTest.java
src/test/java/org/saturn/app/agent/AgentResponseCorrectorTest.java
src/test/java/org/saturn/app/agent/AgentResponseFinalizerTest.java
src/test/java/org/saturn/app/agent/AgentResponseSanitizerTest.java
src/test/java/org/saturn/app/agent/AgentToolCallSchedulerTest.java
src/test/java/org/saturn/app/agent/AgentToolCallValidatorTest.java
src/test/java/org/saturn/app/agent/AgentToolExecutorTest.java
src/test/java/org/saturn/app/agent/AgentToolResultCoordinatorTest.java
src/test/java/org/saturn/app/agent/AgentTurnMemoryTest.java
src/test/java/org/saturn/app/agent/AgentTurnPolicyChainTest.java
src/test/java/org/saturn/app/agent/AgentUnverifiedActionPolicyTest.java
src/test/java/org/saturn/app/agent/DefaultAgentRouterTest.java
src/test/java/org/saturn/app/agent/llm/OpenAiCompatibleClientConstructionTest.java
src/test/java/org/saturn/app/agent/llm/OpenAiCompatibleClientTest.java
```

The two provider tests are deliberately in the old `org.saturn.app.agent.llm` package and directly exercise the package-private HTTP-client constructor and private implementation details via reflection. They must be treated as migration-sensitive tests, not merely import updates.

## Recommended target structure

### Lowest-risk structure (recommended for the requested compatibility constraint)

Keep the eight existing public contract FQNs stable for at least one migration/deprecation window, and separate only implementation:

```text
org.saturn.app.agent.llm                         stable public compatibility/API package
├── LlmClient
├── LlmRequest
├── LlmResponse
├── LlmMessage
├── LlmToolCall
├── LlmException
└── UnsupportedResponseFormatException

org.saturn.app.agent.llm.provider.openai           implementation package
└── OpenAiCompatibleClient
```

`AgentRouterFactory` becomes the sole production composition point for the provider implementation. This yields interface/value-object versus implementation separation without changing the type identity of any value crossing the agent boundary. If desired, the stable package can later be renamed/documented as `llm.api` only in a major/breaking release.

### Clean-break target (only if public FQNs may change)

For a deliberate major-version migration, the coherent modular layout is:

```text
org.saturn.app.agent.llm.api
├── LlmClient
├── LlmRequest
├── LlmResponse
├── LlmMessage
├── LlmToolCall
└── exceptions/
    ├── LlmException
    └── UnsupportedResponseFormatException

org.saturn.app.agent.llm.infrastructure.openai
└── OpenAiCompatibleClient
```

The records and exception hierarchy should move together because their signatures are mutually recursive. Splitting records into separate `request`, `response`, and `message` packages would increase import churn and create no meaningful dependency boundary; the minimal domain unit is the complete protocol model set.

## Compatibility and migration hazards

1. **Source compatibility:** every direct `org.saturn.app.agent.llm.*` import listed above breaks after relocation. Tests also use fully qualified names and method references.
2. **Binary compatibility:** compiled clients refer to old JVM internal names; moving classes removes those names even if APIs are textually identical.
3. **Records cannot be facaded by inheritance:** Java records are final. An old `LlmMessage` cannot extend a new `LlmMessage`, and vice versa.
4. **Generic invariance:** `List<new.LlmMessage>` is not assignable to `List<old.LlmMessage>`. Constructors, record accessors, `AgentMemoryStore`, and persistence APIs would need explicit conversion at every boundary.
5. **Functional interface signatures:** changing `LlmClient` parameter/return types breaks lambdas, Mockito stubs, and method references. A facade interface with the same method name is not enough when request/response types differ.
6. **Exception catches:** moving or duplicating `LlmException` changes checked-exception declarations and catch matching. `UnsupportedResponseFormatException` must remain catchable as the same subtype along the correction fallback path.
7. **Constructor behavior:** preserving only the public OpenAI constructor is insufficient; same-package tests and possible downstream package peers rely on the package-private `(AgentConfig, Gson, HttpClient)` seam.
8. **Record semantics:** preserve canonical constructors, convenience constructors, static factories, null normalization, shallow immutability (`List.copyOf`), component order, and equality/hash behavior. Do not “improve” deep-copy behavior during relocation.
9. **Persistence boundary:** H2 memory stores `LlmMessage` values and reconstructs them. Introducing new message types without a conversion boundary risks changing the public `AgentMemoryStore` contract and persistence tests.
10. **Reflection and serialization:** provider tests reflectively access `backoff`; external code may inspect class names or serialize record component names. Keep implementation behavior and JSON field spelling unchanged.
11. **Package-private collaborators:** moving the client means updating test package placement or adding a deliberate test seam; do not widen the injectable constructor casually.
12. **Documentation/API drift:** `AGENTIC_ARCHITECTURE.md`, `agent/package-info.java`, BUG_HUNT references, and plans mention the current package and should be updated in the implementation phase, but are not production/test edits for this phase.
13. **Spotless/test ordering:** imports will change across many files; the repository's Spotless plugin is configured to apply Google Java Format during Maven builds. The refactor needs focused tests first, then `./mvnw spotless:check`, `./mvnw test`, and `./mvnw package`.

## Staged migration recommendation

1. **Stage 1 — establish the safe boundary:** retain the current seven public API/value/exception classes in `agent.llm`; move `OpenAiCompatibleClient` to `agent.llm.provider.openai` and update only composition plus provider test package/seam deliberately. Add focused architecture/contract tests before broad import changes.
2. **Stage 2 — optional new API namespace:** if a new `agent.llm.api` namespace is required, introduce explicit conversion methods/constructors and migrate internal consumers incrementally. Keep old classes as deprecated compatibility types; do not pretend they are aliases.
3. **Stage 3 — major-version removal:** remove old types only after all downstream consumers are migrated and the project explicitly accepts source/binary incompatibility. This is the only stage where all eight types can be physically relocated cleanly.

**Conclusion:** compatibility facades are required only if the project insists on new public FQNs while retaining old ones; Java cannot provide transparent facades for this mutually recursive record graph. Under the stated “preserve public API contracts” requirement, a staged migration is required, and the minimal immediate modular refactor is to keep the stable contract package and move the concrete provider implementation behind a subpackage.

## Verification status

- Repository, guidance, package layout, definitions, imports, source references, tests, architecture documentation, and dirty-tree status were inspected.
- No production files or test files were modified.
- This phase intentionally did not run a refactor test/build because no implementation change was made.
- Existing unrelated/untracked files were preserved, including `.hermes/`, credentials/config/runtime artifacts, IDE files, and database/log files.
