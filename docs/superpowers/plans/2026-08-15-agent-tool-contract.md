# Agent Tool Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a compatibility-first SDK descriptor contract so Saturn tools expose explicit labels, usage, authority, effects, examples, and result-delivery semantics to the agent.

**Architecture:** Keep `AgentTool` as the execution interface and add default descriptor metadata that each existing tool may override. `AgentToolRegistry` will compose contextual descriptors into provider function definitions, while `DefaultAgentRouter` will use descriptor result modes to prevent duplicated room-delivered output and preserve current execution guards.

**Tech Stack:** Java 23, Gson JSON schemas, JUnit 5, Maven, existing Saturn agent registry/router/tool abstractions.

## Global Constraints

- Preserve source compatibility for existing `AgentTool` implementations through default methods.
- Keep capability filtering in `AgentToolRegistry` as the authority for which tools are exposed.
- Do not redesign SQL authorization, moderation policy, prompt-cache handling, or database schema.
- Keep tool metadata immutable after construction.
- Never persist or publish a model response that claims an action a tool did not successfully perform.
- Use TDD for every production behavior change and run focused tests before broader verification.

---

### Task 1: Add Descriptor Value Types

**Files:**
- Create: `src/main/java/org/saturn/app/agent/AgentToolDescriptor.java`
- Create: `src/main/java/org/saturn/app/agent/ToolAccess.java`
- Create: `src/main/java/org/saturn/app/agent/ToolEffect.java`
- Create: `src/main/java/org/saturn/app/agent/ToolResultMode.java`
- Create: `src/main/java/org/saturn/app/agent/ToolExample.java`
- Test: `src/test/java/org/saturn/app/agent/AgentToolDescriptorTest.java`

**Interfaces:**
- Produces immutable descriptor types used by `AgentTool` and `AgentToolRegistry`.
- `ToolExample` stores a short purpose, command/tool name, and immutable string arguments JSON.
- `AgentToolDescriptor` stores `name`, `label`, `description`, `category`, `access`, `effect`, `resultMode`, contextual `parameters`, `whenToUse`, `whenNotToUse`, examples, required capabilities, and required successful tools.

- [ ] **Step 1: Write failing constructor-validation and immutability tests**

Test blank names, labels, descriptions, and categories; null enum values; non-object parameter schemas; and mutation attempts against lists and sets.

- [ ] **Step 2: Run the focused descriptor test and verify the expected failures**

Run: `./mvnw -Dtest=AgentToolDescriptorTest test`

Expected: compilation or assertion failures because the descriptor types do not yet exist.

- [ ] **Step 3: Implement immutable enums, example, and descriptor records**

Use defensive copies, `List.copyOf`, `Set.copyOf`, and `Objects.requireNonNull`. Validate JSON parameters with `isJsonObject()`.

- [ ] **Step 4: Run the focused descriptor test and verify it passes**

Run: `./mvnw -Dtest=AgentToolDescriptorTest test`

Expected: all descriptor validation and immutability tests pass.

### Task 2: Add Backward-Compatible Tool Metadata Defaults

**Files:**
- Modify: `src/main/java/org/saturn/app/agent/AgentTool.java`
- Test: `src/test/java/org/saturn/app/agent/AgentToolTest.java`

**Interfaces:**
- `AgentTool` gains `default AgentToolDescriptor descriptor(AgentContext context)`.
- Default metadata derives `name()`, `description()`, `parameters(context)`, availability, and `requiredSuccessfulTools()`.
- Defaults use category `general`, access `PUBLIC`, effect `READ_ONLY`, and result mode `MODEL_DATA` so existing tools remain valid until specialized.

- [ ] **Step 1: Write a failing test proving a legacy tool receives a complete descriptor**

Register a minimal anonymous `AgentTool`, call `descriptor(context)`, and assert that existing name, description, and contextual parameters are preserved while safe defaults are populated.

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./mvnw -Dtest=AgentToolTest test`

Expected: failure because `descriptor(context)` is not available.

- [ ] **Step 3: Add the default descriptor implementation**

Return a validated `AgentToolDescriptor` using the existing tool methods and immutable empty guidance/example/capability collections.

- [ ] **Step 4: Run the focused test and verify it passes**

Run: `./mvnw -Dtest=AgentToolTest test`

Expected: all legacy-default assertions pass.

### Task 3: Serialize the SDK Contract in the Registry

**Files:**
- Modify: `src/main/java/org/saturn/app/agent/AgentToolRegistry.java`
- Test: `src/test/java/org/saturn/app/agent/AgentToolRegistryTest.java`

**Interfaces:**
- `AgentToolRegistry.definitions(context)` continues returning OpenAI function definitions.
- Each function description includes a compact, deterministic `SATURN SDK CONTRACT` block generated from `tool.descriptor(context)`.
- The registry still filters unavailable tools before descriptor generation.

- [ ] **Step 1: Write failing serialization tests**

Create a tool with custom label, category, effect, result mode, usage rules, examples, and capability metadata. Assert the function definition preserves the JSON parameter schema and includes every contract field exactly once. Assert unavailable tools are absent.

- [ ] **Step 2: Run registry tests and verify the new assertions fail**

Run: `./mvnw -Dtest=AgentToolRegistryTest test`

Expected: function descriptions contain no SDK contract block.

- [ ] **Step 3: Implement deterministic contract rendering**

Add a private registry formatter that emits stable lines for label, category, access, effect, result mode, when-to-use, when-not-to-use, prerequisites, capabilities, and examples. Escape values through Gson rather than string concatenation that could create malformed JSON descriptions.

- [ ] **Step 4: Run registry tests and verify they pass**

Run: `./mvnw -Dtest=AgentToolRegistryTest test`

Expected: contextual definitions include the complete contract and retain capability-specific command enums.

### Task 4: Describe Existing Tools Explicitly

**Files:**
- Modify: `src/main/java/org/saturn/app/agent/tool/RunCommandTool.java`
- Modify: `src/main/java/org/saturn/app/agent/tool/RoomUsersTool.java`
- Modify: `src/main/java/org/saturn/app/agent/tool/UserMessageHistoryTool.java`
- Modify: `src/main/java/org/saturn/app/agent/tool/DatabaseSchemaTool.java`
- Modify: `src/main/java/org/saturn/app/agent/tool/DatabaseSqlTool.java`
- Modify: `src/main/java/org/saturn/app/agent/tool/DatabaseQueryTool.java`
- Test: `src/test/java/org/saturn/app/agent/tool/SaturnAgentToolsTest.java`

**Interfaces:**
- Each tool overrides `descriptor(context)` or a focused metadata hook while retaining its current execution code.
- `run_command` describes exactly-one-command behavior, approved command enums, room delivery, command examples, and hypothetical/quoted/future exclusions.
- Room and history tools declare `MODEL_DATA` and read-only effects.
- Database schema/query tools declare read-only model-data behavior and their dynamic-SQL prerequisites.

- [ ] **Step 1: Add failing metadata assertions for every existing SDK tool**

Assert names, labels, categories, effects, result modes, and non-empty usage guidance for the six tool families. Assert `run_command` reports `ROOM_MESSAGE` for informational-only callers, `MODERATION` when moderation commands are exposed, and `CREATOR_ONLY` when the permanent-ban capability is exposed.

- [ ] **Step 2: Run the focused tool tests and verify the assertions fail**

Run: `./mvnw -Dtest=SaturnAgentToolsTest test`

Expected: tools expose only generic defaults.

- [ ] **Step 3: Implement explicit descriptors**

Use contextual command availability already computed by each tool. Keep descriptions short enough for provider payloads and make result-delivery behavior unmistakable.

- [ ] **Step 4: Run the focused tool tests and verify they pass**

Run: `./mvnw -Dtest=SaturnAgentToolsTest test`

Expected: all tool metadata and existing execution tests pass.

### Task 5: Enforce Result Delivery Semantics in the Router

**Files:**
- Modify: `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java`
- Modify: `src/main/java/org/saturn/app/agent/AgentToolResult.java` only if a compatibility-safe result field is required
- Test: `src/test/java/org/saturn/app/agent/DefaultAgentRouterTest.java`

**Interfaces:**
- Router resolves the descriptor for each executed tool from the same contextual registry used for execution.
- `MODEL_DATA` results remain available for model synthesis.
- `ROOM_DELIVERY` results append a concise delivery acknowledgment instruction and reject duplicated command/payload prose.
- `ROOM_DELIVERY_AND_MODEL_DATA` permits summarization but still requires successful tool outcome.

- [ ] **Step 1: Write failing router tests for result modes**

Cover a room-delivery tool whose model tries to repeat the command, a model-data tool whose result must be summarized, and a failed room-delivery tool that must not produce a success claim.

- [ ] **Step 2: Run the focused router tests and verify failures**

Run: `./mvnw -Dtest=DefaultAgentRouterTest test`

Expected: the router currently treats all tool results as indistinguishable text.

- [ ] **Step 3: Implement descriptor-aware post-tool instructions and validation**

Keep the existing command prose guard and stale-response handling. Add only the result-mode behavior required by the descriptor, without changing SQL or moderation authorization.

- [ ] **Step 4: Run the focused router tests and verify they pass**

Run: `./mvnw -Dtest=DefaultAgentRouterTest test`

Expected: room delivery is not duplicated and model-data results remain answerable.

### Task 6: Make the System Prompt SDK-Contract First

**Files:**
- Modify: `src/main/java/org/saturn/app/agent/AgentSystemPrompt.java`
- Modify: `src/main/resources/agent/vaelen-system-prompt.txt`
- Test: `src/test/java/org/saturn/app/agent/AgentSystemPromptTest.java`

**Interfaces:**
- The runtime policy explicitly treats serialized SDK descriptors as authoritative.
- Persona text remains secondary and cannot add tools, permissions, or result semantics.

- [ ] **Step 1: Write failing prompt assertions**

Assert the rendered policy includes descriptor authority, `whenToUse`/`whenNotToUse`, access/effect checks, result-mode handling, exact parameter adherence, no invented tools, and no duplicate room-delivered output.

- [ ] **Step 2: Run the prompt test and verify it fails**

Run: `./mvnw -Dtest=AgentSystemPromptTest test`

Expected: the current policy does not contain the full SDK-first contract rules.

- [ ] **Step 3: Update the runtime policy and persona guidance**

Put operational rules in `AgentSystemPrompt` before the persona. Keep the resource persona concise and reinforce that it cannot override SDK metadata.

- [ ] **Step 4: Run the prompt tests and verify they pass**

Run: `./mvnw -Dtest=AgentSystemPromptTest test`

Expected: all prompt rendering and existing anti-fluff assertions pass.

### Task 7: End-to-End Contract Verification and Documentation

**Files:**
- Modify: `docs/superpowers/specs/2026-08-15-agent-tool-contract-design.md`
- Modify: `README.md` if agent tooling documentation is present there
- Test: existing agent, router, registry, and tool test suites

**Interfaces:**
- No new runtime interfaces; this task verifies the complete descriptor-to-provider-to-router path.

- [ ] **Step 1: Run focused SDK contract tests**

Run: `./mvnw -Dtest=AgentToolDescriptorTest,AgentToolTest,AgentToolRegistryTest,SaturnAgentToolsTest,DefaultAgentRouterTest,AgentSystemPromptTest test`

Expected: all focused contract, routing, prompt, and tool tests pass.

- [ ] **Step 2: Run formatting and whitespace checks**

Run: `./mvnw spotless:check`

Expected: formatting check succeeds without changing unrelated files.

- [ ] **Step 3: Run the complete Maven verification**

Run: `./mvnw verify`

Expected: every test passes with zero failures and errors.

- [ ] **Step 4: Rebuild and inspect runtime deployment**

Run: `make rebuild`

Then verify with:

```bash
docker inspect --format '{{.State.Status}} restarts={{.RestartCount}} image={{.Image}}' saturn
docker exec saturn sh -c 'ls -lah /app/database/database.db*'
docker logs --since 5m saturn
```

Expected: one running container, zero restarts, database files visible, and no routing, database, or WebSocket errors.

- [ ] **Step 5: Update the README with the SDK contract model**

Document that tools expose structured metadata, that descriptors are authoritative, and that room-delivery tools must not be duplicated by the agent.

- [ ] **Step 6: Run final diff checks**

Run: `git diff --check && git status --short --branch`

Expected: no whitespace errors; unrelated existing work remains untouched; changes are ready for review without an automatic commit.
