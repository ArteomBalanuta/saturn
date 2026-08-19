# Full Agent Package Refactor — Final Cleanup QA

Date: 2026-08-19
Branch: `develop`

## Scope

Final structural/API cleanup after the full agent-package migration. The obsolete
`org.saturn.app.agent.llm.OpenAiCompatibleClient` facade and its compatibility test
were removed; provider callers/tests now use the canonical
`org.saturn.app.agent.llm.provider.openai.OpenAiCompatibleClient`.

## Structural results

- Direct root production declarations: **0**; `src/main/java/org/saturn/app/agent`
  contains only `package-info.java`.
- Expected migrated declarations: **83**.
- Actual migrated top-level declarations: **83**, exactly once each, with counts:
  - `api`: 21/21
  - `config`: 4/4
  - `routing`: 18/18
  - `turn`: 15/15
  - `room`: 7/7
  - `tool.contract`: 4/4
  - `tool.execution`: 14/14
- Duplicate migrated declaration names: **0**.
- Canonical OpenAI client declarations: **1** at
  `agent.llm.provider.openai.OpenAiCompatibleClient`.
- Old facade FQN references in `src/main` and `src/test`: **0**.
- Wildcard imports in task-owned agent Java sources/tests: **0**.
- `agent/package-info.java` links to `agent.routing.AgentRuntimeFactory` and
  documents the final `api`, `config`, `routing`, `turn`, `room`, `tool.contract`,
  `tool.execution`, `llm`, and `llm.provider.openai` namespaces.
- Migration-only visibility review: cross-package visibility retained only where
  required by callers in `turn`/`tool.execution` or the routing composition API;
  no additional unnecessary narrowing was identified without changing the public
  migration contract.

## Verification

| Check | Result |
|---|---|
| Focused LLM/provider + routing tests | **PASS** — 158 run, 0 failures, 0 errors, 5 skipped |
| Maven clean compile (via focused test lifecycle) | **PASS** — 300 main sources, 134 test sources compiled |
| `./mvnw spotless:check` | **PASS** |
| `git diff --check` | **PASS** |
| Full `./mvnw test` | **PASS** — 599 run, 0 failures, 0 errors, 5 skipped |
| `./mvnw package` | **PASS** — tests 599/0/0/5; shaded JAR produced |

Package output: `target/saturn.jar` (shaded replacement completed successfully).
Maven emitted normal shade-plugin duplicate-resource/module-info warnings only; no
build or test errors.

## Cleanup paths

- Deleted `src/main/java/org/saturn/app/agent/llm/OpenAiCompatibleClient.java`.
- Deleted `src/test/java/org/saturn/app/agent/llm/OpenAiCompatibleClientCompatibilityTest.java`.
- Moved provider tests to:
  - `src/test/java/org/saturn/app/agent/llm/provider/openai/OpenAiCompatibleClientTest.java`
  - `src/test/java/org/saturn/app/agent/llm/provider/openai/OpenAiCompatibleClientConstructionTest.java`
- Updated the moved tests to the canonical provider package and contract imports.
- Replaced wildcard API imports with explicit imports in the four production routing
  files and the 16 migrated agent tests that contained them.
- Updated `src/main/java/org/saturn/app/agent/package-info.java`.

The repository had pre-existing migration changes and unrelated untracked local
artifacts; none were reset, cleaned, committed, or pushed.
