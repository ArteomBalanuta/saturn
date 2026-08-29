# Agent Command Tool Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose every reflected Saturn command handler as a contextual, validated agent tool without duplicating aliases or bypassing Saturn command authorization.

**Architecture:** `SaturnCommandToolCatalog` derives command identity and aliases from each handler's `@CommandAliases` annotation, while its metadata table supplies only agent-specific typed parameter contracts, capability gates, effects, and guidance. One `SaturnCommandTool` turns each catalog entry into an `AgentTool`, validates and renders its structured arguments, and delegates to `SaturnCommandGateway`; `AgentRuntimeFactory` registers every generated tool.

**Tech Stack:** Java 23, Gson JSON Schema contracts, JUnit 5, ClassGraph-backed Saturn command discovery, existing `AgentToolExecutor` and `EngineSaturnCommandGateway`.

## Global Constraints

- Derive canonical command aliases and all aliases from `@CommandAliases`; do not repeat them as catalog data.
- Provide one provider-visible tool for every concrete handler under `org.saturn.app.command.impl`.
- Preserve Saturn command authorization and output behavior by dispatching through `SaturnCommandGateway` only.
- Grant `ADMIN_COMMANDS` only when the direct caller trip equals `[agent-participation].creatorTrip`.
- Do not grant admin tools to ambient, moderation, mention, or non-creator invocations.
- Treat every command-derived tool as sequential, non-idempotent action work.
- Keep JSON schemas closed (`additionalProperties: false`) and return standard `AgentToolResult` errors for expected invalid input.
- Run focused tests first, then `./mvnw spotless:check`, `./mvnw test`, and `./mvnw package`.

---

### Task 1: Add Creator-Only Administrative Capability

**Files:**
- Modify: `src/main/java/org/saturn/app/agent/AgentCapability.java`
- Modify: `src/main/java/org/saturn/app/agent/AgentInvocationFactory.java`
- Modify: `src/test/java/org/saturn/app/agent/AgentInvocationFactoryTest.java`

**Interfaces:**
- Produces: `AgentCapability.ADMIN_COMMANDS`
- Produces: direct creator invocations with `ADMIN_COMMANDS`; all other invocation modes omit it.

- [ ] **Step 1: Write failing creator capability tests**

```java
assertTrue(direct.context().hasCapability(AgentCapability.ADMIN_COMMANDS));
assertFalse(mention.context().hasCapability(AgentCapability.ADMIN_COMMANDS));
assertFalse(impostor.context().hasCapability(AgentCapability.ADMIN_COMMANDS));
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `./mvnw -Dtest=AgentInvocationFactoryTest test`

Expected: compilation failure because `ADMIN_COMMANDS` does not exist.

- [ ] **Step 3: Add the capability and grant condition**

```java
public enum AgentCapability {
  DYNAMIC_SQL,
  MODERATION_COMMANDS,
  PERMANENT_BAN,
  ADMIN_COMMANDS
}

if (creator && mode == AgentInvocationMode.DIRECT) {
  capabilities.add(AgentCapability.ADMIN_COMMANDS);
}
```

- [ ] **Step 4: Run the focused test to verify it passes**

Run: `./mvnw -Dtest=AgentInvocationFactoryTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/saturn/app/agent/AgentCapability.java \
  src/main/java/org/saturn/app/agent/AgentInvocationFactory.java \
  src/test/java/org/saturn/app/agent/AgentInvocationFactoryTest.java
git commit -m "feat: add creator admin agent capability"
```

### Task 2: Create Reflection-Backed Command Identity And Contract Metadata

**Files:**
- Create: `src/main/java/org/saturn/app/agent/tool/SaturnCommandToolCatalog.java`
- Create: `src/test/java/org/saturn/app/agent/tool/SaturnCommandToolCatalogTest.java`
- Modify: `src/main/java/org/saturn/app/agent/AgentToolSchemaValidator.java` only if a documented schema feature is missing.

**Interfaces:**
- Produces: `SaturnCommandToolCatalog.entries()` returning immutable `CommandToolDefinition` entries.
- Produces: `CommandToolDefinition.toolName()`, `commandAlias()`, `aliases()`, `parameters()`, `requiredCapabilities()`, `effect()`, `isIdempotent()`, `timeout()`, `description()`, `whenToUse()`, `whenNotToUse()`, `examples()`, and `renderArguments(JsonObject)`.
- Consumes: `@CommandAliases` on every concrete Saturn command handler.

- [ ] **Step 1: Write failing catalog completeness and schema tests**

```java
assertEquals(discoveredCommandTypes(), catalog.handlerTypes());
assertTrue(catalog.entries().stream().allMatch(entry -> entry.aliases().contains(entry.commandAlias())));
assertTrue(catalog.entries().stream().allMatch(entry -> entry.parameters().get("additionalProperties").getAsBoolean() == false));
assertTrue(catalog.entries().stream().allMatch(entry -> !entry.whenNotToUse().isEmpty()));
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `./mvnw -Dtest=SaturnCommandToolCatalogTest test`

Expected: compilation failure because the catalog does not exist.

- [ ] **Step 3: Implement reflection identity and explicit agent metadata**

```java
@CommandAliases(aliases = {"weather", "w", "today"})
// The catalog reads this annotation; no aliases are stored in catalog metadata.

record CommandToolDefinition(
    Class<? extends UserCommand> handlerType,
    String toolName,
    String commandAlias,
    List<String> aliases,
    JsonObject parameters,
    Set<AgentCapability> requiredCapabilities,
    ToolEffect effect,
    boolean isIdempotent,
    Duration timeout,
    String description,
    List<String> whenToUse,
    List<String> whenNotToUse,
    List<ToolExample> examples,
    Function<JsonObject, String> argumentRenderer) {}
```

Implement one metadata row per handler type. Derive `commandAlias` from the first annotation alias,
derive `aliases` from the same annotation, and fail construction if discovery differs from the
metadata map. Use tool names such as `saturn_weather`, `saturn_kick`, and `saturn_restart` to avoid
collisions with the existing `room_users` and database tools. Define typed fields for each command:
target/user fields as `target`, text tails as `message` or `reason`, command modes as enums, and
structured multi-argument commands with one property per semantic argument.

- [ ] **Step 4: Run catalog tests to verify they pass**

Run: `./mvnw -Dtest=SaturnCommandToolCatalogTest test`

Expected: PASS; the discovered handler count equals the catalog count and every schema validates.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/saturn/app/agent/tool/SaturnCommandToolCatalog.java \
  src/test/java/org/saturn/app/agent/tool/SaturnCommandToolCatalogTest.java
git commit -m "feat: define catalog for Saturn command tools"
```

### Task 3: Implement The Shared Catalog-Derived Tool

**Files:**
- Create: `src/main/java/org/saturn/app/agent/tool/SaturnCommandTool.java`
- Create: `src/test/java/org/saturn/app/agent/tool/SaturnCommandToolTest.java`
- Modify: `src/main/java/org/saturn/app/agent/AgentToolDescriptor.java` only if required to declare per-entry timeouts without changing existing tools.

**Interfaces:**
- Consumes: `SaturnCommandToolCatalog.CommandToolDefinition`, `SaturnCommandGateway`, `AgentContext`.
- Produces: a contextual `AgentTool` that describes and executes exactly one catalog command.

- [ ] **Step 1: Write failing descriptor, validation, and dispatch tests**

```java
SaturnCommandTool tool = new SaturnCommandTool(weatherDefinition, gateway);
assertEquals("saturn_weather", tool.name());
assertFalse(tool.descriptor(userContext).isReadOnly());
assertFalse(tool.descriptor(userContext).isIdempotent());
assertEquals("weather", executedCommand.get());
assertEquals("Tokyo", executedArguments.get());
assertTrue(tool.execute(userContext, new JsonObject()).isError());
```

Add a capability test that verifies a creator-only definition is unavailable to a moderator context
and an injected call does not reach the gateway.

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `./mvnw -Dtest=SaturnCommandToolTest test`

Expected: compilation failure because `SaturnCommandTool` does not exist.

- [ ] **Step 3: Implement descriptor and command rendering**

```java
@Override
public boolean isAvailableTo(AgentContext context) {
  return context.capabilities().containsAll(definition.requiredCapabilities());
}

@Override
public AgentToolResult execute(AgentContext context, JsonObject arguments) {
  String validationError = definition.validate(arguments);
  if (validationError != null) {
    return AgentToolResult.error(null, name(), "INVALID_ARGUMENTS", validationError);
  }
  SaturnCommandGateway.CommandExecution execution =
      gateway.executeWithResult(context, definition.commandAlias(), definition.renderArguments(arguments));
  return execution.executed()
      ? AgentToolResult.success(name(), execution.modelData())
      : AgentToolResult.error(null, name(), "COMMAND_REJECTED", "Saturn rejected the command");
}
```

Build the descriptor with `ToolEffect.ROOM_MESSAGE` or the concrete action effect recorded in the
definition, `ToolResultMode.ROOM_DELIVERY_AND_MODEL_DATA`, `false` idempotency, a nonzero timeout,
and catalog guidance/examples. Do not special-case direct command authorization here.

- [ ] **Step 4: Run the focused test to verify it passes**

Run: `./mvnw -Dtest=SaturnCommandToolTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/saturn/app/agent/tool/SaturnCommandTool.java \
  src/test/java/org/saturn/app/agent/tool/SaturnCommandToolTest.java
git commit -m "feat: add validated Saturn command tool wrapper"
```

### Task 4: Register Catalog Tools And Retire The Partial Command Bridge

**Files:**
- Modify: `src/main/java/org/saturn/app/agent/AgentRuntimeFactory.java`
- Delete: `src/main/java/org/saturn/app/agent/tool/RunCommandTool.java`
- Modify: `src/test/java/org/saturn/app/agent/AgentRuntimeFactoryTest.java`
- Modify: `src/test/java/org/saturn/app/agent/tool/SaturnAgentToolsTest.java`
- Modify: `src/test/java/org/saturn/app/agent/AgentToolRegistryTest.java`

**Interfaces:**
- Produces: registry definitions containing catalog tools contextual to the caller.
- Removes: `run_command` as the provider-visible generic command tool.

- [ ] **Step 1: Write failing registry visibility tests**

```java
assertTrue(toolNames(creator).contains("saturn_restart"));
assertFalse(toolNames(moderator).contains("saturn_restart"));
assertTrue(toolNames(moderator).contains("saturn_kick"));
assertFalse(toolNames(regularUser).contains("saturn_kick"));
assertFalse(toolNames(ambient).contains("saturn_restart"));
assertFalse(toolNames(creator).contains("run_command"));
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `./mvnw -Dtest=AgentToolRegistryTest,AgentRuntimeFactoryTest,SaturnAgentToolsTest test`

Expected: assertions fail because only `run_command` is registered.

- [ ] **Step 3: Register all catalog tools**

```java
AgentToolRegistry registry = new AgentToolRegistry();
// Register existing room/database tools first.
SaturnCommandToolCatalog.entries().forEach(
    definition -> registry.register(new SaturnCommandTool(definition, commandGateway)));
registry.freeze();
```

Delete `RunCommandTool` and convert any tests that assert its behavior into `SaturnCommandTool`
tests. Keep `EngineSaturnCommandGateway` unchanged as the sole bridge to `UserCommandBaseImpl`.

- [ ] **Step 4: Run focused registry tests to verify they pass**

Run: `./mvnw -Dtest=AgentToolRegistryTest,AgentRuntimeFactoryTest,SaturnAgentToolsTest test`

Expected: PASS; capabilities filter definitions and no generic command tool remains.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/saturn/app/agent/AgentRuntimeFactory.java \
  src/main/java/org/saturn/app/agent/tool \
  src/test/java/org/saturn/app/agent
git commit -m "feat: register every Saturn command as an agent tool"
```

### Task 5: Update Prompt Resources And Generate Command Inventory

**Files:**
- Create: `COMMAND_TOOL_INVENTORY.md`
- Modify: `src/main/resources/agent/tool-copy.json`
- Modify: `src/main/resources/agent/system-policy.txt`
- Modify: `src/main/resources/agent/participation-moderation.txt`
- Modify: `src/main/resources/agent/router-command-tool-correction.txt`
- Modify: `AGENTIC_ARCHITECTURE.md`
- Modify: `TOOL_ROUTING_ARCHITECTURE.md`
- Modify: `src/test/java/org/saturn/app/agent/AgentPromptCatalogTest.java`

**Interfaces:**
- Produces: documentation mapping every handler to its agent tool and a prompt policy that refers to
  the contextual named contracts instead of `run_command`.

- [ ] **Step 1: Write failing prompt and inventory consistency tests**

```java
assertFalse(systemPolicy.contains("run_command"));
assertTrue(inventory.contains("saturn_weather"));
assertTrue(inventory.contains("WeatherUserCommandImpl"));
assertEquals(SaturnCommandToolCatalog.entries().size(), documentedInventoryRows(inventory));
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `./mvnw -Dtest=AgentPromptCatalogTest,SaturnCommandToolCatalogTest test`

Expected: assertions fail because prompt resources still reference `run_command` and the inventory is absent.

- [ ] **Step 3: Update resources and documentation**

Replace generic command guidance with: choose the named `saturn_*` tool matching the requested
command; call it only when the user requests execution; do not write a command as prose. State that
only creator-direct sessions can see creator administration tools. Generate a Markdown table from
the catalog containing handler class, canonical alias, aliases, tool name, parameter synopsis,
required capability, effect, idempotency, and sequential execution rule.

- [ ] **Step 4: Run focused documentation tests to verify they pass**

Run: `./mvnw -Dtest=AgentPromptCatalogTest,SaturnCommandToolCatalogTest test`

Expected: PASS; every catalog entry has exactly one inventory row.

- [ ] **Step 5: Commit**

```bash
git add COMMAND_TOOL_INVENTORY.md src/main/resources/agent \
  AGENTIC_ARCHITECTURE.md TOOL_ROUTING_ARCHITECTURE.md \
  src/test/java/org/saturn/app/agent/AgentPromptCatalogTest.java
git commit -m "docs: document Saturn command tool inventory"
```

### Task 6: Verify End-To-End Contract Coverage

**Files:**
- Modify: `src/test/java/org/saturn/app/agent/AgentToolExecutorTest.java`
- Modify: `src/test/java/org/saturn/app/agent/DefaultAgentRouterTest.java`
- Modify: `src/test/java/org/saturn/app/agent/tool/EngineSaturnCommandGatewayTest.java`

**Interfaces:**
- Verifies: malformed command parameters never reach the gateway; allowed named tools produce normal
  observations; creator-only tools remain unavailable/invocation-rejected for other contexts.

- [ ] **Step 1: Write failing executor and router tests**

```java
AgentToolResult invalid = executor.execute(user, call("saturn_weather", "{}"));
assertEquals("INVALID_ARGUMENTS", invalid.errorCode());
assertEquals(0, gatewayCalls.get());

AgentToolResult injected = executor.execute(moderator, call("saturn_restart", "{}"));
assertEquals("UNKNOWN_TOOL", injected.errorCode());
```

- [ ] **Step 2: Run the focused tests to verify they fail**

Run: `./mvnw -Dtest=AgentToolExecutorTest,DefaultAgentRouterTest,EngineSaturnCommandGatewayTest test`

Expected: tests fail before catalog tools are fully integrated.

- [ ] **Step 3: Adjust only integration wiring exposed by the tests**

Use real catalog tools in the registry fixtures. Assert that an action command creates an ordered
observation and that no command-derived descriptor qualifies for a parallel batch.

- [ ] **Step 4: Run the focused tests to verify they pass**

Run: `./mvnw -Dtest=AgentToolExecutorTest,DefaultAgentRouterTest,EngineSaturnCommandGatewayTest test`

Expected: PASS.

- [ ] **Step 5: Run full verification**

Run:

```bash
./mvnw spotless:check
./mvnw test
./mvnw package
```

Expected: all commands compile, all tests pass, and the shaded jar builds.

- [ ] **Step 6: Commit**

```bash
git add src/test/java/org/saturn/app/agent
git commit -m "test: verify agent command tool coverage"
```
