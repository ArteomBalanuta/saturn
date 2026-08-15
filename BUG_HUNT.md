# Agentic Package Functional Bug Hunt

Static analysis scope: agent routing, SDK contracts, context and memory propagation, tool handling,
provider response parsing, and agent-driven moderation. Core non-agent application behavior,
security findings, and race/concurrency findings are excluded.

Method: Obra Superpowers-style boundary tracing from invocation contract to router decision, tool
execution, provider response, and persisted state, cross-checked against the existing unit tests.

## Implementation Ledger

Each finding is tracked with an implementation action and regression target. Statuses will be
updated as fixes are implemented and verified.

- BUG-001: Add an aggregate request budget and trim lower-priority context before provider calls. Regression: oversized memory, room context, and history-result routing tests. Status: [FIXED]; `DefaultAgentRouter` caps room context and trims history to the aggregate request budget.
- BUG-002: Make freshness target-aware and reject history calls for a different nick. Regression: mismatched `user_message_history` arguments. Status: [FIXED]; `AgentFreshnessPolicy` extracts the requested nick and `DefaultAgentRouter` binds the mandatory lookup to it.
- BUG-003: Expand named-user detection for offline users while retaining concept exclusions. Regression: `who is nex` and `tell me about jill` without room presence. Status: [FIXED]; offline nick-like profile requests now require history while documented concept exclusions stay on normal routing.
- BUG-004: Ground fresh-profile answers without exposing internal evidence formatting as a user-facing requirement. Regression: stale pre-lookup profile summary. Status: [FIXED]; the router owns the fresh lookup, rejects reuse of a prior summary, and supplies structured evidence to the model without requiring timestamp/count echoing.
- BUG-005: Persist tool calls/results as internal conversation evidence. Regression: tool result followed by an elliptical question. Status: [FIXED]; successful tool results are retained in a dedicated expiring `agent_tool_memory` table and rehydrated as internal assistant evidence without polluting user-facing assistant history.
- BUG-006: Return command execution output through the gateway. Regression: agent-issued `weather` or `ping` receives its actual result. Status: [FIXED]; a command-scoped output collector returns synchronous chat output to the model without removing it from Saturn's delivery queues.
- BUG-007: Flush semantic moderation actions without turning silent results into replies. Regression: moderation side effect is visible immediately. Status: [FIXED]; `AgentServiceImpl` flushes side effects while preserving silent results.
- BUG-008: Apply mode-specific tool exposure and execution policy. Regression: ambient moderation cannot call unrelated tools. Status: [FIXED]; moderation exposes and executes only `run_command`.
- BUG-009: Bind semantic moderation actions to the triggering author. Regression: a model-selected second target is rejected. Status: [FIXED]; `AgentContext.moderationTarget` is populated by semantic moderation and checked by `RunCommandTool`.
- BUG-010: Validate every tool call against its published JSON schema. Regression: missing, unknown, wrong-typed, out-of-range, and invalid-enum parameters. Status: [FIXED]; `AgentToolSchemaValidator` now enforces closed properties, primitive types, enum values, numeric bounds, and string-length bounds.
- BUG-011: Reject descriptor/tool name drift. Regression: mismatched SDK registration fails deterministically. Status: [FIXED]; `AgentToolRegistry` validates canonical tool and descriptor names before publication/execution.
- BUG-012: Use descriptor metadata as the single prerequisite contract. Regression: descriptor prerequisites are enforced. Status: [FIXED]; `AgentToolExecutor` reads `requiredSuccessfulTools` from the descriptor.
- BUG-013: Require an object-schema root. Regression: array and scalar roots. Status: [FIXED]; `AgentToolSchemaValidator.validateSchema` rejects non-object root schemas.
- BUG-014: Keep retryable tools available after an all-error batch. Regression: transient failure followed by a valid retry. Status: [FIXED]; the router no longer globally disables tools after one all-error batch.
- BUG-015: Record duplicate-call keys only after successful execution. Regression: failed call retried with identical arguments. Status: [FIXED]; `AgentToolExecutor` records invocation keys only after a successful result.
- BUG-016: Treat provider length termination as incomplete. Regression: `finish_reason=length`. Status: [FIXED]; `DefaultAgentRouter` rejects incomplete provider responses.
- BUG-017: Re-run the unverified-action guard after every model response. Regression: post-tool prose claiming an unexecuted action. Status: [FIXED]; the guard resets after tool turns while leaving command-shaped output to the command guard.
- BUG-018: Compare duplicate answers using the raw user prompt. Regression: same answer requested by different users. Status: [FIXED]; comparison normalizes both prompts to their user-authored body.
- BUG-019: Supply room context chronologically while preserving the newest-message limit. Regression: follow-up references resolve in order. Status: [FIXED]; `RepositoryAgentConversationContextProvider` orders context oldest-to-newest.
- BUG-020: Exclude the current inbound message from room context. Regression: current prompt appears once. Status: [FIXED]; `AgentInvocation` carries the exact inbound text and the context adapter removes only the newest matching public row.
- BUG-021: Narrow persona cleanup to boilerplate markers. Regression: legitimate evidence phrases remain intact. Status: [FIXED]; cleanup now targets stage directions and standalone boilerplate rather than ordinary evidence phrases.
- BUG-022: Detect plain-text command-shaped model output. Regression: unwrapped `weather tokyo` is corrected. Status: [FIXED]; `AgentCommandProseGuard` covers unwrapped commands while excluding narrative forms.
- BUG-023: Expose the complete reversible moderation command set. Regression: unban/unmute/unshadowban are discoverable. Status: [FIXED]; `RunCommandTool` publishes the reversible moderation catalog.
- BUG-024: Replace the broad bot suffix heuristic with explicit boundaries. Regression: `robot` is human while `QiuLingJinBot_88` remains a bot. Status: [FIXED]; `DefaultAgentRoomAutomation` uses explicit bot-name boundaries.
- BUG-025: Discard post-shutdown silent moderation results. Regression: closed-service moderation emits no visible reply. Status: [FIXED]; `AgentServiceImpl` applies `requiresReply` consistently during shutdown.

### Remediation Decisions

- Context budgeting uses a conservative character budget derived from `maxPromptChars` rather
  than adding a new configuration key. This preserves existing TOML compatibility while keeping
  the assembled request bounded; the newest user prompt and system message are retained first.
- Offline nick-like profile requests intentionally consult history even when the nick is absent
  from the live roster. A small non-nick vocabulary prevents ordinary concept questions from
  becoming database lookups; expanding that vocabulary is safer than trusting presence state.
- Command prose validation remains strict after tool execution. A legacy scripted router fixture
  expects fewer provider calls than the runtime correction contract now requires and remains a
  test migration item rather than a reason to remove the guard.
- Fresh profile synthesis is validated only when `user_message_history` supplied the structured
  `returnedCount`, `oldestCreatedOn`, and `newestCreatedOn` fields. This preserves SDK extension
  compatibility while making Saturn's built-in history contract enforceable.
- Tool evidence is persisted separately from normal `user` and `assistant` turns. Raw provider
  tool-role messages cannot be rehydrated safely without their original tool-call IDs, so the
  stored evidence is presented to the next turn as clearly marked internal assistant context.
- Current-message exclusion carries the exact audited inbound text through `AgentInvocation` and
  removes the last matching `name`/`message` row from the chronological room-context result. This
  preserves earlier identical messages while preventing the triggering event from appearing twice.
- Command output is captured at the enqueue boundary with a thread-local command scope rather
  than by reading shared queues. The original queued payload remains available for normal room
  delivery, and unrelated activity from other threads cannot enter the model result.

### [FIXED][BUG-001] Aggregate Agent Context Has No Input Budget
- Subsystem / Module: State Orchestration
- Affected File / Function: `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java:route/routeInSession`, `src/main/java/org/saturn/app/agent/tool/UserMessageHistoryTool.java:execute`
- Severity: Critical
- Impact: A valid short request can become an oversized provider request after shared memory, recent room messages, tool definitions, or a 500-message history result is appended. The provider can reject the request for context length, causing the user-facing fallback "The agent could not answer that request."
- Steps to Reproduce / Trigger:
  1. Populate agent memory with the configured 30 turns of large prompts and replies, or populate 500 public messages for one nick.
  2. Ask for that user's profile so `user_message_history` returns the full 500-row result.
  3. Observe the next `LlmRequest`: it contains the entire system prompt, memory, room context, definitions, and tool result without an aggregate character/token check.
  4. Use a provider whose context window is smaller than the assembled request.
- Expected vs. Actual Behavior:
  - Expected: The router should budget, trim, summarize, paginate, or reject context before sending a request that exceeds the provider window.
  - Actual: Only `invocation.prompt()` is checked against `maxPromptChars`; every other context source is appended without a total request bound.
- Technical Context / Code Pointers: `DefaultAgentRouter.java:85-87` checks only the newest prompt, while `DefaultAgentRouter.java:104-115`, `DefaultAgentRouter.java:229-236`, `SqliteAgentQueryRepository.java:135-149`, and `UserMessageHistoryTool.java:23` can add large unbounded payloads to subsequent completions.

### [FIXED][BUG-002] Freshness Gate Accepts History for the Wrong User
- Subsystem / Module: Router
- Affected File / Function: `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java:routeInSession/callsExactly`, `src/main/java/org/saturn/app/agent/AgentFreshnessPolicy.java:requiredTool`
- Severity: High
- Impact: A profile request for one user can be answered from another user's messages while still passing the router's mandatory fresh-data gate.
- Steps to Reproduce / Trigger:
  1. Route the prompt `tell me about jill user`.
  2. Have the model call `user_message_history` successfully with `{"nick":"nex"}`.
  3. Have the model synthesize a response about Nex.
  4. Observe that the router records `user_message_history` as successful and returns the response.
- Expected vs. Actual Behavior:
  - Expected: The required tool call should be bound to the target extracted from the newest request, and mismatched arguments should be rejected or corrected.
  - Actual: Freshness is represented only by the tool name; neither `callsExactly` nor `successfulTools` validates the requested nick.
- Technical Context / Code Pointers: `AgentFreshnessPolicy.java:70-80` returns only `user_message_history`; `DefaultAgentRouter.java:132-153`, `DefaultAgentRouter.java:216-224`, and `DefaultAgentRouter.java:478-490` compare only tool names.

### [FIXED][BUG-003] Common Offline-User Questions Bypass Mandatory History Lookup
- Subsystem / Module: Router
- Affected File / Function: `src/main/java/org/saturn/app/agent/AgentFreshnessPolicy.java:requiresNamedUserHistory/matchesTrustedRoomUser`
- Severity: High
- Impact: Questions such as `who is nex` or `tell me about jill` can receive a stale or invented profile whenever the named user is not in the current room snapshot.
- Steps to Reproduce / Trigger:
  1. Build an `AgentContext` whose `roomUsers` does not contain `nex`.
  2. Route `who is nex` without an `@` prefix and without the trailing word `user`.
  3. Return a prose answer from the model without any tool call.
  4. Observe that the router accepts the answer because no fresh tool was required.
- Expected vs. Actual Behavior:
  - Expected: A request that is syntactically a named-user profile/history request should require `user_message_history`, regardless of current presence.
  - Actual: Several common patterns require the target to be mentioned with `@` or already present in `roomUsers`.
- Technical Context / Code Pointers: `AgentFreshnessPolicy.java:84-93` delegates simple profile, who-is, speech, and history forms to `matchesTrustedRoomUser`; `AgentFreshnessPolicy.java:104-117` returns false for an offline unprefixed nick.

### [FIXED][BUG-004] Fresh-History Answers Are Not Validated Against the Profile Contract
- Subsystem / Module: Contracts
- Affected File / Function: `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java:routeInSession/requireFreshSynthesis`
- Severity: High
- Impact: Even after 500 messages are fetched, the final answer can ignore the evidence, omit the returned count and time range, or summarize only a tiny subset while still being accepted as grounded.
- Steps to Reproduce / Trigger:
  1. Make `user_message_history` return hundreds of rows plus `returnedCount`, `oldestCreatedOn`, and `newestCreatedOn`.
  2. Return `Jill is fairly active.` as the model's final answer.
  3. Ensure that exact sentence was not the immediately preceding assistant response.
  4. Observe that the router persists and returns it.
- Expected vs. Actual Behavior:
  - Expected: The response should satisfy the declared history contract by reporting evidence metadata and grounding the synthesis in the returned result.
  - Actual: The router checks only for tool success and exact reuse of the previous assistant string.
- Technical Context / Code Pointers: The contract is stated in `src/main/resources/agent/system-policy.txt:59-65` and `router-fresh-synthesis-correction.txt:1-4`; enforcement in `DefaultAgentRouter.java:156-165` and `DefaultAgentRouter.java:492-511` performs no metadata or grounding validation.
- Fix Decision: `DefaultAgentRouter` performs the required `user_message_history` call itself when the target nick is unambiguous, supplies its structured result to the model, and rejects direct reuse of a pre-lookup answer. The model is not forced to expose internal timestamps or row counts in ordinary room replies; that requirement caused valid grounded responses to fail.

### [FIXED][BUG-005] Tool Calls and Tool Results Are Lost Between Agent Turns
- Subsystem / Module: State Orchestration
- Affected File / Function: `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java:persist`, `src/main/java/org/saturn/app/agent/AgentMemoryStore.java`, `src/main/java/org/saturn/app/agent/persistence/SqliteAgentMemoryStore.java`
- Severity: High
- Impact: Follow-up requests cannot reliably refer to prior database rows, room rosters, schema details, or tool arguments because only the user prompt and final assistant prose survive into the next turn.
- Steps to Reproduce / Trigger:
  1. Ask a question that calls `room_users` or `database_query` and returns structured data.
  2. Have the assistant give a short summary that omits some returned fields.
  3. On the next turn, ask about one of the omitted fields using a reference such as `the second one`.
  4. Inspect loaded memory and observe that neither the tool call nor its result is present.
- Expected vs. Actual Behavior:
  - Expected: The shared session should persist enough of the agent trace to resolve references to prior tool outputs, or explicitly re-run the source tool.
  - Actual: Persistence stores exactly one `user` row and one `assistant` row; tool messages are transient to one `routeInSession` call.
- Technical Context / Code Pointers: `DefaultAgentRouter.java:199-236` holds tool traffic only in the local `messages` list, then `DefaultAgentRouter.java:253` persists only final prose. `AgentMemoryStore.java:7-10` and `SqliteAgentMemoryStore.java:67-85` have no tool-event contract despite `vaelen-system-prompt.txt:10` instructing use of prior tool outputs.
- Fix Decision: Added `AgentMemoryStore.appendToolEvidence`, SQLite-backed `agent_tool_memory`, fresh-schema support, and the dated idempotent migration. The router persists successful results only after the final assistant turn has been stored; loading appends clearly marked internal evidence in chronological order. This avoids creating invalid OpenAI `tool` messages without their original tool-call identifiers.

### [FIXED][BUG-006] `run_command` Discards the Actual Command Output Needed by the Model
- Subsystem / Module: Tool Handling
- Affected File / Function: `src/main/java/org/saturn/app/agent/tool/RunCommandTool.java:execute`, `src/main/java/org/saturn/app/agent/tool/SaturnCommandGateway.java`
- Severity: High
- Impact: The model cannot reason over weather, ping, help, or other command output. Compound requests such as `check Tokyo weather and tell me whether I need an umbrella` either stop at room delivery or force the model to guess.
- Steps to Reproduce / Trigger:
  1. Ask the agent to run `weather Tokyo` and interpret the result.
  2. Let `EngineSaturnCommandGateway` execute the command successfully.
  3. Inspect the tool message returned to the model.
  4. Observe that it contains only a generic command-executed sentence, not the weather payload.
- Expected vs. Actual Behavior:
  - Expected: A tool marked `ROOM_DELIVERY_AND_MODEL_DATA` should return the command's structured/text result to the model as well as delivering it to the room.
  - Actual: `SaturnCommandGateway` returns only a boolean and `RunCommandTool` replaces all command output with a generic success message.
- Technical Context / Code Pointers: `RunCommandTool.java:79` declares `ROOM_DELIVERY_AND_MODEL_DATA`, but `RunCommandTool.java:120-125` returns only `command-executed-result.txt`; `EngineSaturnCommandGateway.java:20-29` exposes no output-bearing result contract.
- Fix Decision: `CommandOutputCapture` wraps the gateway's synchronous command execution and is
  notified by `OutService` only when that execution enqueues chat or raw output. The gateway
  returns captured chat messages as model data while leaving them on the delivery queue. Commands
  that produce only raw or asynchronous output retain the explicit delivery acknowledgement.

### [FIXED][BUG-007] Silent Semantic-Moderation Actions Are Not Flushed Immediately
- Subsystem / Module: State Orchestration
- Affected File / Function: `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java:routeInSession`, `src/main/java/org/saturn/app/service/impl/AgentServiceImpl.java:execute/reply`
- Severity: High
- Impact: An autonomous mute, captcha, kick, or other command can remain in Saturn's outgoing queue until another inbound event arrives, delaying moderation exactly when immediate action is needed.
- Steps to Reproduce / Trigger:
  1. Submit a `MODERATION` invocation whose model response calls `run_command` successfully.
  2. Let the command gateway enqueue the resulting raw/chat payload.
  3. Let the router return `AgentResult.silent` as required for moderation.
  4. Observe that `replyFlusher` is never invoked until a later incoming message causes the engine to drain its queues.
- Expected vs. Actual Behavior:
  - Expected: Successful room-delivery or moderation side effects should trigger an immediate flush even when no assistant reply is emitted.
  - Actual: The flusher runs only inside `reply`, and silent results never call `reply`.
- Technical Context / Code Pointers: `DefaultAgentRouter.java:239-243` returns a silent result for moderation; `AgentServiceImpl.java:117-119` calls `reply` only when `shouldReply` is true, while `AgentServiceImpl.java:151-156` contains the only `replyFlusher.run()` call.

### [FIXED][BUG-008] Moderation Mode Receives the Full General Tool and Command Catalog
- Subsystem / Module: Router
- Affected File / Function: `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java:definitions/routeInSession`, `src/main/java/org/saturn/app/agent/tool/RunCommandTool.java:allowedCommands`
- Severity: High
- Impact: A silent abuse review can invoke unrelated tools such as weather, help, database queries, or user history, and can choose stronger moderation actions than the mode policy requested.
- Steps to Reproduce / Trigger:
  1. Create a `MODERATION` invocation with the bot moderation context.
  2. Inspect the first `LlmRequest.tools` collection.
  3. Return a valid `run_command` call for `weather`, `kick`, `captcha`, or `shadowban` instead of the policy's narrow `mute` action.
  4. Observe that the router executes the call and then returns a silent result.
- Expected vs. Actual Behavior:
  - Expected: Moderation mode should expose a mode-specific allowlist that contains only the actions and read tools required by the moderation contract.
  - Actual: Tool definitions are filtered only by `AgentContext` capabilities; invocation mode is not considered.
- Technical Context / Code Pointers: `DefaultAgentRouter.java:115-117` builds definitions from context alone; `AgentToolRegistry.java:33-38` has no mode input. `RunCommandTool.java:21-40` and `RunCommandTool.java:128-136` expose informational and all moderation commands to the bot context, despite `participation-moderation.txt:4-9` specifying mute-or-no-action behavior.

### [FIXED][BUG-009] Semantic Moderation Does Not Bind an Action to the Triggering Author
- Subsystem / Module: Tool Handling
- Affected File / Function: `src/main/java/org/saturn/app/agent/DefaultAgentRoomAutomation.java:submitSemanticModeration`, `src/main/java/org/saturn/app/agent/tool/RunCommandTool.java:execute`
- Severity: High
- Impact: The model can mute or kick a user mentioned in an abusive message rather than the author who sent it.
- Steps to Reproduce / Trigger:
  1. Receive a severe abusive message authored by `alice` that also mentions `bob`.
  2. Let semantic moderation produce `run_command {"command":"mute","arguments":"bob"}`.
  3. Observe that the tool accepts and executes the command.
  4. Confirm that no router or tool check compares `bob` with the trusted triggering author `alice`.
- Expected vs. Actual Behavior:
  - Expected: Autonomous targeted moderation should bind the allowed target to the trusted author metadata carried by the moderation invocation.
  - Actual: The author exists only in natural-language prompt text, while `run_command.arguments` remains unrestricted within the allowed command enum.
- Technical Context / Code Pointers: `DefaultAgentRoomAutomation.java:143-156` replaces caller identity with the bot context and embeds the author in prose. `RunCommandTool.java:110-125` validates the command name but not the moderation target against invocation metadata.

### [FIXED][BUG-010] Advertised JSON Schemas Are Not Enforced at Tool Execution
- Subsystem / Module: Contracts
- Affected File / Function: `src/main/java/org/saturn/app/agent/AgentToolExecutor.java:execute/parseArguments`, `src/main/java/org/saturn/app/agent/AgentToolRegistry.java:definition`
- Severity: High
- Impact: Tool calls can violate required fields, enums, types, length/range constraints, and `additionalProperties: false`, producing inconsistent behavior between the SDK contract and runtime.
- Steps to Reproduce / Trigger:
  1. Inspect the published `room_users` schema, which rejects additional properties and caps room length at 100.
  2. Execute it through `AgentToolExecutor` with `{"unknown":true}` or an overlong room.
  3. Observe that the tool can fall back to the current room or continue rather than receiving a schema-validation error.
  4. Repeat with an out-of-range `limit`; repository code silently clamps it rather than enforcing the advertised contract.
- Expected vs. Actual Behavior:
  - Expected: Runtime validation should apply the exact schema published to the provider before calling a tool.
  - Actual: The executor only verifies that arguments parse as a JSON object, leaving partial and inconsistent validation to each tool.
- Technical Context / Code Pointers: `AgentToolRegistry.java:41-49` publishes `descriptor.parameters`; `AgentToolExecutor.java:50-56` only parses JSON. Examples of unenforced constraints appear in `RoomUsersTool.java:54-66`, `UserMessageHistoryTool.java:60-85`, and `DatabaseQueryTool.java:55-87`.

### [FIXED][BUG-011] Registry and Provider Can Use Different Names for the Same Tool
- Subsystem / Module: SDK
- Affected File / Function: `src/main/java/org/saturn/app/agent/AgentToolRegistry.java:register/find/definition`, `src/main/java/org/saturn/app/agent/AgentToolDescriptor.java`
- Severity: High
- Impact: A valid SDK extension can be advertised under one name but stored under another, making every model invocation of the advertised tool fail as unknown.
- Steps to Reproduce / Trigger:
  1. Register an `AgentTool` whose `name()` returns `internal_name`.
  2. Return an `AgentToolDescriptor` whose `name` is `public_name`.
  3. Inspect definitions and observe that the provider sees `public_name`.
  4. Execute the model's `public_name` call and observe that registry lookup fails.
- Expected vs. Actual Behavior:
  - Expected: Registration should reject a descriptor whose name differs from `AgentTool.name()`, or use one canonical name everywhere.
  - Actual: Registration/lookup use `AgentTool.name()`, while serialization uses `descriptor.name()`, with no equality validation.
- Technical Context / Code Pointers: `AgentToolRegistry.java:14-20` keys by `tool.name`, `AgentToolRegistry.java:29-30` looks up that key, and `AgentToolRegistry.java:41-49` emits `descriptor.name`. `AgentToolDescriptor.java:22-43` does not compare the two identities.

### [FIXED][BUG-012] Tool Prerequisites Have Two Divergent Sources of Truth
- Subsystem / Module: SDK
- Affected File / Function: `src/main/java/org/saturn/app/agent/AgentTool.java:requiredSuccessfulTools/descriptor`, `src/main/java/org/saturn/app/agent/AgentToolExecutor.java:execute`
- Severity: High
- Impact: A tool can advertise required predecessor tools to the model while the executor allows it to run immediately, or the executor can enforce an undeclared prerequisite that the model was never told about.
- Steps to Reproduce / Trigger:
  1. Implement a custom tool descriptor with `requiredSuccessfulTools = {"database_schema"}`.
  2. Leave the separate `AgentTool.requiredSuccessfulTools()` method at its default empty set.
  3. Call the custom tool before `database_schema`.
  4. Observe that the executor runs it despite the published contract.
- Expected vs. Actual Behavior:
  - Expected: Prerequisites should come from one canonical descriptor and be validated when the registry freezes.
  - Actual: The provider reads descriptor metadata, but the executor reads a separate interface method.
- Technical Context / Code Pointers: `AgentTool.java:30-48` defines both paths; `AgentToolExecutor.java:41-47` uses only `tool.requiredSuccessfulTools()`. Built-in `DatabaseSqlTool` manually keeps both copies aligned, but the SDK does not enforce that invariant.

### [FIXED][BUG-013] Descriptor Accepts a Non-Object Parameter Schema That the Executor Cannot Consume
- Subsystem / Module: Contracts
- Affected File / Function: `src/main/java/org/saturn/app/agent/AgentToolDescriptor.java:compact constructor`, `src/main/java/org/saturn/app/agent/AgentToolExecutor.java:parseArguments`
- Severity: Medium
- Impact: An SDK tool can pass descriptor construction and be published with `{"type":"string"}`, but any conforming provider argument is then rejected because execution always deserializes arguments into `JsonObject`.
- Steps to Reproduce / Trigger:
  1. Construct an `AgentToolDescriptor` with a `JsonObject` containing `{"type":"string"}`.
  2. Register and publish the tool definition.
  3. Have the provider supply a JSON string argument according to the schema.
  4. Observe `Invalid tool arguments` before tool execution.
- Expected vs. Actual Behavior:
  - Expected: Descriptor construction should require top-level schema type `object`, matching the executor contract.
  - Actual: The constructor checks only that a `type` member exists; `parameters.isJsonObject()` is always true because the field is already statically a `JsonObject`.
- Technical Context / Code Pointers: `AgentToolDescriptor.java:30-33` never validates the value of `type`; `AgentToolExecutor.java:86-94` always parses into `JsonObject.class`.

### [FIXED][BUG-014] A Recoverable First Tool Error Disables All Further Tool Use
- Subsystem / Module: Tool Handling
- Affected File / Function: `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java:routeInSession`
- Severity: Medium
- Impact: The model cannot correct malformed arguments or retry a transient read failure, even though per-tool failure limits explicitly allow more than one attempt.
- Steps to Reproduce / Trigger:
  1. Return one tool call with a recoverable argument error, such as an empty `room` for `room_users`.
  2. Let the tool return an error result.
  3. Observe that `allErrors` remains true.
  4. The router removes all tool definitions and asks for a final no-tool response instead of allowing corrected arguments.
- Expected vs. Actual Behavior:
  - Expected: The model should receive the error and retain the affected tool until `maxToolFailures` or `maxCallsPerTool` is reached.
  - Actual: One all-error batch immediately disables every tool for the invocation.
- Technical Context / Code Pointers: `DefaultAgentRouter.java:201-235` sets `toolsEnabled = false` after the first all-error batch, while `AgentToolExecutor.java:119-123` otherwise permits failures up to the configured threshold.

### [FIXED][BUG-015] Failed Tool Calls Cannot Be Retried with Correctly Reused Arguments
- Subsystem / Module: Tool Handling
- Affected File / Function: `src/main/java/org/saturn/app/agent/AgentToolExecutor.java:execute`
- Severity: Medium
- Impact: A transient tool failure becomes unrecoverable when the correct retry uses the same tool and arguments.
- Steps to Reproduce / Trigger:
  1. In one model response, execute one successful tool and one tool that transiently returns an error, so the router keeps tools enabled.
  2. Have the model retry the failed tool with the same arguments.
  3. Observe that the executor returns `Duplicate tool call; use the previous result` without re-executing it.
  4. The previous result is an error, so no usable result exists.
- Expected vs. Actual Behavior:
  - Expected: Duplicate suppression should reuse only successful results or permit bounded retries after failures.
  - Actual: The invocation key is recorded before execution and is retained regardless of success or failure.
- Technical Context / Code Pointers: `AgentToolExecutor.java:58-61` records/deduplicates before `AgentToolExecutor.java:71-78` knows the outcome.

### [FIXED][BUG-016] Provider `finish_reason` Is Parsed but Ignored
- Subsystem / Module: Contracts
- Affected File / Function: `src/main/java/org/saturn/app/agent/llm/OpenAiCompatibleClient.java:parse`, `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java:routeInSession`
- Severity: High
- Impact: Answers cut off by `maxCompletionTokens` are treated as complete, sanitized, persisted, and sent to users.
- Steps to Reproduce / Trigger:
  1. Return an OpenAI-compatible response with non-empty partial content and `finish_reason: "length"`.
  2. Do not include tool calls.
  3. Observe that routing succeeds and stores the partial text.
  4. Repeat with a 500-message profile request, where the configured 768-token completion cap makes truncation plausible.
- Expected vs. Actual Behavior:
  - Expected: Non-terminal or truncated finish reasons should trigger continuation, a bounded retry, or an explicit incomplete-response failure.
  - Actual: `finishReason` is retained in `LlmResponse` but never inspected by the router.
- Technical Context / Code Pointers: `OpenAiCompatibleClient.java:146-150` parses the field; `DefaultAgentRouter.java:187-254` decides completion solely from tool calls and content.

### [FIXED][BUG-017] Unverified-Action Guard Runs Only on the First Model Response
- Subsystem / Module: Router
- Affected File / Function: `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java:routeInSession/correctUnverifiedActionClaim`
- Severity: High
- Impact: After any real tool call, the model can claim that it will fetch, query, check, or execute another action without making that second tool call.
- Steps to Reproduce / Trigger:
  1. Have the initial response call `room_users` successfully.
  2. Have the next response contain `I will fetch the weather now.` with no tool call.
  3. Observe that the router exits and returns the promise as final content.
  4. Confirm no weather command was executed.
- Expected vs. Actual Behavior:
  - Expected: Every assistant response produced inside the tool loop should be checked for unverified action claims.
  - Actual: `unverifiedActionChecked` is set after inspecting the initial response and prevents checks on all later responses.
- Technical Context / Code Pointers: `DefaultAgentRouter.java:127`, `DefaultAgentRouter.java:167-171`, and `DefaultAgentRouter.java:236` show the one-shot flag and subsequent unguarded provider response.

### [FIXED][BUG-018] Stale-Response Detection Rejects Correct Repeated Answers from Another User
- Subsystem / Module: Router
- Affected File / Function: `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java:completeInitialRequest/isStaleDuplicate/contextualizePrompt`
- Severity: High
- Impact: In shared room memory, two users asking the same deterministic question can make the second request fail after cache bypass even when the only correct answer is identical.
- Steps to Reproduce / Trigger:
  1. Persist Alice's contextualized prompt `What is 2 + 2?` and assistant answer `4`.
  2. Have Bob ask the same question in the same room.
  3. Return `4` from both the initial completion and cache-bypass retry.
  4. Observe `Agent returned a stale response after cache bypass`.
- Expected vs. Actual Behavior:
  - Expected: Identical prompts with deterministic answers should permit identical output regardless of the caller nick.
  - Actual: The contextualized prompts differ because they contain `@alice` versus `@bob`, so exact answer equality is classified as stale.
- Technical Context / Code Pointers: `DefaultAgentRouter.java:281-295` retries and fails; `DefaultAgentRouter.java:350-361` compares contextualized user strings; `DefaultAgentRouter.java:615-619` injects caller identity into those strings.

### [FIXED][BUG-019] Recent Room Context Is Supplied in Reverse Conversation Order
- Subsystem / Module: State Orchestration
- Affected File / Function: `src/main/java/org/saturn/app/agent/persistence/SqliteAgentQueryRepository.java:recentMessagesForRoom`, `src/main/java/org/saturn/app/agent/persistence/RepositoryAgentConversationContextProvider.java:load`
- Severity: Medium
- Impact: Pronouns and short approvals such as `do it`, `check him`, or `there` can bind to the wrong earlier message because the model receives newest-to-oldest rows while the prompt describes them as conversation context.
- Steps to Reproduce / Trigger:
  1. Insert three related room messages in chronological order, ending with `do it`.
  2. Load recent room context.
  3. Observe the array order: `do it` appears first, followed by the offer and then the initiating request.
  4. Ask the model to resolve the approval from that payload.
- Expected vs. Actual Behavior:
  - Expected: Conversation context should be chronological, or the contract should explicitly label ordering and require timestamp-based reconstruction.
  - Actual: SQL returns descending timestamps and the provider passes that JSON through unchanged and unlabeled.
- Technical Context / Code Pointers: `SqliteAgentQueryRepository.java:162-180` uses `ORDER BY created_on DESC, id DESC`; `RepositoryAgentConversationContextProvider.java:23-27` returns it directly. `system-policy.txt:23-29` relies on this data for follow-up resolution without declaring its order.

### [FIXED][BUG-020] The Current User Message Is Duplicated in Every Public Agent Request
- Subsystem / Module: State Orchestration
- Affected File / Function: `src/main/java/org/saturn/app/agent/persistence/RepositoryAgentConversationContextProvider.java:load`, `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java:routeInSession`
- Severity: Medium
- Impact: The same request appears once inside recent room JSON and again as the newest user message, which can overweight it, cause repeated tool planning, or confuse command text with the normalized agent prompt.
- Steps to Reproduce / Trigger:
  1. Persist a public `*l <question>` or mention in the messages table before routing it.
  2. Load room context for the invocation.
  3. Inspect the resulting `LlmRequest`.
  4. Observe the raw current chat row in `RECENT_PUBLIC_ROOM_MESSAGES_UNTRUSTED_DATA` and the same semantic request in the final `user` message.
- Expected vs. Actual Behavior:
  - Expected: Recent context should exclude the current event or mark it so the model can deduplicate it deterministically.
  - Actual: The context-provider contract has no current message ID/timestamp and always selects the latest rows, while the router independently appends the current prompt.
- Technical Context / Code Pointers: `RepositoryAgentConversationContextProvider.java:23-27` has no exclusion input; `DefaultAgentRouter.java:107-113` injects both sources. At the integration boundary, `UserMessageListenerImpl.java:35-45` audits before agent participation/command dispatch.
- Fix Decision: `AgentInvocationFactory` carries `ChatMessage.getText()` as the exact audited text. The context provider removes the last row whose persisted `name` and `message` match the trusted inbound author/text pair, so it excludes the triggering row without deleting an earlier identical message.

### [FIXED][BUG-021] Legacy Persona Cleanup Deletes Legitimate Answers and Memory
- Subsystem / Module: State Orchestration
- Affected File / Function: `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java:sanitizePersonaArtifacts/excludeLegacyPersonaTurns`
- Severity: Medium
- Impact: Valid content about phrases such as `carpe diem` or `the archives reveal` can disappear, produce an empty-response failure, and remove the associated user turn from future memory.
- Steps to Reproduce / Trigger:
  1. Ask `What does carpe diem mean?`.
  2. Return the valid one-line answer `Carpe diem, translated literally, means seize the day.`.
  3. Observe that the sanitizer removes the whole line and the router reports an empty response.
  4. If an equivalent answer already exists in memory, observe that both it and its preceding user message are excluded on the next load.
- Expected vs. Actual Behavior:
  - Expected: Cleanup should remove only known legacy boilerplate structures, not semantic occurrences requested by the user.
  - Actual: Broad substring and line-prefix checks classify legitimate content as persona artifacts.
- Technical Context / Code Pointers: `DefaultAgentRouter.java:717-733` filters current output; `DefaultAgentRouter.java:736-758` drops stored turns when content contains any banned phrase; `DefaultAgentRouter.java:761-765` removes entire matching lines.

### [FIXED][BUG-022] Plain-Text Saturn Commands Bypass the Command-Execution Guard
- Subsystem / Module: Router
- Affected File / Function: `src/main/java/org/saturn/app/agent/AgentCommandProseGuard.java:findCommand`
- Severity: Medium
- Impact: The agent can print `weather charlotte`, `*kick user`, or another command as prose instead of invoking `run_command`, recreating the misleading command-wrapper behavior the guard is intended to prevent.
- Steps to Reproduce / Trigger:
  1. Have the model answer with a bare line such as `weather charlotte` and no tool call.
  2. Route the response through `enforceCommandChannel`.
  3. Observe that no command is detected and no correction is requested.
  4. The bare command text is sent to the room without execution.
- Expected vs. Actual Behavior:
  - Expected: Standalone allowed command syntax should be detected regardless of Markdown quoting style.
  - Actual: The guard searches only backtick/tilde fenced blocks and inline backticks.
- Technical Context / Code Pointers: `AgentCommandProseGuard.java:19-23` defines only fenced/inline patterns and `AgentCommandProseGuard.java:55-64` searches only those patterns, despite `system-policy.txt:36-37` also prohibiting printed or quoted commands.

### [FIXED][BUG-023] Agent Moderation Catalog Cannot Reverse Actions It Can Apply
- Subsystem / Module: SDK
- Affected File / Function: `src/main/java/org/saturn/app/agent/tool/RunCommandTool.java:MODERATION_COMMANDS/allowedCommands`
- Severity: Medium
- Impact: A trusted moderator can ask the agent to mute, shadowban, or permanently ban, but cannot ask it to unmute, unshadowban, or unban through the same SDK even though Saturn implements those commands.
- Steps to Reproduce / Trigger:
  1. Build a direct moderator context with `MODERATION_COMMANDS`.
  2. Ask the agent to `unmute alice` or `unshadowban alice`.
  3. Inspect the `run_command.command` enum and observe that the requested command is absent.
  4. Any attempted call is rejected as not approved.
- Expected vs. Actual Behavior:
  - Expected: The direct moderation tool surface should include the supported inverse operations needed to recover from or revoke moderation actions.
  - Actual: The hardcoded catalog exposes only `captcha`, `mute`, `kick`, and `shadowban`, plus creator-only `ban`.
- Technical Context / Code Pointers: `RunCommandTool.java:38-40` defines the fixed set; existing command implementations include `UnMuteUserCommandImpl`, `UnShadowBanUserCommandImpl`, and `UnBanUserCommandImpl`, but they cannot be represented by the agent contract.

### [FIXED][BUG-024] Human Users Whose Nick Ends in `bot` Are Ignored as Automated Accounts
- Subsystem / Module: Router
- Affected File / Function: `src/main/java/org/saturn/app/agent/DefaultAgentRoomAutomation.java:isBotAuthor/onMessage`
- Severity: Medium
- Impact: Users named `robot`, `abbot`, or similar cannot mention the agent, participate in ambient routing, or receive semantic moderation processing.
- Steps to Reproduce / Trigger:
  1. Send `@saturn answer this` from a normal unflagged user named `robot`.
  2. Ensure the room user record does not mark that account as a bot.
  3. Invoke `DefaultAgentRoomAutomation.onMessage`.
  4. Observe `Outcome.PASS` with no agent submission.
- Expected vs. Actual Behavior:
  - Expected: Conventional bot-name detection should match an intentional bot suffix/token, not arbitrary words ending in the letters `bot`.
  - Actual: The unanchored pattern `bot(?:[_-]?\\d+)?$` matches any nick ending in `bot`.
- Technical Context / Code Pointers: `DefaultAgentRoomAutomation.java:18` defines the pattern; `DefaultAgentRoomAutomation.java:101-107` exits before mention parsing; `DefaultAgentRoomAutomation.java:175-179` applies the broad suffix match.

### [FIXED][BUG-025] Post-Shutdown Moderation Submission Can Emit a Visible Bot Reply
- Subsystem / Module: State Orchestration
- Affected File / Function: `src/main/java/org/saturn/app/service/impl/AgentServiceImpl.java:submit`
- Severity: Medium
- Impact: A mode whose contract requires complete silence can post `The agent is unavailable because Saturn is shutting down.` to the room, addressed to the bot itself.
- Steps to Reproduce / Trigger:
  1. Close `AgentServiceImpl` while a room automation instance can still submit work.
  2. Submit a `MODERATION` invocation using the bot context.
  3. Inspect the outgoing queue.
  4. Observe a visible reply even though `MODERATION.requiresReply()` is false.
- Expected vs. Actual Behavior:
  - Expected: Pre-routing failures should use the same `requiresReply` rule as routing failures and remain silent for moderation/ambient modes.
  - Actual: The closed-service path calls `reply` unconditionally for every non-ambient mode.
- Technical Context / Code Pointers: `AgentServiceImpl.java:43-52` special-cases only `AMBIENT` before replying on shutdown; `AgentServiceImpl.java:145-148` has the correct `replyIfRequired` helper but does not use it in that path.
