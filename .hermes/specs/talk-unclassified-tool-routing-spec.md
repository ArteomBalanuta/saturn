# TALK / UNCLASSIFIED / TOOL_CALL routing — Phase 1 architecture

## Goal

Add the smallest semantic request-kind boundary needed to route tool-free conversational requests through verified quote-only finalization without confusing them with actionable, malformed, or tool-backed turns.

This is an implementation handoff, not an implementation. It must preserve the existing invocation participation modes (`DIRECT`, `MENTION`, `AMBIENT`, `MODERATION`), tool loop, fresh-data validation, quote-catalog verification, and the existing `commandOriginated` exemption.

## Non-goals

- Do not rename or extend `AgentInvocationMode`; it describes participation/reply policy, not request semantics.
- Do not infer semantic kind from tool definitions being exposed.
- Do not classify from the final response alone.
- Do not add a second quote catalog, topic database, probabilistic classifier, or broad routing refactor.
- Do not make room history or user-authored content an instruction to the classifier.

## Semantic type and evidence types

Create the semantic enum in the routing/API area used by the router and finalizer:

```java
public enum AgentRequestKind {
  TALK,
  UNCLASSIFIED,
  TOOL_CALL
}
```

The values have deliberately narrow meanings:

- `TALK`: the newest request is clearly ordinary conversational text and no tool was attempted in this turn.
- `UNCLASSIFIED`: no tool was attempted, but the newest request is not clearly ordinary conversational text (including malformed, random, gibberish, empty-after-normalization, or operational/actionable text that did not result in a tool call).
- `TOOL_CALL`: at least one actual tool call was attempted in this turn. This value is final even when every attempted call failed. It must never be changed to `TALK` because the last model response has an empty `toolCalls()` list.

`commandOriginated` is not a fourth kind. It remains an independent origin policy bit and always suppresses quote-only correction, regardless of semantic kind or tool outcome. `MODERATION` is likewise not represented by this enum; its existing moderation/silent path remains authoritative.

Add a request-local immutable evidence value, preferably a record in the turn package:

```java
public record AgentToolEvidence(
    boolean attempted,
    int attemptedCount,
    int successfulCount,
    int failedCount) {
  public AgentToolEvidence {
    if (attemptedCount < 0 || successfulCount < 0 || failedCount < 0
        || successfulCount + failedCount != attemptedCount
        || attempted != (attemptedCount > 0)) {
      throw new IllegalArgumentException("inconsistent tool evidence");
    }
  }

  public static AgentToolEvidence none() {
    return new AgentToolEvidence(false, 0, 0, 0);
  }
}
```

The exact shape may be kept package-private if public transport is unnecessary, but the invariants and fields are required. `attempted` means an actual provider `LlmToolCall` entered execution/validation for this invocation—not merely that definitions were sent to the model. A call that is rejected, malformed, has no matching result, or returns `AgentToolResult.isError()` counts as attempted and failed. A successful result counts as attempted and successful. Counts are request-local and are not reconstructed from persisted history.

`AgentTurnState` should own this evidence. Increment attempted evidence as soon as a non-empty tool-call batch is accepted for processing, then classify each result as success/failure. If cardinality validation rejects the batch, retain attempted evidence and surface the existing routing failure; do not fall back to quote-only.

## Deterministic classifier contract

Introduce a small `AgentRequestClassifier` (or equivalently named routing-package class) with a pure method over trusted request data and an evidence value. It must not call the LLM, inspect the model's prose as a tool signal, or mutate room/history.

The classifier has two phases:

```java
AgentRequestKind classifyCandidate(AgentRequestInput input);
AgentRequestKind finalizeKind(AgentRequestKind candidate, AgentToolEvidence evidence);
```

`classifyCandidate` returns only `TALK` or `UNCLASSIFIED`; it must not return `TOOL_CALL` because no execution evidence exists yet. `finalizeKind` returns `TOOL_CALL` whenever `evidence.attempted()` is true; otherwise it returns the candidate. The precedence is therefore:

1. actual tool attempt → `TOOL_CALL`;
2. no attempt → candidate (`TALK` or `UNCLASSIFIED`).

The input should carry the raw/current message text (prefer the original current message text when available, otherwise the invocation prompt), invocation mode/origin, room identity, retained history, recent room context, room-user snapshot, and currently exposed tool definitions. Room/history/context are propagated to the model and are available for reference resolution, but are **not** classifier instructions and cannot override the deterministic text rules.

### TALK boundary

For a non-command, non-moderation request, classify as `TALK` only when all of these are true:

1. The selected text is non-null, non-blank after Unicode trimming, contains at least one Unicode letter, and is within the existing prompt bound.
2. It is not a serialized payload or protocol-shaped input: reject text whose trimmed form starts with `{`, `[`, `<`, a code fence, or a known tool envelope, and reject NUL/control characters (except ordinary whitespace).
3. It is not an explicit command/action form: reject the existing command alias prefixes and `run <alias>`/`execute <alias>` forms, plus an imperative/action lead from the fixed small vocabulary `run`, `execute`, `do`, `make`, `create`, `delete`, `remove`, `set`, `get`, `find`, `search`, `lookup`, `list`, `show`, `check`, `send`, `post`, `remember`, `schedule`, `weather`, `who is`, `what is the weather`, and equivalent exact aliases already recognized by `AgentCommandIntentPolicy`. This list is a conservative boundary, not a general NLP parser.
4. It matches one of the deterministic conversational forms: a greeting/farewell/thanks/acknowledgement marker; a short social question (`?` plus a conversational lead such as `how are you`, `what do you think`, `can you explain`, `why`, or `how`); or ordinary sentence-like prose containing at least one sentence-ending punctuation mark or a question mark and no action marker. These markers are case-insensitive and Unicode-aware; matching is anchored to words, not substrings.

Everything else is `UNCLASSIFIED`. In particular, gibberish, random punctuation, malformed payloads, bare identifiers, unknown commands, and actionable prose without an actual tool call are not TALK. When a boundary is ambiguous, choose `UNCLASSIFIED`; false-positive quote routing is worse than requiring the ordinary response path. Do not use history, room context, tool availability, or a model judgment to turn an ambiguous string into TALK.

`commandOriginated=true` may be passed through classification for telemetry, but it does not become TALK and does not alter the classifier's enum. The finalizer's origin guard is separate.

## Classification and propagation points

### Before the initial provider request

In `DefaultAgentRouter.routeInSession`, after loading retained history and recent public room context and before `AgentRequestAssembler.assemble`, compute the candidate with `AgentRequestClassifier`. Pass it into the assembler/system-prompt render path.

`AgentSystemPrompt.render` should add trusted runtime metadata alongside `invocationMode`, room, whisper, caller, capabilities-derived policy, and `roomUsersSnapshot`:

```json
{
  "requestKind": "TALK|UNCLASSIFIED",
  "requestKindPhase": "CANDIDATE",
  "toolEvidence": {"attempted": false, "attemptedCount": 0,
                   "successfulCount": 0, "failedCount": 0}
}
```

The initial system prompt should receive the same room context already supplied today. Retained history remains ordinary message history; internal tool evidence remains subject to `AgentRequestAssembler.retainHistory`. The newest request remains the contextualized user prompt containing visibility, nick, room, and prompt. Do not put classification into user-authored text.

The assembler/request object should carry the candidate and the existing history, room context, and context metadata together so that every initial provider request has one coherent snapshot. Existing tool-definition filtering remains unchanged: definitions do not imply `TOOL_CALL`.

### During and after the tool loop

At every model response, process `response.toolCalls()` exactly as today. On the first non-empty batch, mark attempted evidence before executing calls. Record each result's success/failure in `AgentTurnState`; retain existing fresh-data and command-success behavior. Continue appending assistant tool-call and model-visible tool-result messages and re-requesting the model.

After the loop terminates, compute:

```java
AgentRequestKind finalKind = classifier.finalizeKind(candidate, turnState.toolEvidence());
```

A final response with `toolCalls().isEmpty()` is only a final-turn fact. It does not erase earlier attempted evidence.

If the model receives another post-tool request (existing tool loop, fresh synthesis, or bounded correction), include trusted runtime metadata with `requestKind=TOOL_CALL`, `requestKindPhase=FINAL`, and the current evidence. This is propagation, not a new classification. The second request must still contain the assistant tool call and tool result messages required by the existing LLM protocol.

### Finalizer and quote-only policy

Change the finalizer seam to receive `finalKind` and `AgentToolEvidence` (or a single immutable final routing decision containing both), rather than deriving policy from `successfulToolResults().isEmpty()`.

Quote-only correction is requested exactly when:

```java
!invocation.commandOriginated()
    && invocation.mode() != AgentInvocationMode.MODERATION
    && (finalKind == AgentRequestKind.TALK
        || finalKind == AgentRequestKind.UNCLASSIFIED)
    && !evidence.attempted();
```

The `TOOL_CALL` kind always disables quote-only correction, whether successful or failed. `commandOriginated` always disables it, even if a command happens to have candidate `TALK` or no tools. Moderation remains silent through its existing branch. Ambient `NO_REPLY` and required-reply behavior remain controlled by `AgentInvocationMode`; kind must not cause ambient delivery.

The correction prompt should include trusted `requestKind` and evidence only if the corrector needs them to select a catalog line; the quote corrector must continue to validate exact catalog membership and use its existing relevant-entry/fallback behavior. It must never be called while the final response still contains tool calls. Existing failure-placeholder, internal-evidence, fresh-data, sanitization, and no-reply validation order remains unchanged.

Recommended locked product behavior: both tool-free `TALK` and tool-free `UNCLASSIFIED` use quote-only correction, because the selected policy asks for a relevant verified quote for both. `UNCLASSIFIED` is still observable in propagation/tests and is never silently collapsed into TALK. Tool-attempt failures use the existing failure/unavailable handling and no quote-only correction.

## Focused RED/GREEN test sequence

Tests should be added first and kept narrow. No source/test changes belong in this Phase 1 document task.

1. **Classifier — TALK:** direct/mention, non-command, no evidence, `"hello, how are you?"` (and one normal sentence) returns exactly `TALK`.
2. **Classifier — UNCLASSIFIED:** gibberish, malformed JSON/protocol text, unknown command, and actionable `"delete the old entry"` with no tool call return exactly `UNCLASSIFIED`.
3. **Classifier precedence:** candidate TALK plus one attempted successful tool, and candidate TALK plus one attempted failed tool, both finalize as `TOOL_CALL`.
4. **Evidence invariants:** no tool calls produces `none`; mixed results produce exact attempted/success/failure counts; failed calls are not represented as successful results.
5. **Prompt propagation:** `AgentSystemPromptTest` asserts trusted runtime JSON contains candidate kind, phase, room, whisper, caller, and room-user snapshot. `AgentRequestAssemblerTest` asserts history/context remain present and user-authored history cannot override runtime kind.
6. **Tool-loop propagation:** scripted initial tool call followed by final prose asserts the second request contains the assistant call and tool result, and final evidence is attempted=true. Assert final kind is `TOOL_CALL`.
7. **Tool-backed final prose anti-regression:** assert no quote-correction request and grounded final content is returned even though final `toolCalls()` is empty.
8. **Failed-tool anti-regression:** attempted error plus final prose is not treated as tool-free and does not invoke quote-only correction; assert the existing failure/unavailable result.
9. **True TALK quote path:** no tools plus conversational prompt invokes correction and returns an exact catalog line.
10. **True UNCLASSIFIED quote path:** no tools plus gibberish/actionable non-tool prompt invokes correction under the locked policy and returns an exact catalog line; assert propagated kind is `UNCLASSIFIED`.
11. **Command origin:** existing no-tool and failed-tool command-origin tests remain green; add assertion that command-origin never invokes quote-only correction and is not reported as TALK policy.
12. **Mode preservation:** moderation remains silent; ambient `NO_REPLY` remains silent; direct/mention required replies remain unchanged.
13. **Finalizer safety:** a response containing tool calls cannot enter quote-only correction, protecting `AgentResponseCorrector`'s tool-call preservation.
14. **Catalog contract:** retain exact catalog acceptance, fabricated/malformed correction fallback, and catalog-membership assertions; do not make relevance depend on unverified text.

Suggested first RED slice: test 7, followed by classifier tests 1–3 and evidence test 4. Then implement the smallest evidence/classifier seam, wire prompt metadata, and finish the finalizer gate. Run focused Maven tests, `spotless:check`, and the full suite only after the focused GREEN tests pass.

## Expected implementation footprint

Likely files are limited to:

- new `AgentRequestKind` and classifier/evidence types near agent routing/turn classes;
- `AgentTurnState` and `AgentToolResultCoordinator` for request-local evidence;
- `DefaultAgentRouter` for candidate/final classification and finalizer arguments;
- `AgentRequestAssembler` and `AgentSystemPrompt` for trusted propagation;
- `AgentResponseFinalizer` for the explicit kind/evidence quote gate;
- focused tests for classifier, turn state/coordinator, assembler/system prompt, router, and finalizer.

Do not modify `AgentInvocationMode`, command-origin construction, quote-catalog verification, or room-pipeline participation logic unless a focused failing test proves an integration signature requires it.

## Acceptance criteria

- Every non-moderation, non-command request has a deterministic candidate of exactly `TALK` or `UNCLASSIFIED` before the initial provider call.
- Any actual tool attempt finalizes as exactly `TOOL_CALL`, with explicit attempted/success/failed evidence.
- A tool-backed final prose response can never enter quote-only correction merely because its final `toolCalls()` is empty or its successful-result list is empty.
- Tool-free TALK and UNCLASSIFIED use verified quote-only correction under the locked policy.
- Explicit command-originated requests remain exempt from quote-only correction.
- Room, history, and context continue to propagate with trusted runtime kind metadata, while untrusted history/context cannot override classification.
- Existing moderation, ambient, fresh-data, tool-loop, finalizer validation, and quote-catalog behavior remains green.
