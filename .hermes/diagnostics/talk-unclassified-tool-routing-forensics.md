# TALK / UNCLASSIFIED / tool-routing forensics (Phase 0)

## Scope and current baseline

This is an investigation only. No production or test source was changed. The working tree was already dirty before this report; the current task-owned changes at `HEAD=428fa3b` add `AgentInvocation.commandOriginated`, factory propagation, the `l` command's `true` flag, and the router's command-origin quote-only bypass.

The current runtime has invocation modes `DIRECT`, `MENTION`, `AMBIENT`, and `MODERATION`; there is no `TALK` or `UNCLASSIFIED` enum/value. `AgentInvocationMode.requiresReply()` is only a participation/reply policy (`AgentInvocationMode.java:4-18`), not a semantic request classifier.

## End-to-end data flow

### 1. Inbound message and invocation creation

* `AgentRoomMessagePipeline.onMessage` runs an ordered handler chain (`AgentRoomMessagePipeline.java:81-89`). `filterIneligible` drops empty text, whispers, bot-authored messages, self-authored messages, and prefix commands before ordinary room routing (`:100-108`). This means ambient/mention classification does not see whispers or explicit prefix commands.
* `prepareInvocation` parses a mention, choosing `MENTION` when a mention is present and `AMBIENT` otherwise, then uses the original text or parsed mention prompt (`:111-117`).
* A mention is submitted immediately (`:126-130`). Ambient is submitted only when enabled, not quiet, and the configured message cadence is reached (`:154-161`). Semantic moderation creates a separate `MODERATION` invocation with a moderation-specific prompt and bot context (`:132-151`).
* The explicit `l` command bypasses the room pipeline. `LUserCommandImpl.execute` renders the command arguments, creates a `DIRECT` invocation, and passes `commandOriginated=true` (`LUserCommandImpl.java:31-47`).
* `AgentInvocationFactory.create` derives trusted room/user metadata and capabilities from the `EngineImpl` and `ChatMessage`, captures `message.getText()` as `currentMessageText`, and carries the command-origin bit into the immutable invocation (`AgentInvocationFactory.java:24-34`, `:37-71`).
* `AgentInvocation` currently carries request id, `AgentContext`, prompt, mode, current message text, and command origin (`AgentInvocation.java:7-23`). `AgentContext` carries room, nick/trip/hash, whisper privacy, room-user snapshot, capabilities, and optional moderation target (`AgentContext.java:7-16`). Its `memoryKey` separates public history from whisper history and identities (`:46-60`).

### 2. Service submission and execution

`AgentServiceImpl.submit` treats ambient specially (coalesced/scheduled), rejects disabled/closed/busy work, and queues all other invocations on a virtual-thread executor (`AgentServiceImpl.java:40-69`, `:71-103`). `execute` calls `router.route`; successful replies are sent to `context.nick()` with `context.whisper()` privacy and flushed (`:105-123`, `:161-173`). Failures become the fixed required-reply failure text for `DIRECT`/`MENTION`; ambient/moderation do not necessarily reply (`:124-159`). No semantic TALK/UNCLASSIFIED value is currently logged, persisted, or transported at this boundary.

### 3. Memory, room context, prompt assembly, and tool definitions

`DefaultAgentRouter.route` enforces the prompt bound and serializes the session by `context.memoryKey()` (`DefaultAgentRouter.java:127-134`). `routeInSession` then:

1. loads persisted history (`:136-140`);
2. loads recent public room context unless this is a whisper; failures are logged and converted to empty context (`:302-316`);
3. calls `AgentRequestAssembler.assemble` (`:139-148`).

`AgentTurnMemory.load` obtains history from `AgentMemoryStore`, filters legacy persona turns, and logs the resulting count (`AgentTurnMemory.java:25-43`). `AgentRequestAssembler.retainHistory` excludes internal tool-evidence entries unless their tool is still registered with `MODEL_DATA` result mode (`AgentRequestAssembler.java:79-87`). Thus persisted user/assistant history and eligible tool evidence can influence the next request, but internal room-delivery/error evidence is intentionally not retained as ordinary conversational history.

`AgentRequestAssembler.assemble` computes fresh-data requirements from the prompt, history, and room users (`:33-45`), contextualizes the user prompt with visibility, nick, room, and prompt (`:90-94`), then builds `[system prompt, retained history, current user prompt]` and trims to budget (`:45-61`, `:96-113`). Definitions are filtered by mode and `AgentCommandIntentPolicy`: non-moderation `saturn_*` command tools are hidden unless the newest prompt explicitly starts with the command alias or `run/execute alias` (`:64-76`; policy `AgentCommandIntentPolicy.java:15-37`). This is a command-exposure gate, not a conversational classifier.

`AgentSystemPrompt.render` includes trusted runtime metadata containing correlation id, current invocation mode, room, whisper flag, caller identity, capabilities-derived database policy, and room-user snapshot (`AgentSystemPrompt.java:22-39`). It also injects the bounded recent room context and participation policy into `system-policy.txt` (`:40-70`). `system-policy.txt` currently tells the model that non-command prose should emit one attributed quote (`system-policy.txt:43-47`), while executable requests use tools first. It does not carry a TALK/UNCLASSIFIED field.

### 4. Initial model response, tool calls, and evidence

The router uses `AgentResponseCorrector.completeInitialRequest` for ordinary requests, which calls the provider and retries once if the response is a stale duplicate of prior history (`DefaultAgentRouter.java:156-161`; `AgentResponseCorrector.java:52-75`). A first response with `toolCalls()` is not a final answer. The router processes fresh-data requirements and turn policies, then loops while tool calls remain (`DefaultAgentRouter.java:163-233`).

For a tool-call response, the router:

* appends the assistant/tool-call message (`DefaultAgentRouter.java:217-218`);
* executes all calls through `AgentToolExecutor`;
* records results through `AgentToolResultCoordinator.record` (`:218-228`).

`AgentToolResultCoordinator` validates call/result cardinality, records successful tool results in `AgentTurnState`, tracks successful/failed `run_command` operations, and appends model-visible tool messages (`AgentToolResultCoordinator.java:27-75`). `AgentTurnState.successfulToolResults()` is the authoritative request-local evidence list (`AgentTurnState.java:17-27`, `:101-103`, `:129-130`). It distinguishes successful tool results from failed calls; a failed tool call is not successful grounding.

The model is called again with the tool observations (`DefaultAgentRouter.java:229-232`). This repeats until a response has no tool calls. Therefore `response.toolCalls().isEmpty()` is only a final-turn fact; inspecting only the initial response or only final textual content would misclassify a tool-backed turn.

On completion, only `MODEL_DATA` successful results are persisted as internal tool evidence (`DefaultAgentRouter.java:247-252`, `:277-286`). The completed user prompt is persisted as `contextualizedPrompt`, not the raw prompt (`:248`).

### 5. Final response correction, classification-adjacent policy, and delivery

`AgentResponseFinalizer.prepare` first corrects failure placeholders and internal-evidence leaks, validates required fresh data, sanitizes output, handles moderation/no-reply behavior, optionally applies quote-only correction, then returns reply/silent (`AgentResponseFinalizer.java:34-94`).

The router currently passes this quote-only predicate:

```java
!invocation.commandOriginated() && turnState.successfulToolResults().isEmpty()
```

(`DefaultAgentRouter.java:235-243`). This is the current root policy seam. It means:

* any non-command invocation with zero successful tool results is forced through quote-only correction, regardless of whether the request is conversational or an unfulfilled/actionable request;
* any command-originated invocation bypasses quote-only correction even with no successful tools (the existing explicit behavior);
* any invocation with one or more successful tool results bypasses quote-only correction, including a conversational-looking prompt that caused a tool call;
* failed tools leave `successfulToolResults()` empty, so a non-command failed-tool turn is incorrectly eligible for quote-only correction unless an earlier required-fresh/tool policy throws.

The current finalizer does not know whether a tool call was attempted but failed except through the successful-result list supplied by the router. It also has no semantic request kind. `AgentResponseFinalizer`'s convenience overload defaults quote-only to every non-moderation mode (`:42-50`), so callers must use the explicit boolean correctly.

## What can be classified from existing data

Available, trusted or observable signals are:

* invocation origin/mode: `AgentInvocation.mode()` and `commandOriginated()`;
* raw/current message text: `currentMessageText` and `prompt`;
* normalized contextualized prompt (room/visibility/nick wrapper) from `AgentRequestAssembler`;
* retained history, including eligible `MODEL_DATA` tool evidence;
* recent public room context;
* exposed tool definitions after `AgentCommandIntentPolicy` filtering;
* actual provider response `toolCalls()` at every loop iteration;
* actual executed results and success/failure in `AgentTurnState`;
* whether a required fresh-data tool was demanded and satisfied;
* final model response after correction.

Existing signals are insufficient for a reliable semantic classification by themselves:

* `AgentInvocationMode` answers who/why the agent was invoked, not whether the request is conversational.
* Presence of tool definitions does not mean a tool was called.
* A final response with no tool calls does not prove no tools were called earlier.
* `successfulToolResults().isEmpty()` conflates no tool attempt with attempted-but-failed tools and with tool calls whose result mode is not persisted.
* Prompt-prefix command filtering only handles explicit `saturn_*` aliases and cannot classify general actionable prose.
* History/room context is untrusted data per `system-policy.txt:54-67`; it can resolve references but must not be treated as a classification instruction.

## Recommended classifier placement and propagation

Use a small routing-package classifier at the orchestration seam, not in `AgentInvocationMode`, `AgentResponseCorrector`, or `AgentResponseFinalizer`:

1. **Candidate classification before the initial provider call:** `AgentRequestAssembler` (or a sibling `AgentRequestClassifier`) can classify the newest request using trusted invocation metadata, prompt, exposed definitions, history, and room context. This is the only point that can place the candidate into the initial system message. It should produce `TALK_CANDIDATE` vs `UNCLASSIFIED_CANDIDATE`, never infer “tool-free” yet.
2. **Final classification after the bounded tool loop:** `DefaultAgentRouter` has the complete evidence and must finalize the semantic result only after the loop. It should use a dedicated immutable evidence object containing `toolCallAttempted`, `successfulToolResults`, `failedToolResults`, and final `response.toolCalls().isEmpty()`.
3. **Final response correction:** pass the final classification and the evidence object into `AgentResponseFinalizer`. Quote correction should be requested only for final `TALK` or final `UNCLASSIFIED` turns according to the truth table below, never merely because the success list is empty.
4. **LLM propagation:** add a trusted `requestClassification`/`requestKind` field to the rendered runtime metadata (the same channel used by `invocationMode`, room, whisper, caller, and room snapshot), and include the final kind in any isolated correction prompt. Do not encode it as user text. Since final kind depends on actual tool execution, the first call can receive only a candidate/constraint; the post-tool model call or final quote-correction call must receive the final evidence/kind. If the requirement means the model must know the final kind before producing prose, make a bounded post-loop synthesis/correction call with the kind and evidence, rather than retroactively changing the first request.

The classifier should not live in `AgentResponseCorrector`: that class is deliberately a bounded recovery mechanism and its quote-only path currently receives only response/messages/correlation id (`AgentResponseCorrector.java:78-122`). It has neither trusted invocation context nor complete tool evidence. It should remain responsible for verifying/selecting a catalog line.

## Proposed truth table

Definitions:

* `command` = `invocation.commandOriginated()`.
* `attempted` = at least one actual provider `LlmToolCall` was executed or rejected/failed in this invocation, not merely a definition being exposed.
* `success` = at least one successful `AgentToolResult` was recorded.
* `conversational` = the newest request is clearly social/conversational/non-actionable after considering trusted prompt context; this is a semantic candidate, not inferred from tool availability.
* `toolFree` = `!attempted` (must be computed from turn evidence, not only final response).

| Invocation / evidence | Final kind | Quote-only correction | Rationale |
|---|---|---:|---|
| `MODERATION`, any evidence | existing moderation path / silent | No | Preserve moderation behavior (`AgentResponseFinalizer.java:71-73`). |
| `command=true`, no tool attempted, ordinary command response | existing command-origin path | No | Preserve explicit current behavior (`DefaultAgentRouter.java:235-243`; `LUserCommandImpl.java:38-45`). |
| `command=true`, tool attempted, success or failure | existing command-origin path | No | Explicit command origin must not be converted into quote-only delivery. |
| non-command, `attempted=true`, `success=true` | tool-grounded (not TALK/UNCLASSIFIED) | No | Actual tool evidence must control; return the grounded result and avoid replacing it with a quote. |
| non-command, `attempted=true`, `success=false` | UNCLASSIFIED/action-failed | No by default; use failure/unavailable policy | Never treat failed action as tool-free conversation. Existing failure/required-tool policies should decide the user-facing limitation. |
| non-command, `attempted=false`, `conversational=true` | TALK | Yes | Conversational, tool-free requests receive a verified relevant quote. |
| non-command, `attempted=false`, `conversational=false` | UNCLASSIFIED | Yes only if the product contract explicitly requires a quote for all tool-free non-command requests; otherwise use ordinary unclassified response policy | The requirement says tool-free non-conversational requests are UNCLASSIFIED; that kind must be propagated and must not be mislabeled TALK. Whether UNCLASSIFIED also gets quote-only is a product decision to lock in with RED tests. |
| ambient, tool-free, model emits no-reply marker | TALK/UNCLASSIFIED candidate but silent | No delivery | Existing ambient participation/no-reply behavior wins (`AgentResponseFinalizer.java:74-76`). |
| any non-command final response where earlier tool calls occurred but final `response.toolCalls()` is empty | tool-grounded or action-failed based on evidence | No quote-only | This is the key anti-misclassification case. Final empty tool-call list is not equivalent to a tool-free turn. |

Recommended implementation interpretation of the requirement is: `TALK` means conversational **and** no actual tool attempt; `UNCLASSIFIED` means no actual tool attempt but not clearly conversational; a turn with actual tool calls is a separate grounded/action-failed outcome and is never classified as TALK merely because its final response is prose.

## Quote catalog and correction assessment

The catalog is operationally verified but not relevance-rich. `VerifiedQuoteCatalog` loads `/agent/verified-quotes.json`, requires non-empty id/quote/book/author/reference, rejects duplicate ids/lines, and validates the exact one-line quote shape (`VerifiedQuoteCatalog.java:16-31`, `:44-83`). The current catalog has only three entries: Austen's truth/want quote, Melville's “Call me Ishmael.”, and Carroll's “Curiouser and curiouser!” (`src/main/resources/agent/verified-quotes.json:1-23`). The `reference` field is validated for non-blank but is not used in selection or output (`VerifiedQuoteCatalog.java:62-82`, `:90-102`).

`AgentResponseCorrector.correctQuoteOnly` first accepts an exact catalog line, otherwise sends an isolated correction request containing only the latest user message plus the catalog (`AgentResponseCorrector.java:78-127`, `:220-232`). The correction contract says to choose the best matching catalog entry and use the first when none is clearly relevant (`router-quote-only-correction.txt:1-12`). It can use textual/contextual matching only; there are no topic tags, deterministic relevance scores, or request-kind parameters. It then verifies the corrected line exactly and falls back to the first entry on malformed/non-catalog quote-shaped output (`AgentResponseCorrector.java:113-121`, `:135-136`).

Conclusion: the current catalog/correction supports **verified exact quote selection**, and the correction prompt nominally supports relevance, but it does not robustly support reliably relevant selection for arbitrary TALK/UNCLASSIFIED prompts. It has only three broad entries, no machine-readable topics, and a deterministic first-entry fallback. A future implementation should either (a) add catalog topic metadata and deterministic selection tests, or (b) explicitly document that “relevant” means LLM-selected from this small catalog with first-entry fallback. Do not expand the catalog or change correction behavior in Phase 0.

One important safety boundary already exists: `correctQuoteOnly` is called at finalization after the router loop. Nevertheless, `verifiedResponse` preserves `response.toolCalls()` (`AgentResponseCorrector.java:130-133`), so the caller must continue to guarantee quote correction is never invoked on a tool-call response. The proposed evidence gate makes that invariant explicit.

## Root cause and gaps

1. **No semantic classification type exists.** Invocation mode is overloaded as participation policy and cannot represent TALK/UNCLASSIFIED.
2. **The current quote gate is an indirect proxy.** `!commandOriginated && successfulToolResults().isEmpty()` (`DefaultAgentRouter.java:243`) is not equivalent to “tool-free conversational request.”
3. **Failed calls are indistinguishable from no calls at the finalizer seam.** Only successful results are passed; failed tool attempts are lost as a classification signal.
4. **The first/final response distinction is not represented in policy data.** `response.toolCalls().isEmpty()` becomes true after a tool-backed turn reaches a final prose response.
5. **Classification metadata is absent from the system prompt.** `AgentSystemPrompt` can propagate mode/room/context but has no request kind or tool-evidence summary.
6. **Quote correction is verified but weakly relevant.** The catalog is tiny and untagged; fallback is always the first entry.
7. **Room context and history are available but only indirectly used.** They are supplied to the model and fresh-data policy; no dedicated classifier consumes them with an explicit trust/evidence contract.
8. **Command-origin behavior is intentionally special and must remain special.** The current uncommitted change and tests establish that `[l]` command responses bypass quote-only correction; a new classifier must not regress this.

## Impacted files (read-only inventory)

* `src/main/java/org/saturn/app/agent/api/AgentInvocation.java` — invocation metadata and current command-origin bit.
* `src/main/java/org/saturn/app/agent/api/AgentInvocationMode.java` — existing participation modes; likely not the right home for semantic kinds.
* `src/main/java/org/saturn/app/agent/api/AgentContext.java` — room/user/privacy/capability context and memory key.
* `src/main/java/org/saturn/app/agent/room/AgentRoomMessagePipeline.java` — mention/ambient/moderation entry routing and exclusions.
* `src/main/java/org/saturn/app/command/impl/user/LUserCommandImpl.java` — explicit command-origin creation.
* `src/main/java/org/saturn/app/service/impl/AgentServiceImpl.java` — submission, execution, and room reply delivery.
* `src/main/java/org/saturn/app/agent/turn/AgentTurnMemory.java` and `AgentMemoryStore.java` — history/tool-evidence load and persistence.
* `src/main/java/org/saturn/app/agent/routing/AgentInvocationFactory.java` — trusted invocation/context construction.
* `src/main/java/org/saturn/app/agent/routing/AgentRequestAssembler.java` — history/context/system/user message assembly and definitions.
* `src/main/java/org/saturn/app/agent/routing/AgentSystemPrompt.java` plus `src/main/resources/agent/system-policy.txt` — trusted runtime and policy propagation.
* `src/main/java/org/saturn/app/agent/routing/DefaultAgentRouter.java` — complete tool loop, evidence accumulation, current quote predicate, final persistence.
* `src/main/java/org/saturn/app/agent/turn/AgentTurnState.java` and `src/main/java/org/saturn/app/agent/tool/execution/AgentToolResultCoordinator.java` — tool attempt/result evidence seam.
* `src/main/java/org/saturn/app/agent/routing/AgentResponseFinalizer.java` — final correction and reply/silence decisions.
* `src/main/java/org/saturn/app/agent/routing/AgentResponseCorrector.java` — verified quote correction; should remain a verifier/corrector, not classifier.
* `src/main/java/org/saturn/app/agent/routing/VerifiedQuoteCatalog.java` and `src/main/resources/agent/verified-quotes.json` — catalog verification and available entries.
* Existing focused tests: `DefaultAgentRouterTest`, `AgentResponseFinalizerTest`, `AgentResponseCorrectorTest`, `AgentRequestAssemblerTest`, `AgentSystemPromptTest`, `AgentToolResultCoordinatorTest`, `AgentTurnStateTest`, `AgentInvocationTest`, `AgentInvocationFactoryTest`, and `LUserCommandImplTest`.

## Tight RED test plan (Phase 1 implementation handoff)

Do not write production code before these tests are made red. Keep each test focused and assert exact observable behavior.

1. **Classifier unit: tool-free conversational request => TALK.** Given a non-command `DIRECT` or `MENTION` invocation, no tool call attempted, and a plainly conversational prompt, assert `TALK`.
2. **Classifier unit: tool-free non-conversational request => UNCLASSIFIED.** Same evidence, prompt is not clearly conversational/actionable, assert `UNCLASSIFIED`, not TALK.
3. **Router anti-regression: tool-backed final prose is not TALK.** Script initial `room_users` tool call then final ordinary prose; assert no quote-correction request and ordinary grounded content is returned. This catches the “final `toolCalls()` empty” mistake.
4. **Router failed-tool distinction.** Script a non-command tool attempt returning an error and final prose; assert it is not treated as tool-free quote-only. Assert the existing failure/unavailable policy or explicit unclassified action-failure result.
5. **Router true no-tool TALK.** Script one ordinary final response and no tool calls; assert quote correction is invoked and final content is an exact catalog line.
6. **Router true no-tool UNCLASSIFIED.** Script one ordinary final response and no tool calls for a non-conversational/non-command prompt; assert the chosen contract (quote-only if required, otherwise ordinary unclassified) and propagated kind.
7. **Command-origin preservation.** Existing `commandOriginatedNoToolResponseBypassesQuoteOnly` and `commandOriginatedAllFailedToolsBypassQuoteOnly` must remain green; add an assertion that command-origin is not reclassified as TALK.
8. **Mode preservation.** Moderation remains silent; ambient `NO_REPLY` remains silent; mention/direct reply requirements remain unchanged.
9. **Prompt propagation.** `AgentSystemPromptTest`/`AgentRequestAssemblerTest` should assert trusted runtime metadata contains request kind plus room, whisper, caller, and room snapshot; user-authored history must not be able to override it.
10. **Tool evidence propagation.** Router test should assert the second provider request contains the assistant tool call and tool result, while finalizer receives `toolAttempted=true`, success/failure counts, and the final kind.
11. **Quote relevance/catalog contract.** `AgentResponseCorrectorTest` should assert exact catalog acceptance, fabricated quote correction, malformed correction fallback, and that the selected line is one of the catalog entries. If “relevant” becomes deterministic, add topic/selection tests; otherwise codify first-entry fallback.
12. **No quote correction with tool calls.** Directly test the finalizer seam with a response containing a tool call and assert it never calls quote-only correction (or rejects the invalid finalization state). This protects the `verifiedResponse` tool-call preservation boundary.

A good first RED slice is test 3: it isolates the current root cause at `DefaultAgentRouter`/`AgentResponseFinalizer` with a deterministic scripted client and does not require changing the room pipeline.
