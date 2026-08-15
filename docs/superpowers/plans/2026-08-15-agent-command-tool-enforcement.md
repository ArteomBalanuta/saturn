# Agent Command Tool Enforcement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent Vaelen from displaying Markdown-wrapped Saturn commands and require those commands to travel through the authorized `run_command` tool channel.

**Architecture:** Add a focused guard that derives allowed command names from the invocation's capability-filtered `run_command` schema and recognizes command-shaped inline or fenced Markdown. `DefaultAgentRouter` gives the provider one corrective completion; only exactly one matching structured tool call may proceed, while repeated, piggybacked, or evasive responses fail without being returned, executed, or persisted.

**Tech Stack:** Java 24, Gson, OpenAI-compatible chat completions, JUnit 5, Maven, Google Java Format.

## Global Constraints

- Never strip Markdown and execute model-authored prose.
- Recognize only commands exposed by the current invocation's `run_command` enum.
- Preserve existing authorization, tool budgets, duplicate detection, and gateway behavior.
- Allow at most one corrective completion per routed request.
- Require a corrective response to contain exactly one matching `run_command` call or one valid
  non-executable `respond_without_command` result.
- Never return or persist rejected pseudo-command text.

---

### Task 1: Detect Command-Shaped Assistant Prose

**Files:**
- Create: `src/main/java/org/saturn/app/agent/AgentCommandProseGuard.java`
- Create: `src/test/java/org/saturn/app/agent/AgentCommandProseGuardTest.java`

**Interfaces:**
- Consumes: capability-filtered `List<JsonObject>` tool definitions and `LlmToolCall` values.
- Produces: `AgentCommandProseGuard.from(List<JsonObject>)`, `findCommand(String)`, and `matches(LlmToolCall, String)`.

- [x] **Step 1: Write failing detector tests**

```java
@Test
void detectsOnlyMarkdownWrappedCommandsExposedToTheCaller() {
  AgentCommandProseGuard guard = guardFor(regularContext());

  assertEquals(Optional.of("weather"), guard.findCommand("As requested: `weather charlotte`"));
  assertEquals(Optional.of("weather"), guard.findCommand("``weather charlotte``"));
  assertEquals(Optional.of("time"), guard.findCommand("```text\n*time Tokyo\n```"));
  assertEquals(Optional.of("time"), guard.findCommand("~~~text\n*time Tokyo\n~~~"));
  assertTrue(guard.findCommand("Use `List.of()` here").isEmpty());
  assertTrue(guard.findCommand("`kick bob`").isEmpty());
}

@Test
void acceptsOnlyMatchingStructuredRunCommandCalls() {
  AgentCommandProseGuard guard = guardFor(regularContext());

  assertTrue(
      guard.matches(
          new LlmToolCall("call-1", "run_command",
              "{\"command\":\"weather\",\"arguments\":\"charlotte\"}"),
          "weather"));
  assertFalse(
      guard.matches(
          new LlmToolCall("call-2", "run_command", "{\"command\":\"help\"}"),
          "weather"));
}
```

- [x] **Step 2: Run the focused test and verify RED**

Run: `./mvnw -Dtest=AgentCommandProseGuardTest test`

Expected: test compilation fails because `AgentCommandProseGuard` does not exist.

- [x] **Step 3: Implement the guard**

```java
final class AgentCommandProseGuard {
  private static final String RUN_COMMAND = "run_command";
  private static final Pattern BACKTICK_FENCED_CODE =
      Pattern.compile("(?ms)^[ \\t]{0,3}`{3,}[^\\r\\n]*\\R(.*?)^[ \\t]{0,3}`{3,}[ \\t]*$");
  private static final Pattern TILDE_FENCED_CODE =
      Pattern.compile("(?ms)^[ \\t]{0,3}~{3,}[^\\r\\n]*\\R(.*?)^[ \\t]{0,3}~{3,}[ \\t]*$");
  private static final Pattern INLINE_CODE =
      Pattern.compile("(?<!`)(`+)([^`\\r\\n]+)\\1(?!`)");
  private final Gson gson = new Gson();
  private final Set<String> allowedCommands;

  private AgentCommandProseGuard(Set<String> allowedCommands) {
    this.allowedCommands = Set.copyOf(allowedCommands);
  }

  static AgentCommandProseGuard from(List<JsonObject> definitions) {
    Set<String> commands = new HashSet<>();
    for (JsonObject definition : definitions) {
      JsonObject function = definition.getAsJsonObject("function");
      if (function == null || !RUN_COMMAND.equals(string(function, "name"))) {
        continue;
      }
      JsonObject parameters = function.getAsJsonObject("parameters");
      JsonObject properties = parameters == null ? null : parameters.getAsJsonObject("properties");
      JsonObject command = properties == null ? null : properties.getAsJsonObject("command");
      JsonArray values = command == null ? null : command.getAsJsonArray("enum");
      if (values != null) {
        values.forEach(value -> commands.add(value.getAsString().toLowerCase(Locale.ROOT)));
      }
    }
    return new AgentCommandProseGuard(commands);
  }

  Optional<String> findCommand(String content) {
    if (content == null || content.isBlank()) {
      return Optional.empty();
    }
    Optional<String> fenced = findIn(content, BACKTICK_FENCED_CODE, 1);
    if (fenced.isEmpty()) {
      fenced = findIn(content, TILDE_FENCED_CODE, 1);
    }
    return fenced.isPresent() ? fenced : findIn(content, INLINE_CODE, 2);
  }

  boolean matches(LlmToolCall call, String expectedCommand) {
    if (!RUN_COMMAND.equals(call.name())) {
      return false;
    }
    try {
      JsonObject arguments = gson.fromJson(call.arguments(), JsonObject.class);
      return arguments != null
          && arguments.has("command")
          && expectedCommand.equalsIgnoreCase(arguments.get("command").getAsString());
    } catch (JsonParseException | IllegalStateException exception) {
      return false;
    }
  }

  private Optional<String> findIn(String content, Pattern pattern, int contentGroup) {
    Matcher matcher = pattern.matcher(content);
    while (matcher.find()) {
      Optional<String> command = commandAtStart(matcher.group(contentGroup));
      if (command.isPresent()) {
        return command;
      }
    }
    return Optional.empty();
  }

  private Optional<String> commandAtStart(String snippet) {
    String normalized = snippet.stripLeading();
    if (!normalized.isEmpty() && !Character.isLetterOrDigit(normalized.codePointAt(0))) {
      normalized = normalized.substring(Character.charCount(normalized.codePointAt(0))).stripLeading();
    }
    int separator = normalized.indexOf(' ');
    String firstToken = (separator < 0 ? normalized : normalized.substring(0, separator))
        .toLowerCase(Locale.ROOT);
    return allowedCommands.contains(firstToken) ? Optional.of(firstToken) : Optional.empty();
  }

  private static String string(JsonObject object, String name) {
    return object.has(name) && object.get(name).isJsonPrimitive()
        ? object.get(name).getAsString()
        : "";
  }
}
```

Normalize one optional non-alphanumeric command prefix before comparing the snippet's first token.
Return an immutable allowed-command set and treat malformed definitions or arguments as non-matches.

- [x] **Step 4: Run the focused test and verify GREEN**

Run: `./mvnw -Dtest=AgentCommandProseGuardTest test`

Expected: all detector tests pass.

---

### Task 2: Correct Pseudo-Commands at the Router Boundary

**Files:**
- Modify: `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java`
- Modify: `src/test/java/org/saturn/app/agent/DefaultAgentRouterTest.java`
- Modify: `docs/superpowers/specs/2026-08-15-agent-command-tool-enforcement-design.md`

**Interfaces:**
- Consumes: `AgentCommandProseGuard` from Task 1 and existing `LlmResponse`/`LlmRequest` routing types.
- Produces: one guarded corrective completion integrated into `DefaultAgentRouter.routeInSession`.

- [x] **Step 1: Clarify the approved failure rule in the design**

Replace the final correction step with: "Fail the turn unless the corrective completion contains exactly one matching structured `run_command` call; ordinary prose, a different command, extra tool calls, or another wrapped command is not accepted."

- [x] **Step 2: Write a failing correction-and-execution test**

```java
@Test
void convertsWrappedCommandIntentIntoARealToolCallWithoutPublishingTheWrapper() throws Exception {
  List<String> executions = new ArrayList<>();
  RunCommandTool commandTool =
      new RunCommandTool((context, command, arguments) -> {
        executions.add(command + " " + arguments);
        return true;
      });
  ScriptedClient client =
      new ScriptedClient(
          new LlmResponse("As commanded:\n`weather charlotte`", List.of(), "stop"),
          new LlmResponse(
              "",
              List.of(new LlmToolCall(
                  "weather-1", "run_command",
                  "{\"command\":\"weather\",\"arguments\":\"charlotte\"}")),
              "tool_calls"),
          new LlmResponse("The live weather was sent to the room.", List.of(), "stop"));
  RecordingMemory memory = new RecordingMemory();
  DefaultAgentRouter router =
      new DefaultAgentRouter(
          config(4, 2_000), client,
          new AgentToolRegistry().register(commandTool).freeze(), memory);

  AgentResult result = router.route(new AgentInvocation(context(), "please fetch it"));

  assertEquals(List.of("weather charlotte"), executions);
  assertEquals("The live weather was sent to the room.", result.content());
  assertFalse(memory.appended.stream().anyMatch(value -> value.contains("`weather charlotte`")));
}
```

- [x] **Step 3: Write failing rejection and false-positive tests**

```java
@Test
void rejectsCorrectionThatDoesNotReturnTheMatchingToolCall() {
  ScriptedClient client =
      new ScriptedClient(
          new LlmResponse("`weather charlotte`", List.of(), "stop"),
          new LlmResponse("I will not call it.", List.of(), "stop"));
  RecordingMemory memory = new RecordingMemory();
  DefaultAgentRouter router = routerWithRunCommand(client, memory);

  assertThrows(
      AgentRoutingException.class,
      () -> router.route(new AgentInvocation(context(), "fetch weather")));
  assertTrue(memory.appended.isEmpty());
}

@Test
void preservesUnrelatedInlineCodeInOrdinaryAnswers() throws Exception {
  ScriptedClient client =
      new ScriptedClient(new LlmResponse("Use `List.of()`.", List.of(), "stop"));

  AgentResult result = routerWithRunCommand(client, new RecordingMemory())
      .route(new AgentInvocation(context(), "show Java"));

  assertEquals("Use `List.of()`.", result.content());
  assertEquals(1, client.requests.size());
}
```

- [x] **Step 4: Run router tests and verify RED**

Run: `./mvnw -Dtest=DefaultAgentRouterTest test`

Expected: wrapped-command tests fail because the current router returns the first prose completion.

- [x] **Step 5: Implement one-shot correction**

```java
private GuardedResponse enforceCommandChannel(
    LlmResponse response,
    List<LlmMessage> messages,
    List<JsonObject> definitions,
    AgentCommandProseGuard guard,
    boolean correctionUsed,
    boolean runCommandSucceeded)
    throws LlmException, AgentRoutingException {
  Optional<String> command =
      response.toolCalls().isEmpty() ? guard.findCommand(response.content()) : Optional.empty();
  if (command.isEmpty()) {
    return new GuardedResponse(response, correctionUsed);
  }
  if (correctionUsed) {
    throw new AgentRoutingException("Agent emitted a Saturn command as prose after correction");
  }
  messages.add(LlmMessage.assistant(response.content(), List.of()));
  messages.add(LlmMessage.user(correctionPrompt(command.get(), runCommandSucceeded)));
  LlmResponse corrected =
      client.complete(new LlmRequest(messages, runCommandSucceeded ? List.of() : definitions));
  if (!runCommandSucceeded
      && (corrected.toolCalls().size() != 1
          || !guard.matches(corrected.toolCalls().getFirst(), command.get()))) {
    throw new AgentRoutingException("Agent did not return exactly one required Saturn tool call");
  }
  return new GuardedResponse(corrected, true);
}
```

Call this guard before processing each no-tool completion. Track successful `run_command` results,
and if a command already ran, use the one retry only to request a clean final answer with no tools.

- [x] **Step 6: Run router tests and verify GREEN**

Run: `./mvnw -Dtest=DefaultAgentRouterTest test`

Expected: all router tests pass and the rejected wrapper never reaches memory.

---

### Task 3: Make the Runtime and Persona Action-First

**Files:**
- Modify: `src/main/java/org/saturn/app/agent/AgentSystemPrompt.java`
- Modify: `src/main/resources/agent/vaelen-system-prompt.txt`
- Modify: `src/test/java/org/saturn/app/agent/AgentSystemPromptTest.java`

**Interfaces:**
- Consumes: the existing rendered Saturn runtime policy and Vaelen persona.
- Produces: an execution-first prompt hierarchy plus explicit live weather/time command-channel
  guidance.

- [x] **Step 1: Add a failing policy payload test**

Add `prioritizesAuthorizedExecutionOverPersonaAndDialogue` and assert that the policy:

```java
assertTrue(rendered.contains("Execute the user's authorized request"));
assertTrue(rendered.contains("This duty outranks persona"));
assertTrue(rendered.contains("call the matching tool immediately"));
assertTrue(rendered.contains("Do not mock, lecture, philosophize"));
assertTrue(rendered.contains("For current weather or time, call run_command"));
assertTrue(rendered.contains("Never print, quote, or fence a Saturn command"));
assertTrue(rendered.indexOf("Execute the user's authorized request") < rendered.indexOf("PERSONA"));
```

- [x] **Step 2: Run the focused test and verify RED**

Run: `./mvnw -Dtest=AgentSystemPromptTest test`

Expected: the new policy assertions fail.

- [x] **Step 3: Add action-first runtime and persona instructions**

Add runtime rules above the persona that require Saturn to resolve available context, execute the
matching authorized tool immediately, avoid confirmation or repeated questions, and report the
real outcome. Keep these command-channel rules explicit:

```text
For current weather or time, call run_command before answering. Never print, quote, or fence a
Saturn command as a substitute for a run_command tool call.
```

Rewrite conflicting persona guidance so calm mentor styling remains optional and never encourages
mockery, philosophical detours, stage directions, threats, or dialogue in place of execution.

- [x] **Step 4: Run focused agent tests**

Run: `./mvnw -Dtest=AgentCommandProseGuardTest,DefaultAgentRouterTest,AgentSystemPromptTest test`

Expected: all focused tests pass.

---

### Task 4: Format, Verify, Rebuild, and Inspect Runtime Logs

**Files:**
- Modify only files listed in Tasks 1-3 through formatting.

**Interfaces:**
- Consumes: the completed command-channel guard.
- Produces: a verified shaded JAR and a rebuilt running Saturn container.

- [x] **Step 1: Format changed Java files**

Run Google Java Format through Spotless on the six changed Java source/test files:

`./mvnw spotless:apply -DspotlessFiles=src/main/java/org/saturn/app/agent/AgentCommandProseGuard.java,src/main/java/org/saturn/app/agent/DefaultAgentRouter.java,src/main/java/org/saturn/app/agent/AgentSystemPrompt.java,src/test/java/org/saturn/app/agent/AgentCommandProseGuardTest.java,src/test/java/org/saturn/app/agent/DefaultAgentRouterTest.java,src/test/java/org/saturn/app/agent/AgentSystemPromptTest.java`

Then run:

`./mvnw spotless:check -DspotlessFiles=src/main/java/org/saturn/app/agent/AgentCommandProseGuard.java,src/main/java/org/saturn/app/agent/DefaultAgentRouter.java,src/main/java/org/saturn/app/agent/AgentSystemPrompt.java,src/test/java/org/saturn/app/agent/AgentCommandProseGuardTest.java,src/test/java/org/saturn/app/agent/DefaultAgentRouterTest.java,src/test/java/org/saturn/app/agent/AgentSystemPromptTest.java`

Expected: Spotless reports success for every changed Java file.

- [x] **Step 2: Run the complete verification suite**

Run: `./mvnw verify`

Expected: all tests pass and Maven builds `target/saturn.jar`.

- [x] **Step 3: Audit the diff**

Run: `git diff --check` and `git status --short`.

Expected: no whitespace errors and only the planned files are modified or added.

- [x] **Step 4: Rebuild Saturn**

Run: `make rebuild`

Expected: the image builds, the old container stops gracefully, and a new `saturn` container starts.

- [x] **Step 5: Inspect startup and agent logs**

Run: `docker inspect saturn` and `docker logs --since 2m saturn`.

Expected: the container is running with zero restarts and no SQLite, router, or startup errors.

---

### Task 5: Independent Review Hardening

- [x] **Step 1: Reject corrective responses containing extra tool calls before execution**
- [x] **Step 2: Cover multi-backtick inline spans and tilde-fenced command prose**
- [x] **Step 3: Tell the provider to return exactly one matching corrective tool call**
- [x] **Step 4: Treat `Status.FAILED` command results as failed tool execution**
- [x] **Step 5: Separate successful and failed post-tool command cleanup prompts**
- [x] **Step 6: Validate corrective calls against exact argument fields and types**
- [x] **Step 7: Track successful execution by exact command name across multi-command turns**
- [x] **Step 8: Re-run formatting, full verification, rebuild, and runtime inspection**

---

### Task 6: Runtime Memory Regression Hardening

- [x] **Step 1: Correlate persisted `agent_memory`, room messages, router logs, and executed tools**
- [x] **Step 2: Reproduce public `DIRECT` room-context omission with a failing test**
- [x] **Step 3: Hydrate public direct requests while keeping whispers isolated**
- [x] **Step 4: Reproduce and fix wrapped command references being forced into execution**
- [x] **Step 5: Fail closed on memory load and append errors instead of answering statelessly**
- [x] **Step 6: Include the exact executed command in successful `run_command` results**
- [x] **Step 7: Run focused agent and tool tests**
- [x] **Step 8: Diagnose live `SQLITE_IOERR_SHORT_READ` as split host/container WAL state**
- [x] **Step 9: Stop Saturn, create a consistent backup, verify integrity, and reset WAL sidecars**
- [x] **Step 10: Run full verification, rebuild, and inspect live memory diagnostics**

---

### Task 7: Stale Llama Completion Hardening

- [x] **Step 1: Correlate queue, request, memory, and outbound message hashes**
- [x] **Step 2: Prove two different prompts persisted the exact same assistant payload**
- [x] **Step 3: Rule out duplicate routing, duplicate queue drains, and multiple Saturn runtimes**
- [x] **Step 4: Verify the llama.cpp endpoint accepts `cache_prompt: false`**
- [x] **Step 5: Retry a stale completion once with prompt cache bypassed**
- [x] **Step 6: Reject and avoid persisting a second stale completion**
- [x] **Step 7: Preserve identical responses when the user repeats the same prompt**
- [x] **Step 8: Run full verification, rebuild, and inspect live diagnostics**
