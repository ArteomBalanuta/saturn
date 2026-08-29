# Vaelen Autonomous Moderator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Saturn's agent use the Vaelen persona, participate through `*l`, mentions, and ambient chat, hydrate recent public messages from SQLite, respect per-user quiet requests, and autonomously react to configured spam and raid signals through Saturn moderation commands.

**Architecture:** Extend the existing agent contracts with invocation modes and silent results, then compose a resource-backed persona with trusted runtime context and bounded database history. An engine-scoped room automation component receives chat/join events, delegates conversation to the existing FIFO agent service, and delegates deterministic spam/raid decisions to a moderation command executor.

**Tech Stack:** Java 23, Maven, JUnit 5, Gson, Toml4j, SQLite JDBC, Saturn handler chains and command gateway.

## Global Constraints

- Work directly on `develop`, preserving the pushed baseline commit `9de8f80`.
- Use TDD for every behavior change and Google Java Format for task-owned Java files.
- Do not add unrelated bug fixes, a new audit subsystem, or a privacy/security refactor.
- Direct `*l` and exact bot mentions always answer; ambient turns may remain silent.
- A polite quiet request suppresses only that user's ambient turns in that room for 15 minutes.
- Autonomous actions stop at captcha, warning, mute, kick, and shadow-ban.
- Permanent bans require a direct invocation from creator trip `595754`.
- Existing public-message visibility filters and bounded read-only SQL policy remain unchanged.
- Completion review reports Critical feature defects only.

---

### Task 1: Invocation, Participation, And Moderation Configuration Contracts

**Files:**
- Create: `src/main/java/org/saturn/app/agent/AgentInvocationMode.java`
- Create: `src/main/java/org/saturn/app/agent/AgentParticipationConfig.java`
- Create: `src/main/java/org/saturn/app/agent/moderation/AgentModerationConfig.java`
- Modify: `src/main/java/org/saturn/app/agent/AgentInvocation.java`
- Modify: `src/main/java/org/saturn/app/agent/AgentResult.java`
- Test: `src/test/java/org/saturn/app/agent/AgentParticipationConfigTest.java`
- Test: `src/test/java/org/saturn/app/agent/moderation/AgentModerationConfigTest.java`

**Interfaces:**
- Produces: `enum AgentInvocationMode { DIRECT, MENTION, AMBIENT }` with `requiresReply()`.
- Produces: `AgentInvocation(String requestId, AgentContext context, String prompt, AgentInvocationMode mode)` while preserving constructors that default to `DIRECT`.
- Produces: `AgentResult.reply(String, String)` and `AgentResult.silent(String)` plus `boolean shouldReply()`.
- Produces: `AgentParticipationConfig.from(Toml)` with creator trip, ambient toggle, quiet duration, context limit, and no-reply marker.
- Produces: `AgentModerationConfig.from(Toml)` with enabled toggle and all approved thresholds/windows.

- [ ] **Step 1: Write failing contract and configuration tests**

```java
@Test
void defaultsDirectInvocationsAndBuildsSilentResults() {
  AgentInvocation invocation = new AgentInvocation(context(), "hello");
  assertEquals(AgentInvocationMode.DIRECT, invocation.mode());
  assertTrue(invocation.mode().requiresReply());
  assertFalse(AgentResult.silent("id").shouldReply());
}

@Test
void loadsApprovedParticipationDefaults() {
  AgentParticipationConfig config = AgentParticipationConfig.from(new Toml());
  assertEquals("595754", config.creatorTrip());
  assertEquals(Duration.ofMinutes(15), config.quietDuration());
  assertTrue(config.ambientEnabled());
}
```

- [ ] **Step 2: Run focused tests and confirm missing-type failures**

Run: `./mvnw -Dtest=AgentParticipationConfigTest,AgentModerationConfigTest test`

Expected: compilation fails because the new contracts do not exist.

- [ ] **Step 3: Implement immutable records and strict positive validation**

```java
public enum AgentInvocationMode {
  DIRECT(true), MENTION(true), AMBIENT(false);
  private final boolean requiresReply;
  public boolean requiresReply() { return requiresReply; }
}

public record AgentResult(String correlationId, String content, boolean shouldReply) {
  public AgentResult(String correlationId, String content) { this(correlationId, content, true); }
  public static AgentResult silent(String id) { return new AgentResult(id, "", false); }
}
```

- [ ] **Step 4: Run the focused tests**

Run: `./mvnw -Dtest=AgentParticipationConfigTest,AgentModerationConfigTest test`

Expected: PASS.

### Task 2: Vaelen Prompt And Automatic SQLite Room Context

**Files:**
- Create: `src/main/resources/agent/vaelen-system-prompt.txt`
- Create: `src/main/java/org/saturn/app/agent/AgentSystemPrompt.java`
- Create: `src/main/java/org/saturn/app/agent/AgentConversationContextProvider.java`
- Create: `src/main/java/org/saturn/app/agent/persistence/RepositoryAgentConversationContextProvider.java`
- Modify: `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java`
- Modify: `src/main/java/org/saturn/app/agent/AgentRuntimeFactory.java`
- Test: `src/test/java/org/saturn/app/agent/AgentSystemPromptTest.java`
- Modify test: `src/test/java/org/saturn/app/agent/DefaultAgentRouterTest.java`
- Test: `src/test/java/org/saturn/app/agent/persistence/RepositoryAgentConversationContextProviderTest.java`

**Interfaces:**
- Consumes: `AgentInvocation.mode()` and `AgentParticipationConfig` from Task 1.
- Produces: `AgentConversationContextProvider.load(AgentContext)` returning a bounded JSON string; `none()` returns an empty string.
- Produces: `AgentSystemPrompt.render(AgentInvocation, String correlationId, String recentRoomContext)`.

- [ ] **Step 1: Write failing prompt and hydration tests**

```java
@Test
void rendersVaelenCreatorAndToolPlaybook() {
  String prompt = systemPrompt.render(creatorInvocation(), "cid", "{\"rows\":[]}");
  assertTrue(prompt.contains("Vaelen"));
  assertTrue(prompt.contains("595754"));
  assertTrue(prompt.contains("user_message_history"));
  assertTrue(prompt.contains("database_schema"));
}

@Test
void hydratesOnlyBoundedPublicRoomMessages() {
  String context = provider.load(agentContext("lounge"));
  assertTrue(context.contains("public text"));
  assertFalse(context.contains("whisper secret"));
  assertFalse(context.contains("legacy unknown"));
}
```

- [ ] **Step 2: Run focused tests and confirm RED**

Run: `./mvnw -Dtest=AgentSystemPromptTest,RepositoryAgentConversationContextProviderTest,DefaultAgentRouterTest test`

- [ ] **Step 3: Add the persona resource and prompt composer**

Load `/agent/vaelen-system-prompt.txt` once with UTF-8 and fail fast if it is missing. Render the persona after Saturn's operational instructions and before trusted JSON context. Include mode-specific text: ambient requests may return the configured marker; direct and mention requests must answer.

- [ ] **Step 4: Implement bounded context using the existing named query repository**

```java
public String load(AgentContext context) {
  JsonObject arguments = new JsonObject();
  arguments.addProperty("room", context.room());
  arguments.addProperty("limit", limit);
  return repository.execute("recent_messages_for_room", arguments, context).toString();
}
```

- [ ] **Step 5: Route ambient/mention requests with hydrated context and silent ambient results**

If the provider returns the no-reply marker for `AMBIENT`, return `AgentResult.silent` before memory append. Continue best-effort fallback when context loading fails. Preserve the existing tool loop and public/shared memory behavior.

- [ ] **Step 6: Run focused tests**

Run: `./mvnw -Dtest=AgentSystemPromptTest,RepositoryAgentConversationContextProviderTest,DefaultAgentRouterTest,AgentRuntimeFactoryTest test`

Expected: PASS.

### Task 3: Quiet Registry, Mention Parsing, And Engine Invocation Factory

**Files:**
- Create: `src/main/java/org/saturn/app/agent/AgentUserIdentity.java`
- Create: `src/main/java/org/saturn/app/agent/AgentQuietRegistry.java`
- Create: `src/main/java/org/saturn/app/agent/AgentMentionParser.java`
- Create: `src/main/java/org/saturn/app/agent/AgentInvocationFactory.java`
- Modify: `src/main/java/org/saturn/app/agent/AgentCapability.java`
- Modify: `src/main/java/org/saturn/app/command/impl/user/LUserCommandImpl.java`
- Test: `src/test/java/org/saturn/app/agent/AgentQuietRegistryTest.java`
- Test: `src/test/java/org/saturn/app/agent/AgentMentionParserTest.java`
- Test: `src/test/java/org/saturn/app/agent/AgentInvocationFactoryTest.java`
- Modify test: `src/test/java/org/saturn/app/command/impl/user/LUserCommandImplTest.java`

**Interfaces:**
- Produces: `AgentMentionParser.parse(String text, String botNick)` returning `Optional<String>`.
- Produces: `AgentQuietRegistry.silence(AgentContext)`, `isQuiet(AgentContext)`, and `isPoliteQuietRequest(String, String)`.
- Produces: `AgentInvocationFactory.create(EngineImpl, ChatMessage, String, AgentInvocationMode)`.
- Adds capabilities: `MODERATION_COMMANDS` and `PERMANENT_BAN` only when caller trip equals configured creator trip.

- [ ] **Step 1: Write parser, expiry, and capability tests**

```java
assertEquals("can you explain this?", parser.parse("@KoRiN, can you explain this?", "korin").orElseThrow());
assertTrue(quietRegistry.isPoliteQuietRequest("Vaelen, please be quiet", "korin"));
quietRegistry.silence(context);
assertTrue(quietRegistry.isQuiet(context));
clock.advance(Duration.ofMinutes(15));
assertFalse(quietRegistry.isQuiet(context));
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `./mvnw -Dtest=AgentQuietRegistryTest,AgentMentionParserTest,AgentInvocationFactoryTest,LUserCommandImplTest test`

- [ ] **Step 3: Implement stable identity and exact mention parsing**

Use trip, then hash, then case-normalized nick. Mention matching is case-insensitive and uses quoted regex boundaries so partial nicks do not match.

- [ ] **Step 4: Extract invocation construction from `LUserCommandImpl`**

The command remains orchestration-only and submits `DIRECT`. Creator capabilities derive from trusted `ChatMessage.trip`, never message text.

- [ ] **Step 5: Run focused tests**

Run: `./mvnw -Dtest=AgentQuietRegistryTest,AgentMentionParserTest,AgentInvocationFactoryTest,LUserCommandImplTest test`

Expected: PASS.

### Task 4: Ambient Submission And Chat Handler Integration

**Files:**
- Create: `src/main/java/org/saturn/app/agent/AgentRoomAutomation.java`
- Create: `src/main/java/org/saturn/app/agent/DefaultAgentRoomAutomation.java`
- Create: `src/main/java/org/saturn/app/listener/message/handler/AgentParticipationHandler.java`
- Modify: `src/main/java/org/saturn/app/service/impl/AgentServiceImpl.java`
- Modify: `src/main/java/org/saturn/app/facade/Base.java`
- Modify: `src/main/java/org/saturn/app/agent/AgentRuntimeFactory.java`
- Modify: `src/main/java/org/saturn/app/listener/impl/UserMessageListenerImpl.java`
- Modify test: `src/test/java/org/saturn/app/service/impl/AgentServiceImplTest.java`
- Test: `src/test/java/org/saturn/app/agent/DefaultAgentRoomAutomationTest.java`
- Test: `src/test/java/org/saturn/app/listener/message/handler/AgentParticipationHandlerTest.java`

**Interfaces:**
- Produces: `AgentRoomAutomation.onMessage(ChatMessage)` returning `CLAIMED` for a handled mention and `PASS` otherwise.
- Consumes: invocation factory, quiet registry, mention parser, and agent service from prior tasks.
- Agent service coalesces ambient work and never emits busy/error replies for ambient failures.

- [ ] **Step 1: Write failing coalescing and handler tests**

```java
@Test
void coalescesAmbientMessagesWithoutBlockingDirectAdmission() {
  service.submit(ambient("first"));
  service.submit(ambient("latest"));
  assertTrue(service.submit(direct("urgent")));
  assertEquals(List.of("first", "urgent", "latest"), routedPrompts());
}

@Test
void routesMentionsAndSuppressesQuietAuthorsOnly() {
  assertEquals(CLAIMED, automation.onMessage(message("alice", "@korin help me")));
  automation.onMessage(message("alice", "Vaelen, please be quiet"));
  assertEquals(PASS, automation.onMessage(message("alice", "an ambient thought")));
  assertTrue(submissions.stream().noneMatch(i -> i.prompt().contains("ambient thought")));
}
```

- [ ] **Step 2: Run focused tests and confirm RED**

Run: `./mvnw -Dtest=AgentServiceImplTest,DefaultAgentRoomAutomationTest,AgentParticipationHandlerTest test`

- [ ] **Step 3: Implement ambient coalescing and silent delivery**

Keep at most one pending ambient invocation per engine. New ambient messages replace the pending invocation. Direct and mention submissions continue through admitted FIFO tasks. `AgentResult.shouldReply() == false` queues and flushes nothing.

- [ ] **Step 4: Wire the automation into `Base` and the message chain**

Insert `AgentParticipationHandler` after ordinary side-effect handlers and immediately before `DispatchUserCommandHandler`. Commands pass through untouched; claimed mentions stop the chain.

- [ ] **Step 5: Run focused and listener tests**

Run: `./mvnw -Dtest=AgentServiceImplTest,DefaultAgentRoomAutomationTest,AgentParticipationHandlerTest,InfoMessageListenerImplTest test`

Expected: PASS.

### Task 5: Capability-Aware Existing Command Execution

**Files:**
- Modify: `src/main/java/org/saturn/app/agent/tool/RunCommandTool.java`
- Modify: `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java`
- Modify test: `src/test/java/org/saturn/app/agent/tool/SaturnAgentToolsTest.java`
- Modify test: `src/test/java/org/saturn/app/agent/DefaultAgentRouterTest.java`

**Interfaces:**
- Consumes: `MODERATION_COMMANDS` and `PERMANENT_BAN` capabilities from Task 3.
- Produces informational command definitions for all users, captcha/mute/kick/shadowban for moderation-capable creator calls, and ban only with `PERMANENT_BAN`.

- [ ] **Step 1: Write failing tool-definition and execution tests**

```java
assertFalse(commandEnum(regular).contains("kick"));
assertTrue(commandEnum(moderator).contains("kick"));
assertTrue(commandEnum(creator).contains("ban"));
assertFalse(commandEnum(autonomousModerator).contains("ban"));
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `./mvnw -Dtest=SaturnAgentToolsTest,DefaultAgentRouterTest test`

- [ ] **Step 3: Build command enums per context and validate against the same set at execution**

Never publish a command in JSON schema that execution rejects. Keep recursive `l`, unban-all, shutdown, SQL command text, and unrelated admin commands unavailable.

- [ ] **Step 4: Run focused tests**

Run: `./mvnw -Dtest=SaturnAgentToolsTest,DefaultAgentRouterTest test`

Expected: PASS.

### Task 6: Deterministic Spam And Raid Moderation

**Files:**
- Create: `src/main/java/org/saturn/app/agent/moderation/ModerationAction.java`
- Create: `src/main/java/org/saturn/app/agent/moderation/ModerationDecision.java`
- Create: `src/main/java/org/saturn/app/agent/moderation/RoomModerationMonitor.java`
- Create: `src/main/java/org/saturn/app/agent/moderation/ModerationActionExecutor.java`
- Create: `src/main/java/org/saturn/app/agent/moderation/EngineModerationActionExecutor.java`
- Modify: `src/main/java/org/saturn/app/agent/DefaultAgentRoomAutomation.java`
- Modify: `src/main/java/org/saturn/app/agent/AgentRuntimeFactory.java`
- Modify: `src/main/java/org/saturn/app/listener/impl/UserJoinedListenerImpl.java`
- Test: `src/test/java/org/saturn/app/agent/moderation/RoomModerationMonitorTest.java`
- Test: `src/test/java/org/saturn/app/agent/moderation/EngineModerationActionExecutorTest.java`
- Modify test: `src/test/java/org/saturn/app/agent/DefaultAgentRoomAutomationTest.java`

**Interfaces:**
- Produces: `RoomModerationMonitor.onMessage(ChatMessage)` and `onJoin(User)` returning immutable decision lists.
- Produces actions: `WARN`, `CAPTCHA_ON`, `MUTE`, `KICK`, `SHADOWBAN`; there is no autonomous `BAN` action.
- Consumes: existing `SaturnCommandGateway` for captcha/mute/kick/shadowban and `OutService` for warning text.

- [ ] **Step 1: Write failing escalation and raid tests with a mutable clock**

```java
sendMessages("spammer", 6, Duration.ofSeconds(5));
assertEquals(WARN, decisions.getLast().action());
repeatSameMessage("spammer", 4, Duration.ofSeconds(10));
assertEquals(MUTE, decisions.getLast().action());
triggerSecondBreach("spammer", Duration.ofSeconds(30));
assertEquals(KICK, decisions.getLast().action());
triggerPostKickBreach("spammer", Duration.ofMinutes(10));
assertEquals(SHADOWBAN, decisions.getLast().action());

joinUsers(8, Duration.ofSeconds(10));
assertEquals(CAPTCHA_ON, decisions.getLast().action());

joinSimilarNames(List.of("raid001", "raid002", "raid003", "raid004", "raid005"));
assertEquals(CAPTCHA_ON, decisions.getLast().action());
assertTrue(decisions.stream().noneMatch(d -> d.action() == SHADOWBAN));
```

- [ ] **Step 2: Run moderation tests and confirm RED**

Run: `./mvnw -Dtest=RoomModerationMonitorTest,EngineModerationActionExecutorTest test`

- [ ] **Step 3: Implement bounded sliding windows, escalation state, and cooldowns**

Prune deques on every event. Normalize repeated text with lowercase and collapsed whitespace. Key identity by trip, then hash, then nick. Normalize suspicious nick clusters by removing separators and trailing digits; a cluster can enable captcha but cannot shadow-ban without repeat-offence or same-hash evidence. Exclude host, replicas, and configured admin trips before adding target-specific state.

- [ ] **Step 4: Map decisions to existing commands**

```java
return switch (decision.action()) {
  case WARN -> warn(decision.target(), decision.reason());
  case CAPTCHA_ON -> gateway.execute(botContext, "captcha", "on");
  case MUTE -> gateway.execute(botContext, "mute", decision.target());
  case KICK -> gateway.execute(botContext, "kick", decision.target());
  case SHADOWBAN -> gateway.execute(botContext, "shadowban", decision.target());
};
```

- [ ] **Step 5: Feed public messages and joins through automation**

Message moderation runs before ambient quiet/mention decisions. `UserJoinedListenerImpl` calls automation only after the user is added to current room state. A command execution failure is logged and does not produce a stronger fallback action.

- [ ] **Step 6: Run focused tests**

Run: `./mvnw -Dtest=RoomModerationMonitorTest,EngineModerationActionExecutorTest,DefaultAgentRoomAutomationTest test`

Expected: PASS.

### Task 7: Configuration, Documentation, Verification, And Deployment

**Files:**
- Modify: `config.example.toml`
- Modify: `README.md`
- Modify: `src/test/java/org/saturn/app/agent/AgentRuntimeFactoryTest.java`

**Interfaces:**
- Documents every new `[agent]` setting and the exact autonomous action ceiling.
- Verifies factory wiring for host and replica engines without an external provider call.

- [ ] **Step 1: Add documented configuration defaults and factory coverage**

Use the exact defaults from the approved design and explain that captcha remains on until an authorized command disables it.

- [ ] **Step 2: Format only task-owned Java files**

Run Google Java Format 1.24.0 in replacement mode against files changed after commit `9de8f80`.

- [ ] **Step 3: Run focused feature tests**

Run: `./mvnw -Dtest='Agent*Test,DefaultAgentRouterTest,RepositoryAgentConversationContextProviderTest,SaturnAgentToolsTest,RoomModerationMonitorTest,EngineModerationActionExecutorTest,AgentParticipationHandlerTest,LUserCommandImplTest' test`

- [ ] **Step 4: Run full verification**

Run: `./mvnw verify`

Expected: every test passes and the shaded JAR builds.

- [ ] **Step 5: Validate source hygiene**

Run: `git diff --check`

Expected: no output and exit code 0.

- [ ] **Step 6: Perform a Critical-only feature review**

Review only crashes, destructive action escalation beyond the approved ceiling, command/mention loops, direct-request starvation, and inability to load/execute the feature. Fix Critical findings; report lesser findings without expanding scope.

- [ ] **Step 7: Rebuild and inspect the container**

Run: `make rebuild`, verify the container is running, and inspect logs for startup, agent, moderation, SQLite, and reply-flush errors. Do not open the host SQLite database while the container is running.
