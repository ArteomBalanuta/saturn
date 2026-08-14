# Saturn Agent Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current inline `*l` prototype with a bounded, testable ACE-inspired agent router and SDK integrated with Saturn.

**Architecture:** Keep chat integration behind `AgentService`, execute a provider-neutral router over `LlmClient`, and route model tool calls through an immutable catalog plus policy executor. Use dedicated SQLite repositories for named read queries and bounded conversation memory.

**Tech Stack:** Java 23, Gson, Java `HttpClient`, SQLite JDBC, JUnit 5, Mockito only where an external boundary requires it.

## Global Constraints

- Work directly on `develop` as explicitly requested.
- Do not call the real LLM endpoint from tests.
- Never expose arbitrary SQL or destructive/admin commands to the model.
- Preserve Saturn's `OutService` newline handling and HELP `\u2009` formatting.
- API secrets come from environment variables, not committed TOML values.
- Every asynchronous queue, HTTP call, tool loop, prompt, output, and memory load is bounded.

---

### Task 1: Runtime Contracts and Configuration

**Files:**
- Create: `src/main/java/org/saturn/app/agent/AgentConfig.java`
- Create: `src/main/java/org/saturn/app/agent/AgentInvocation.java`
- Create: `src/main/java/org/saturn/app/agent/AgentResult.java`
- Create: `src/main/java/org/saturn/app/agent/AgentMessage.java`
- Create: `src/main/java/org/saturn/app/agent/AgentRouter.java`
- Create: `src/test/java/org/saturn/app/agent/AgentConfigTest.java`

**Interfaces:**
- Produces: `AgentConfig.from(Toml, Map<String,String>)`, `AgentRouter.route(AgentInvocation)`.

- [ ] Write tests proving defaults, environment API-key lookup, and rejection of invalid endpoint/limits.
- [ ] Run `./mvnw -q -Dtest=AgentConfigTest test` and confirm failure because contracts do not exist.
- [ ] Implement immutable records and validated configuration.
- [ ] Re-run the focused test and confirm success.

### Task 2: Provider Port and OpenAI Adapter

**Files:**
- Create: `src/main/java/org/saturn/app/agent/llm/LlmClient.java`
- Create: `src/main/java/org/saturn/app/agent/llm/LlmRequest.java`
- Create: `src/main/java/org/saturn/app/agent/llm/LlmResponse.java`
- Create: `src/main/java/org/saturn/app/agent/llm/LlmToolCall.java`
- Create: `src/main/java/org/saturn/app/agent/llm/OpenAiCompatibleClient.java`
- Create: `src/test/java/org/saturn/app/agent/llm/OpenAiCompatibleClientTest.java`

**Interfaces:**
- Consumes: `AgentConfig`.
- Produces: `LlmClient.complete(LlmRequest)` with typed response and tool calls.

- [ ] Write in-process HTTP tests for endpoint path, optional model omission, bearer header, response parsing, transient retry, non-retryable 4xx, and malformed payloads.
- [ ] Run the focused test and confirm expected compilation/test failures.
- [ ] Implement typed mapping, timeout, status validation, and bounded retry/backoff.
- [ ] Re-run the focused test and confirm success.

### Task 3: Tool Catalog and Policy Executor

**Files:**
- Replace: `src/main/java/org/saturn/app/agent/AgentTool.java`
- Replace: `src/main/java/org/saturn/app/agent/AgentToolRegistry.java`
- Create: `src/main/java/org/saturn/app/agent/AgentToolResult.java`
- Create: `src/main/java/org/saturn/app/agent/AgentToolExecutor.java`
- Replace: `src/test/java/org/saturn/app/agent/AgentToolRegistryTest.java`
- Create: `src/test/java/org/saturn/app/agent/AgentToolExecutorTest.java`

**Interfaces:**
- Produces: immutable registry definitions and `AgentToolExecutor.execute(context, call)`.

- [ ] Write tests for unknown tools, malformed arguments, duplicate calls, per-tool limits, failure disabling, and stable structured results.
- [ ] Run focused tests and confirm failure for missing policy behavior.
- [ ] Implement registry freeze semantics, JSON-schema definitions, execution state per invocation, and safe error conversion.
- [ ] Re-run focused tests and confirm success.

### Task 4: Router, Limits, and Finalization

**Files:**
- Create: `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java`
- Create: `src/main/java/org/saturn/app/agent/AgentMemoryStore.java`
- Create: `src/test/java/org/saturn/app/agent/DefaultAgentRouterTest.java`

**Interfaces:**
- Consumes: `LlmClient`, registry/executor, memory store, config.
- Produces: bounded `AgentResult` and one no-tools finalization call on exhaustion.

- [ ] Write scripted-client tests for plain answers, multi-tool loops, duplicate/error-only termination, cancellation/interruption, prompt limits, output limits, and final synthesis.
- [ ] Run focused tests and confirm failure.
- [ ] Implement the router, correlation IDs, system context, bounded history, and finalization path.
- [ ] Re-run focused tests and confirm success.

### Task 5: Saturn Tools and SQLite Persistence

**Files:**
- Create: `src/main/java/org/saturn/app/agent/tool/RoomUsersTool.java`
- Create: `src/main/java/org/saturn/app/agent/tool/RunCommandTool.java`
- Create: `src/main/java/org/saturn/app/agent/tool/DatabaseQueryTool.java`
- Create: `src/main/java/org/saturn/app/agent/persistence/AgentQueryRepository.java`
- Create: `src/main/java/org/saturn/app/agent/persistence/SqliteAgentQueryRepository.java`
- Create: `src/main/java/org/saturn/app/agent/persistence/SqliteAgentMemoryStore.java`
- Modify: `schema.sql`
- Create: `database/migrations/20260815_agent_memory.sql`
- Test: corresponding files under `src/test/java/org/saturn/app/agent/`.

**Interfaces:**
- Produces: safe room data, named read queries, authorization-preserving command execution, and TTL-bounded memory.

- [ ] Write temporary-SQLite tests for schema, query allowlist, prepared parameters, row limits, memory identity, TTL cleanup, and turn limits; write command-tool authorization tests.
- [ ] Run focused tests and confirm failure.
- [ ] Implement dedicated-connection repositories and concrete tools.
- [ ] Keep `schema.sql`, migration SQL, Docker database setup, and tests consistent.
- [ ] Re-run focused tests and confirm success.

### Task 6: Service, Command, Lifecycle, and Documentation

**Files:**
- Replace: `src/main/java/org/saturn/app/service/AgentService.java`
- Replace: `src/main/java/org/saturn/app/service/impl/AgentServiceImpl.java`
- Modify: `src/main/java/org/saturn/app/facade/Base.java`
- Modify: `src/main/java/org/saturn/app/facade/impl/EngineImpl.java`
- Modify: `src/main/java/org/saturn/app/command/impl/user/LUserCommandImpl.java`
- Modify: `src/main/java/org/saturn/app/command/impl/user/HelpUserCommandImpl.java`
- Modify: `config.example.toml`
- Modify: `README.md`
- Test: service and command tests.

**Interfaces:**
- Produces: bounded asynchronous submission, busy handling, clean shutdown, and documented `*l` behavior.

- [ ] Write tests for missing prompt, disabled agent, queue saturation, successful reply, whisper preservation, and shutdown rejection.
- [ ] Run focused tests and confirm failure.
- [ ] Wire validated config, router, repositories, registry, and bounded executor in one composition root.
- [ ] Update configuration and operational documentation.
- [ ] Run `./mvnw spotless:check`, `./mvnw test`, and `./mvnw package`; classify any unrelated pre-existing formatting debt explicitly.
