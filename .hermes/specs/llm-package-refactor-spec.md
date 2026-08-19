# LLM Package Refactor — Phase 1 Architecture Specification

**Status:** Architecture only; no production or test source changes in this phase
**Repository:** `/Users/ab/workspace/projects/saturn`
**Compatibility baseline:** Java 23, current public package `org.saturn.app.agent.llm`

## 1. Decision

The first implementation slice **must move only the concrete provider implementation while retaining the existing public contract types**. A full package migration is not justified under the current requirement to preserve behavior and public API contracts.

The immediate target is:

```text
org.saturn.app.agent.llm
├── LlmClient                         stable public functional contract
├── LlmRequest                        stable public record
├── LlmResponse                       stable public record
├── LlmMessage                        stable public record
├── LlmToolCall                       stable public record
├── LlmException                      stable checked exception
├── UnsupportedResponseFormatException stable checked subtype
└── OpenAiCompatibleClient             deprecated compatibility facade (temporary)

org.saturn.app.agent.llm.provider.openai
└── OpenAiCompatibleClient              canonical OpenAI-compatible implementation
```

`AgentRouterFactory` becomes the only production composition point that names the provider implementation. All agent orchestration, policy, memory, persistence, and test seams continue to exchange the original `LlmClient`, `LlmRequest`, `LlmResponse`, `LlmMessage`, `LlmToolCall`, and exception types.

The old `OpenAiCompatibleClient` FQN remains as a delegating facade during the migration window. This is necessary because it is public and therefore part of the source/binary surface, even though the value records and `LlmClient` are the higher-risk contracts. The facade cannot extend the new implementation because the implementation is intentionally final; delegation is the compatibility mechanism.

## 2. Why a full relocation is unsafe in Java

The seven provider-neutral types form one mutually recursive protocol model:

- `LlmClient.complete` has exact parameter, return, and checked-exception types.
- `LlmRequest` contains `List<LlmMessage>`.
- `LlmMessage` and `LlmResponse` contain `List<LlmToolCall>`.
- `LlmException` and `UnsupportedResponseFormatException` participate in checked throws/catch flow.

The records are final by definition and cannot be subclassed into aliases. `List<new.LlmMessage>` is not assignable to `List<old.LlmMessage>`, so facade records would require conversion at every boundary and would alter record equality/type identity. A second `LlmClient` with apparently identical method names would also break lambdas, method references, mocks, and checked exception declarations. Directly changing all imports is therefore a deliberate source and binary break, not a refactor with transparent compatibility.

The provider class has an additional seam: public `(AgentConfig)` construction and package-private `(AgentConfig, Gson, HttpClient)` construction used by same-package tests. Moving the class without a test-seam decision would break those tests even if the public constructor remained unchanged.

## 3. Exact interface/implementation boundaries

### Stable contract package: `org.saturn.app.agent.llm`

Keep these types physically and nominally unchanged in Stage 1:

- `LlmClient`: the sole provider-neutral functional interface; one abstract method, `LlmResponse complete(LlmRequest) throws LlmException`.
- `LlmRequest`: request value record, including canonical component order, convenience constructors, `withoutPromptCache`, shallow `List.copyOf`, and response-format semantics.
- `LlmResponse`: response value record, including null-content normalization, shallow tool-call copying, finish-reason preservation, equality, and hash behavior.
- `LlmMessage`: conversation value record and its `system`, `user`, `assistant`, and `tool` factories.
- `LlmToolCall`: tool-call value record with unchanged component names/types and record semantics.
- `LlmException`: checked provider failure boundary and both existing constructors.
- `UnsupportedResponseFormatException`: final checked subtype used by the structured-output fallback.

These types are the domain/API boundary. They must not import provider-specific HTTP, Gson transport policy, or OpenAI naming.

### Provider implementation package: `org.saturn.app.agent.llm.provider.openai`

Create the canonical `OpenAiCompatibleClient` implementation here. It implements the stable `org.saturn.app.agent.llm.LlmClient` and imports all stable request/response/exception types explicitly. It owns:

- `java.net.http.HttpClient` request/response transport;
- Gson serialization and parsing;
- `/v1/chat/completions` URI/request construction;
- bearer authentication, model, stream, token, template, cache, tools, and response-format fields;
- transient retry and bounded backoff;
- timeout, I/O, interruption, HTTP failure, and structured-format rejection behavior.

It must not move or duplicate protocol records. It must remain `final`.

### Compatibility facade: `org.saturn.app.agent.llm.OpenAiCompatibleClient`

Retain the old class name temporarily as a `final` delegating facade implementing the old `LlmClient`. Preserve:

- public `OpenAiCompatibleClient(AgentConfig)`;
- package-private `(AgentConfig, Gson, HttpClient)` constructor for the existing same-package tests;
- `complete(LlmRequest) throws LlmException` behavior and exception identity.

The facade delegates to `org.saturn.app.agent.llm.provider.openai.OpenAiCompatibleClient`, with no translation of stable contract values. To keep the existing package-private injection seam without making the old test constructor public, the new implementation may expose its dependency-injected constructor as a public constructor on the new implementation class; this is a new implementation API, not a weakening of the old API. The old facade constructor remains package-private. Mark the facade deprecated in the implementation stage and document its removal only for a future major release.

The alternative—leaving the old class as the implementation and adding a new wrapper—does not achieve the requested implementation separation. The alternative of deleting the old class immediately is source/binary breaking and is rejected.

## 4. Complete original-to-target mapping

### Stage 1 target (behavior-preserving)

| Original FQN | Stage 1 target FQN | Action |
|---|---|---|
| `org.saturn.app.agent.llm.LlmClient` | `org.saturn.app.agent.llm.LlmClient` | Retain unchanged; stable functional seam |
| `org.saturn.app.agent.llm.LlmRequest` | `org.saturn.app.agent.llm.LlmRequest` | Retain unchanged; stable record |
| `org.saturn.app.agent.llm.LlmResponse` | `org.saturn.app.agent.llm.LlmResponse` | Retain unchanged; stable record |
| `org.saturn.app.agent.llm.LlmMessage` | `org.saturn.app.agent.llm.LlmMessage` | Retain unchanged; stable record |
| `org.saturn.app.agent.llm.LlmToolCall` | `org.saturn.app.agent.llm.LlmToolCall` | Retain unchanged; stable record |
| `org.saturn.app.agent.llm.LlmException` | `org.saturn.app.agent.llm.LlmException` | Retain unchanged; stable checked exception |
| `org.saturn.app.agent.llm.UnsupportedResponseFormatException` | `org.saturn.app.agent.llm.UnsupportedResponseFormatException` | Retain unchanged; stable subtype/catch contract |
| `org.saturn.app.agent.llm.OpenAiCompatibleClient` | `org.saturn.app.agent.llm.OpenAiCompatibleClient` (facade) + `org.saturn.app.agent.llm.provider.openai.OpenAiCompatibleClient` (implementation) | Delegate old FQN to new implementation |

No Stage 1 import of the seven contract types changes. The only production import migration is in `src/main/java/org/saturn/app/agent/AgentRouterFactory.java`:

```java
// remove
import org.saturn.app.agent.llm.OpenAiCompatibleClient;

// add
import org.saturn.app.agent.llm.provider.openai.OpenAiCompatibleClient;
```

The provider implementation imports the stable contracts explicitly:

```java
import org.saturn.app.agent.llm.LlmClient;
import org.saturn.app.agent.llm.LlmException;
import org.saturn.app.agent.llm.LlmMessage;
import org.saturn.app.agent.llm.LlmRequest;
import org.saturn.app.agent.llm.LlmResponse;
import org.saturn.app.agent.llm.UnsupportedResponseFormatException;
```

The old-package provider tests may remain in `package org.saturn.app.agent.llm` for the facade constructor in the first slice. When tests are migrated to exercise the canonical implementation, change their package declaration to `org.saturn.app.agent.llm.provider.openai` and update only the implementation import/reference; do not change their stable contract imports.

### Optional Stage 2 clean API namespace (only with an explicit migration decision)

If the project later requires a domain/API namespace, use this coherent mapping and migrate all consumers in one controlled source migration. Do not split the mutually recursive records into separate request/message/response packages.

| Original FQN | Clean-break target FQN |
|---|---|
| `org.saturn.app.agent.llm.LlmClient` | `org.saturn.app.agent.llm.api.LlmClient` |
| `org.saturn.app.agent.llm.LlmRequest` | `org.saturn.app.agent.llm.api.LlmRequest` |
| `org.saturn.app.agent.llm.LlmResponse` | `org.saturn.app.agent.llm.api.LlmResponse` |
| `org.saturn.app.agent.llm.LlmMessage` | `org.saturn.app.agent.llm.api.LlmMessage` |
| `org.saturn.app.agent.llm.LlmToolCall` | `org.saturn.app.agent.llm.api.LlmToolCall` |
| `org.saturn.app.agent.llm.LlmException` | `org.saturn.app.agent.llm.api.exceptions.LlmException` |
| `org.saturn.app.agent.llm.UnsupportedResponseFormatException` | `org.saturn.app.agent.llm.api.exceptions.UnsupportedResponseFormatException` |
| `org.saturn.app.agent.llm.OpenAiCompatibleClient` | `org.saturn.app.agent.llm.infrastructure.openai.OpenAiCompatibleClient` |

The clean-break package split is deliberately deferred. If undertaken, all seven contract types must migrate together, followed by all affected production and test imports, persistence signatures, lambda/scripted clients, catch clauses, and same-package provider tests. Old types can only be retained as explicitly converting adapters, never as Java aliases.

## 5. Affected import migration inventory

### Stage 1

- `AgentRouterFactory`: migrate only `OpenAiCompatibleClient` to `org.saturn.app.agent.llm.provider.openai.OpenAiCompatibleClient`.
- All other 35 production consumer files listed by the forensic report retain their existing `org.saturn.app.agent.llm.*` imports. This includes `DefaultAgentRouter`, `AgentResponseCorrector`, `AgentCommandChannelPolicy`, `AgentFreshDataCoordinator`, all response/freshness/tool policies, `AgentMemoryStore`, `AgentTurnMemory`, `AgentRequestAssembler`, and `H2AgentMemoryStore`.
- The 25 affected tests retain stable contract imports. The two provider tests remain old-package tests for the facade initially; if moved, their package declaration and implementation reference change together.
- No imports in orchestration, persistence, or policy code should point directly to the provider implementation.

### Future full migration

The import replacement is systematic but must be done only after the Stage 2 types exist:

```text
org.saturn.app.agent.llm.LlmClient                       -> org.saturn.app.agent.llm.api.LlmClient
org.saturn.app.agent.llm.LlmRequest                      -> org.saturn.app.agent.llm.api.LlmRequest
org.saturn.app.agent.llm.LlmResponse                     -> org.saturn.app.agent.llm.api.LlmResponse
org.saturn.app.agent.llm.LlmMessage                      -> org.saturn.app.agent.llm.api.LlmMessage
org.saturn.app.agent.llm.LlmToolCall                     -> org.saturn.app.agent.llm.api.LlmToolCall
org.saturn.app.agent.llm.LlmException                    -> org.saturn.app.agent.llm.api.exceptions.LlmException
org.saturn.app.agent.llm.UnsupportedResponseFormatException -> org.saturn.app.agent.llm.api.exceptions.UnsupportedResponseFormatException
org.saturn.app.agent.llm.OpenAiCompatibleClient          -> org.saturn.app.agent.llm.infrastructure.openai.OpenAiCompatibleClient
```

`AgentResponseCorrector` is high priority because it catches `UnsupportedResponseFormatException`; its catch and throws imports must migrate atomically with the exception hierarchy. `DefaultAgentRouter`, `AgentCommandChannelPolicy`, and `AgentFreshDataCoordinator` are high priority because they define the `LlmClient` lambda/injection seam and repeatedly construct requests. `AgentMemoryStore`, `AgentTurnMemory`, and `H2AgentMemoryStore` are high priority because persistence stores `LlmMessage` values. `AgentRouterFactory` is the sole Stage 1 composition edit.

## 6. Staged implementation plan

1. **Contract freeze:** add/confirm focused tests for record constructors/factories, null normalization, list copying, exception subtype catching, `LlmClient` lambda compatibility, and provider JSON/retry behavior. Do not alter contract source.
2. **Extract implementation:** copy the current provider implementation into `org.saturn.app.agent.llm.provider.openai.OpenAiCompatibleClient`, changing only package/imports and making the new injection constructor accessible to the facade/test arrangement.
3. **Add old-FQN facade:** retain constructor signatures and delegate with the same stable contract types. Mark deprecated with a removal note tied to a future major release.
4. **Switch composition:** update only `AgentRouterFactory` to instantiate the canonical provider package.
5. **Migrate provider tests deliberately:** keep old-package facade coverage, and add/move canonical implementation tests to the provider package so the injectable constructor and reflection assumptions remain explicit.
6. **Verify behavior:** run focused provider/router/corrector tests, then `./mvnw spotless:check`, `./mvnw test`, and `./mvnw package`. Confirm no production/test imports of stable contract types changed in Stage 1 except the provider implementation import in `AgentRouterFactory`.
7. **Deprecation window:** document the old provider FQN and migration target. Do not remove the facade until a major-version decision accepts source and binary incompatibility.

## 7. Non-negotiable parity and review checks

- Preserve all record component names/order, constructors, factories, list-copy behavior, null normalization, equality/hash behavior, and JSON field spelling.
- Preserve `LlmException` checked declarations and `UnsupportedResponseFormatException` catchability in `AgentResponseCorrector`.
- Preserve timeout, interruption re-interruption, retry status classification, backoff bounds, auth behavior, response parsing, finish reasons, tool calls, and structured-output fallback.
- Preserve the public provider constructor and old package-private injectable constructor on the facade.
- Do not introduce provider-specific types into `AgentRouter`, policies, memory, or persistence.
- Do not modify unrelated dirty files, configuration, databases, logs, IDE files, or `.hermes` artifacts other than this specification.

## 8. Risks and explicit non-goals

The temporary facade adds one class and a delegation hop, but avoids the much larger and semantically risky conversion graph. It does not make a future full API namespace migration free; that migration remains a planned source/binary break. The clean-break mapping is documented for consistency, not approved for Stage 1 execution.

This specification does not modify production code or tests and does not claim a refactor build has passed. Verification belongs to the implementation phase.
