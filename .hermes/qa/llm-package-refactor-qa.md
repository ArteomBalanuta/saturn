# Stage 1 LLM Package Refactor — Phase 3 QA

Date: 2026-08-19
Repository: `/Users/ab/workspace/projects/saturn`

## Overall result

**PASS.** The Stage 1 package refactor passed focused behavior tests, formatting, whitespace validation, the full test suite, and package assembly. No refactor-related source or test fixes were required during this QA pass.

## Exact verification statuses

| Check | Command | Status |
|---|---|---|
| Focused provider/facade/router/corrector tests | `./mvnw -q -Dtest=OpenAiCompatibleClientTest,OpenAiCompatibleClientConstructionTest,OpenAiCompatibleClientCompatibilityTest,AgentRouterFactoryTest,AgentResponseCorrectorTest test` | **PASS** (exit 0) |
| Formatting | `./mvnw -q spotless:check` | **PASS** (exit 0) |
| Patch whitespace | `git diff --check` | **PASS** (exit 0) |
| Full test suite | `./mvnw -q test` | **PASS** (exit 0) |
| Package build | `./mvnw -q package` | **PASS** (exit 0) |

The Maven commands emitted expected application/test logging but no failures. No files were changed by the verification commands.

## Contract and behavior audit

- **Canonical provider:** `org.saturn.app.agent.llm.provider.openai.OpenAiCompatibleClient` is `final`, implements the stable `org.saturn.app.agent.llm.LlmClient`, explicitly imports the stable request/response/message/tool/exception contracts, and owns the original HTTP/Gson implementation.
- **Old facade:** `org.saturn.app.agent.llm.OpenAiCompatibleClient` remains `final`, implements the old `LlmClient`, is annotated `@Deprecated(forRemoval = false)`, retains the public `(AgentConfig)` constructor, and retains the package-private `(AgentConfig, Gson, HttpClient)` constructor.
- **Delegation:** The facade delegates `complete(LlmRequest) throws LlmException` directly to the canonical implementation without stable-contract value translation. Reflection coverage confirmed the delegate class, return type, and checked exception type.
- **Constructor behavior:** Null dependency checks remain enforced. The canonical injected constructor is public as permitted by the spec; the facade injected constructor remains package-private.
- **JSON behavior:** Existing provider tests passed for model omission, authorization, stream/token/template fields, prompt-cache bypass, structured response format, tool definitions, tool calls, tool messages, null content, and finish-reason handling.
- **Retry behavior:** Existing provider tests passed transient 503 and 429 retry, non-transient client-error no-retry, exhausted transient failure, transport-IOException retry, and bounded/overflow-safe backoff.
- **Timeout/interruption behavior:** Existing provider tests passed no-retry timeout handling with `HttpTimeoutException` cause and timeout message; transport interruption and retry-backoff interruption both preserve the interrupt flag and produce the expected `LlmException` messages.
- **Exception behavior:** Malformed successful responses remain translated to `LlmException`; structured-response rejection remains `UnsupportedResponseFormatException`; the facade preserves the stable checked exception surface. `AgentResponseCorrectorTest` passed, including structured-output fallback/catch behavior.
- **Composition:** `AgentRouterFactory` is the only production composition point changed and now instantiates the canonical provider. The router reflection test confirmed the runtime client class is the canonical FQN.
- **Implementation parity:** Comparing the canonical source to the original `HEAD` provider after normalizing the package showed only the spec-required explicit stable-contract imports and the visibility change for the dependency-injected constructor.

## Complete original → target mapping

| Original FQN | Stage 1 target FQN | Action |
|---|---|---|
| `org.saturn.app.agent.llm.LlmClient` | `org.saturn.app.agent.llm.LlmClient` | Retain unchanged; stable functional seam |
| `org.saturn.app.agent.llm.LlmRequest` | `org.saturn.app.agent.llm.LlmRequest` | Retain unchanged; stable record |
| `org.saturn.app.agent.llm.LlmResponse` | `org.saturn.app.agent.llm.LlmResponse` | Retain unchanged; stable record |
| `org.saturn.app.agent.llm.LlmMessage` | `org.saturn.app.agent.llm.LlmMessage` | Retain unchanged; stable record |
| `org.saturn.app.agent.llm.LlmToolCall` | `org.saturn.app.agent.llm.LlmToolCall` | Retain unchanged; stable record |
| `org.saturn.app.agent.llm.LlmException` | `org.saturn.app.agent.llm.LlmException` | Retain unchanged; stable checked exception |
| `org.saturn.app.agent.llm.UnsupportedResponseFormatException` | `org.saturn.app.agent.llm.UnsupportedResponseFormatException` | Retain unchanged; stable subtype/catch contract |
| `org.saturn.app.agent.llm.OpenAiCompatibleClient` | `org.saturn.app.agent.llm.OpenAiCompatibleClient` (facade) + `org.saturn.app.agent.llm.provider.openai.OpenAiCompatibleClient` (canonical implementation) | Delegate old FQN to new implementation |

Deferred clean-break mapping (not performed in Stage 1):

| Original FQN | Future Stage 2 target FQN |
|---|---|
| `org.saturn.app.agent.llm.LlmClient` | `org.saturn.app.agent.llm.api.LlmClient` |
| `org.saturn.app.agent.llm.LlmRequest` | `org.saturn.app.agent.llm.api.LlmRequest` |
| `org.saturn.app.agent.llm.LlmResponse` | `org.saturn.app.agent.llm.api.LlmResponse` |
| `org.saturn.app.agent.llm.LlmMessage` | `org.saturn.app.agent.llm.api.LlmMessage` |
| `org.saturn.app.agent.llm.LlmToolCall` | `org.saturn.app.agent.llm.api.LlmToolCall` |
| `org.saturn.app.agent.llm.LlmException` | `org.saturn.app.agent.llm.api.exceptions.LlmException` |
| `org.saturn.app.agent.llm.UnsupportedResponseFormatException` | `org.saturn.app.agent.llm.api.exceptions.UnsupportedResponseFormatException` |
| `org.saturn.app.agent.llm.OpenAiCompatibleClient` | `org.saturn.app.agent.llm.infrastructure.openai.OpenAiCompatibleClient` |

## Task-owned touched files

- `.hermes/qa/llm-package-refactor-qa.md` (this QA report)
- `src/main/java/org/saturn/app/agent/AgentRouterFactory.java`
- `src/main/java/org/saturn/app/agent/llm/OpenAiCompatibleClient.java`
- `src/main/java/org/saturn/app/agent/llm/provider/openai/OpenAiCompatibleClient.java`
- `src/test/java/org/saturn/app/agent/AgentRouterFactoryTest.java`
- `src/test/java/org/saturn/app/agent/llm/OpenAiCompatibleClientCompatibilityTest.java`

Existing unrelated dirty files and runtime/config/IDE/database/log artifacts were preserved and not modified. No commit or push was performed.
