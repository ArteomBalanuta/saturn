# Agent Fresh User-History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent Saturn from answering named-user history requests from stale shared memory by requiring a successful current-invocation `user_message_history` call before LLM synthesis.

**Architecture:** A focused `AgentFreshnessPolicy` identifies when the newest prompt requires named-user history without generating tool arguments or response text. `DefaultAgentRouter` tracks successful tools for the current invocation and performs one focused correction when required data is missing. `UserMessageHistoryTool` enriches its existing bounded rows with evidence metadata so the LLM can state what it analyzed.

**Tech Stack:** Java 23, Gson, SQLite JDBC, JUnit 5, Maven, Google Java Format, Docker

## Global Constraints

- The LLM remains responsible for resolving the target nick and synthesizing the answer.
- Every named-user activity/history request requires a fresh `user_message_history` success in the same invocation.
- Prior assistant replies and prior tool-related conversation memory never satisfy freshness.
- `recent_messages_for_user` continues to default to and cap at 500 public rows across all rooms.
- Whisper rows remain excluded from public history.
- A failed or refused fresh lookup fails routing instead of returning stale prose.
- Existing uncommitted worktree changes must be preserved.

---

### Task 1: Classify Fresh Named-User History Requirements

**Files:**
- Create: `src/main/java/org/saturn/app/agent/AgentFreshnessPolicy.java`
- Create: `src/test/java/org/saturn/app/agent/AgentFreshnessPolicyTest.java`

**Interfaces:**
- Consumes: newest raw prompt and loaded shared `List<LlmMessage>`.
- Produces: `Optional<String> requiredTool(String prompt, List<LlmMessage> history)` whose current supported value is `user_message_history`.

- [ ] **Step 1: Write the failing classification tests**

```java
@Test
void requiresFreshHistoryForNamedUserAnalysis() {
  AgentFreshnessPolicy policy = new AgentFreshnessPolicy();

  assertEquals(Optional.of("user_message_history"), policy.requiredTool("tell me about jill user", List.of()));
  assertEquals(Optional.of("user_message_history"), policy.requiredTool("who is nex", List.of()));
  assertEquals(Optional.of("user_message_history"), policy.requiredTool("what did sun say recently?", List.of()));
}

@Test
void carriesFreshnessAcrossAnExplicitFollowUpButNotGeneralQuestions() {
  AgentFreshnessPolicy policy = new AgentFreshnessPolicy();
  List<LlmMessage> history = List.of(
      LlmMessage.user("Public Saturn message from @mer in #programming:\ntell me about jill user"),
      LlmMessage.assistant("Old Jill summary", List.of()));

  assertEquals(Optional.of("user_message_history"), policy.requiredTool("check it again", history));
  assertTrue(policy.requiredTool("tell me about Java records", List.of()).isEmpty());
  assertTrue(policy.requiredTool("who is in lounge?", List.of()).isEmpty());
  assertTrue(policy.requiredTool("status", history).isEmpty());
}
```

- [ ] **Step 2: Run the policy test and verify red**

Run: `./mvnw -q -Dtest=AgentFreshnessPolicyTest test`

Expected: compilation failure because `AgentFreshnessPolicy` does not exist.

- [ ] **Step 3: Implement the focused policy**

```java
final class AgentFreshnessPolicy {
  static final String USER_MESSAGE_HISTORY = "user_message_history";
  private static final Pattern USER_PROFILE =
      Pattern.compile(
          "(?is).*\\b(?:tell\\s+me\\s+about|describe|profile|summari[sz]e|analy[sz]e)"
              + "\\b.{0,120}\\b(?:user|nick|member|messages?|history|activity)\\b.*");
  private static final Pattern WHO_IS_USER =
      Pattern.compile(
          "(?is).*\\bwho\\s+is\\s+"
              + "(?!in\\b|here\\b|online\\b|currently\\b|present\\b)"
              + "[@\\p{L}\\p{N}_-]{1,100}(?:\\s+user)?\\b.*");
  private static final Pattern USER_SPEECH =
      Pattern.compile(
          "(?is).*\\bwhat\\s+(?:did|has)\\s+[@\\p{L}\\p{N}_-]{1,100}\\s+"
              + "(?:say|said|post|posted|write|wrote)\\b.*");
  private static final Pattern USER_HISTORY =
      Pattern.compile(
          "(?is).*\\b(?:messages?|history|activity)\\s+(?:of|for|from|by)\\s+"
              + "[@\\p{L}\\p{N}_-]{1,100}\\b.*");
  private static final Pattern HISTORY_FOLLOW_UP =
      Pattern.compile(
          "(?is)^\\s*(?:please\\s+)?(?:check|look\\s+up)\\s+"
              + "(?:it|him|her|them|that)(?:\\s+(?:again|elsewhere))?[?.!\\s]*$");

  Optional<String> requiredTool(String prompt, List<LlmMessage> history) {
    if (requiresNamedUserHistory(prompt)) {
      return Optional.of(USER_MESSAGE_HISTORY);
    }
    if (isHistoryFollowUp(prompt)
        && latestUser(history).map(this::requiresNamedUserHistory).orElse(false)) {
      return Optional.of(USER_MESSAGE_HISTORY);
    }
    return Optional.empty();
  }

  private boolean requiresNamedUserHistory(String prompt) {
    return prompt != null
        && (USER_PROFILE.matcher(prompt).matches()
            || WHO_IS_USER.matcher(prompt).matches()
            || USER_SPEECH.matcher(prompt).matches()
            || USER_HISTORY.matcher(prompt).matches());
  }

  private static boolean isHistoryFollowUp(String prompt) {
    return prompt != null && HISTORY_FOLLOW_UP.matcher(prompt).matches();
  }

  private static Optional<String> latestUser(List<LlmMessage> history) {
    for (int index = history.size() - 1; index >= 0; index--) {
      LlmMessage message = history.get(index);
      if ("user".equals(message.role())) {
        return Optional.ofNullable(message.content());
      }
    }
    return Optional.empty();
  }
}
```

The patterns deliberately distinguish named-user analysis from general topics and current room-presence questions. Follow-ups are limited to explicit lookup continuations such as `check it again`, `check them elsewhere`, or `look him up again`.

- [ ] **Step 4: Run the policy test and verify green**

Run: `./mvnw -q -Dtest=AgentFreshnessPolicyTest test`

Expected: PASS.

### Task 2: Enforce Same-Invocation Tool Freshness in the Router

**Files:**
- Modify: `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java`
- Create: `src/main/resources/agent/router-fresh-tool-correction.txt`
- Modify: `src/test/java/org/saturn/app/agent/DefaultAgentRouterTest.java`

**Interfaces:**
- Consumes: `AgentFreshnessPolicy.requiredTool(String, List<LlmMessage>)`, current `List<JsonObject>` tool definitions, and current `AgentToolResult` outcomes.
- Produces: a reply only after the required tool appears in the current invocation's successful-tool set.

- [ ] **Step 1: Write the repeated-prompt regression test**

```java
@Test
void refreshesUserHistoryInsteadOfReusingThePreviousSummary() throws Exception {
  String oldAnswer = "Jill is a user of modest but distinct activity.";
  RecordingMemory memory = new RecordingMemory(List.of(
      LlmMessage.user("Public Saturn message from @alice in #programming:\ntell me about jill user"),
      LlmMessage.assistant(oldAnswer, List.of())));
  AtomicInteger historyCalls = new AtomicInteger();
  AgentTool historyTool = new AgentTool() {
    public String name() {
      return "user_message_history";
    }

    public AgentToolResult execute(AgentContext context, JsonObject arguments) {
      historyCalls.incrementAndGet();
      return AgentToolResult.success(name(), "{\"returnedCount\":325,\"rows\":[]}");
    }
  };
  ScriptedClient client = new ScriptedClient(
      new LlmResponse(oldAnswer, List.of(), "stop"),
      new LlmResponse("", List.of(new LlmToolCall(
          "history-1", "user_message_history", "{\"nick\":\"jill\"}")), "tool_calls"),
      new LlmResponse(
          "Based on 325 public messages from timestamps 100 through 300, Jill repeatedly discusses food and weather.",
          List.of(),
          "stop"));

  DefaultAgentRouter router = new DefaultAgentRouter(
      config(4, 2_000),
      client,
      new AgentToolRegistry().register(historyTool).freeze(),
      memory);
  AgentResult result = router.route(
      new AgentInvocation(context(), "tell me about jill user"));

  assertEquals(1, historyCalls.get());
  assertEquals(3, client.requests.size());
  assertEquals(Set.of("user_message_history"), toolNames(client.requests.get(1)));
  assertTrue(result.content().contains("325"));
  assertFalse(result.content().equals(oldAnswer));
}
```

Add companion tests proving that a prose-only correction and a failed required tool throw `AgentRoutingException` without appending the stale answer. Preserve the existing general repeated `status` answer test.

- [ ] **Step 2: Run the router test and verify red**

Run: `./mvnw -q -Dtest=DefaultAgentRouterTest test`

Expected: the repeated Jill request returns after the first stale completion and the new assertions fail.

- [ ] **Step 3: Implement current-invocation enforcement**

Add an `AgentFreshnessPolicy` field, compute the required tool from `invocation.prompt()` plus cleaned history, and track all successful tool names:

```java
Optional<String> requiredFreshTool = freshnessPolicy.requiredTool(invocation.prompt(), history);
Set<String> successfulTools = new HashSet<>();
boolean freshnessCorrectionUsed = false;
```

Before breaking on a tool-free response, require the missing fresh tool:

```java
if (response.toolCalls().isEmpty()
    && requiredFreshTool.filter(tool -> !successfulTools.contains(tool)).isPresent()) {
  if (freshnessCorrectionUsed) {
    throw new AgentRoutingException("Agent did not call the required fresh-data tool");
  }
  String tool = requiredFreshTool.orElseThrow();
  messages.add(LlmMessage.assistant(response.content(), List.of()));
  messages.add(LlmMessage.user(FRESH_TOOL_CORRECTION.formatted(tool).strip()));
  response = client.complete(new LlmRequest(messages, definitionFor(definitions, tool)));
  freshnessCorrectionUsed = true;
  continue;
}
```

Record `successfulTools.add(call.name())` only for non-error results. If the required tool returns an error, throw immediately rather than entering the existing no-tools finalization path. Add `private static List<JsonObject> definitionFor(List<JsonObject> definitions, String toolName) throws AgentRoutingException`; it must fail if the required tool is not exposed and must return exactly that function definition.

Create `router-fresh-tool-correction.txt` with:

```text
The newest request requires fresh data from the `%s` tool in this invocation. Prior conversation
memory and prior summaries do not satisfy this requirement. Call that tool now with arguments
resolved from the newest request and shared context. Do not answer the user before the tool result.
```

- [ ] **Step 4: Update pre-existing router tests affected by the new contract**

Tests whose prompts intentionally say `tell me about <nick>` or `who is <nick>` must either script a real history tool call or use a non-history prompt when history retrieval is irrelevant to the behavior under test. Keep `requiresUserHistoryToolWhenCompletionClaimsItFetchedAUsersHistory` as coverage for narrated-action correction.

- [ ] **Step 5: Run router and policy tests and verify green**

Run: `./mvnw -q -Dtest=AgentFreshnessPolicyTest,DefaultAgentRouterTest test`

Expected: PASS, including stale-memory, failed-tool, and ordinary repeated-prompt cases.

### Task 3: Expose and Require History Evidence

**Files:**
- Modify: `src/main/java/org/saturn/app/agent/tool/UserMessageHistoryTool.java`
- Modify: `src/main/resources/agent/system-policy.txt`
- Modify: `src/main/resources/agent/tool-copy.json`
- Modify: `src/test/java/org/saturn/app/agent/tool/SaturnAgentToolsTest.java`
- Modify: `src/test/java/org/saturn/app/agent/AgentSystemPromptTest.java`

**Interfaces:**
- Consumes: repository result containing a `rows` JSON array where each row may contain numeric `createdOn`.
- Produces: the same rows plus `returnedCount`, `newestCreatedOn`, and `oldestCreatedOn`; empty results expose count `0` and JSON null timestamps.

- [ ] **Step 1: Write failing metadata and prompt tests**

```java
@Test
void userMessageHistoryReportsReturnedEvidenceRange() {
  AgentQueryRepository repository = (name, arguments, context) -> {
    JsonArray rows = new JsonArray();
    JsonObject older = new JsonObject();
    older.addProperty("message", "older");
    older.addProperty("createdOn", 100L);
    JsonObject newer = new JsonObject();
    newer.addProperty("message", "newer");
    newer.addProperty("createdOn", 300L);
    rows.add(older);
    rows.add(newer);
    JsonObject result = new JsonObject();
    result.add("rows", rows);
    return result;
  };

  JsonObject arguments = new JsonObject();
  arguments.addProperty("nick", "jill");

  JsonObject content = JsonParser.parseString(
      new UserMessageHistoryTool(repository).execute(context(), arguments).content())
      .getAsJsonObject();

  assertEquals(2, content.get("returnedCount").getAsInt());
  assertEquals(100L, content.get("oldestCreatedOn").getAsLong());
  assertEquals(300L, content.get("newestCreatedOn").getAsLong());
}
```

Add empty and single-row assertions. Extend `AgentSystemPromptTest` to require wording that every named-user analysis refreshes the tool, states `returnedCount` and the oldest/newest range, and synthesizes the complete result rather than a handful of rows.

- [ ] **Step 2: Run tool and prompt tests and verify red**

Run: `./mvnw -q -Dtest=SaturnAgentToolsTest,AgentSystemPromptTest test`

Expected: metadata fields and new prompt contract assertions fail.

- [ ] **Step 3: Enrich the model-visible history result**

After `repository.execute("recent_messages_for_user", queryArguments, context)`, deep-copy its `JsonObject`, count `rows`, and scan valid numeric `createdOn` values for minimum and maximum. Always add all three metadata fields, using `JsonNull.INSTANCE` when no timestamp exists. Preserve every row unchanged.

- [ ] **Step 4: Strengthen resource-based instructions**

Add to `system-policy.txt` that prior memory never satisfies named-user freshness, every such request must call `user_message_history` again, and the final synthesis must state `returnedCount` plus oldest/newest time range. Require analysis across the complete returned result, representative recurring themes, and clear separation of observed facts from inference.

Update `tool-copy.json` so the tool result contract advertises the evidence metadata and its fresh-per-request usage.

- [ ] **Step 5: Run tool and prompt tests and verify green**

Run: `./mvnw -q -Dtest=SaturnAgentToolsTest,AgentSystemPromptTest test`

Expected: PASS for empty, one-row, and multi-row evidence.

### Task 4: Verify, Package, and Deploy

**Files:**
- Verify all modified production, resource, test, and plan files.

**Interfaces:**
- Consumes: completed freshness policy, router enforcement, and evidence contract.
- Produces: a rebuilt Saturn container with regression-tested behavior and observable freshness logs.

- [ ] **Step 1: Run formatting verification**

Run: `./mvnw -q spotless:check`

Expected: exit code `0`.

- [ ] **Step 2: Run the complete test suite outside restricted socket sandboxing**

Run: `./mvnw -q test`

Expected: all tests pass with zero failures and zero errors. The six `OpenAiCompatibleClientTest` cases require permission to bind loopback HTTP sockets.

- [ ] **Step 3: Build the deployable artifact**

Run: `./mvnw -q package`

Expected: exit code `0` and a current packaged JAR under `target/`.

- [ ] **Step 4: Check patch hygiene and worktree ownership**

Run: `git diff --check`

Expected: no whitespace errors. Review `git status --short --branch` and preserve pre-existing `MessageSchemaMigrator` and database-test changes.

- [ ] **Step 5: Rebuild Saturn**

Run: `make rebuild`

Expected: Docker image builds, the `saturn` container starts, and startup logs contain no SQLite errors.

- [ ] **Step 6: Verify the next repeated live request from logs**

For two consecutive `*l tell me about jill user` requests, verify each correlation ID logs:

Verify each correlation ID has one log line containing `Agent fresh data required` and
`tool=user_message_history`, one containing `Agent tool completed`,
`tool=user_message_history`, and `outcome=success`, and one containing
`Agent fresh data satisfied` and `tool=user_message_history`.

The room response must state the returned count and time range. With the current database snapshot, the expected count is 325; if messages arrived since the snapshot, use the fresh returned count rather than hardcoding 325.
