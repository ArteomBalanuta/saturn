# Agentic Architecture Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split agent SDK serialization, request construction, and output normalization from router orchestration without changing public behavior.

**Architecture:** Preserve `DefaultAgentRouter` as the session coordinator. Introduce narrow collaborators for descriptor serialization, bounded request assembly, and response cleanup; retain the existing stateful tool loop behind the router boundary.

**Tech Stack:** Java 23, Gson, JUnit 5, Maven.

## Global Constraints

- Modify only the agentic package and its tests, plus `REFACTORING.md` and this design documentation.
- Keep public SDK method signatures source-compatible.
- Preserve existing command delivery and persisted memory behavior.

---

### Task 1: Extract Tool Definition Serialization

**Files:**
- Create: `src/main/java/org/saturn/app/agent/AgentToolDefinitionFactory.java`
- Modify: `src/main/java/org/saturn/app/agent/AgentToolRegistry.java`
- Test: `src/test/java/org/saturn/app/agent/AgentToolDefinitionFactoryTest.java`

- [x] Write a failing test that creates an `AgentToolDescriptor` and verifies the provider function name, schema, and SDK contract text.
- [x] Run `./mvnw -Dtest=AgentToolDefinitionFactoryTest test` and confirm compilation fails before the factory exists.
- [x] Implement the factory and make the registry delegate to it.
- [x] Run the focused test and `AgentToolRegistryTest`.

### Task 2: Extract Output Normalization

**Files:**
- Create: `src/main/java/org/saturn/app/agent/AgentResponseSanitizer.java`
- Modify: `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java`
- Test: `src/test/java/org/saturn/app/agent/AgentResponseSanitizerTest.java`

- [x] Write failing examples for persona-stage removal, plain evidence preservation, and Saturn thin-space list formatting.
- [x] Run `./mvnw -Dtest=AgentResponseSanitizerTest test` and confirm compilation fails before the sanitizer exists.
- [x] Implement the sanitizer and route all output/legacy-memory cleanup through it.
- [x] Run sanitizer and router tests.

### Task 3: Extract Request Assembly

**Files:**
- Create: `src/main/java/org/saturn/app/agent/AgentRequestAssembler.java`
- Modify: `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java`
- Test: `src/test/java/org/saturn/app/agent/AgentRequestAssemblerTest.java`

- [x] Write failing tests for mode-specific tool selection, contextualized prompts, and bounded history retaining the system/current-user turns.
- [x] Run the focused test and confirm the missing type failure.
- [x] Implement request assembly and replace the router's duplicated setup code.
- [x] Run assembler and router tests.

### Task 4: Documentation And Verification

**Files:**
- Create: `REFACTORING.md`
- Modify: `BUG_HUNT.md`

- [x] Document collaborator responsibilities, compatibility, and the remaining stateful tool loop.
- [x] Run `./mvnw test`, `./mvnw package`, and `git diff --check`.
