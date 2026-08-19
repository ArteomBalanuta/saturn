# Explicit command invocation quote-policy forensics

**Phase:** 0 — investigation only
**Repository:** `/Users/ab/workspace/projects/saturn`
**Branch / HEAD:** `develop`, `428fa3becee1877368ee920075889d9844efcf0a`
**Scope:** Trace `*l` from command dispatch through `AgentInvocationFactory`/`AgentInvocation` to `DefaultAgentRouter` and finalization. No production or test source was modified.

## Executive finding

There is **no authoritative command-origin signal** in the current `AgentInvocation` API.

`*l ice and fire` is dispatched as the `l` user command and `LUserCommandImpl` creates an invocation with `AgentInvocationMode.DIRECT`, but `DIRECT` is also the default mode for ordinary programmatic agent requests. `AgentInvocationFactory` copies the caller-selected mode and the original message text; it does not preserve the fact that a command wrapper created the request. `AgentInvocation` has no origin/source field. Consequently, by the time `DefaultAgentRouter` finalizes the response, an explicit command with zero successful tools is indistinguishable from an ordinary direct/no-tool turn.

The smallest backward-compatible change is to add an explicit origin bit to `AgentInvocation` (for example `boolean commandOriginated`) with all existing constructors defaulting to `false`, add a factory overload that accepts the bit, and have only `LUserCommandImpl` pass `true`. The router predicate would then be:

```java
boolean quoteOnlyRequired =
    !invocation.commandOriginated() && turnState.successfulToolResults().isEmpty();
```

This preserves quote-only behavior for ordinary no-tool turns, while explicit commands bypass quote-only even when all requested tools fail or no tool call is produced.

A named enum such as `AgentInvocationOrigin { ROOM, COMMAND }` is semantically clearer, but the boolean is the smaller API/data change and is source-compatible when existing constructors are retained.

## Exact invocation trace

### 1. Command wrapper and `*l` dispatch

- `src/main/java/org/saturn/app/command/impl/user/LUserCommandImpl.java:17-19` declares `@CommandAliases(aliases = {"l"})`.
- `src/main/java/org/saturn/app/command/factory/CommandFactory.java:43-61` receives the parsed command token, matches it against aliases using `Util.checkAnagrams`, reflectively constructs the command, and passes the original `ChatMessage`.
- `src/main/java/org/saturn/app/command/UserCommandBaseImpl.java:45-53` parses the prefix-stripped message. For `*l ice and fire`, it sets alias `L` and arguments `ice`, `and`, `fire`.
- `UserCommandBaseImpl.java:73-93` resolves the concrete command and invokes its `execute()` method after authorization.
- `LUserCommandImpl.java:31-43` validates arguments and agent availability, then calls:

  ```java
  new AgentInvocationFactory(...)
      .create(engine, chatMessage, renderArguments(true).trim(), AgentInvocationMode.DIRECT);
  engine.getAgentService().submit(invocation);
  ```

  Thus the explicit command's prompt is `ice and fire`, but the only invocation classification supplied is `DIRECT`.

The existing command test confirms this contract: `src/test/java/org/saturn/app/command/impl/user/LUserCommandImplTest.java:36-55` asserts the prompt, `AgentInvocationMode.DIRECT`, context, room users, and whisper state, but has no command-origin assertion because no such field exists.

### 2. Factory boundary

`src/main/java/org/saturn/app/agent/routing/AgentInvocationFactory.java:24-25` exposes one creation API:

```java
create(EngineImpl engine, ChatMessage message, String prompt, AgentInvocationMode mode)
```

The factory computes capabilities at `:28-50`, builds `AgentContext` at `:52-60`, and returns the invocation at `:61-62`:

```java
new AgentInvocation(UUID.randomUUID().toString(), context, prompt, mode, message.getText())
```

The `mode` is caller-selected. `currentMessageText` preserves the raw chat text, but is context data, not an authoritative source classification. The factory has no `commandOriginated`/source argument and does not infer one from text.

Ordinary room traffic is created by `src/main/java/org/saturn/app/agent/room/AgentRoomMessagePipeline.java:111-117`: it parses an optional mention and selects only `MENTION` or `AMBIENT`, then calls the same four-argument factory API. `AgentRoomMessagePipeline.java:154-161` may submit an ambient invocation. Semantic moderation separately constructs `MODERATION` at `:146-150`.

### 3. Invocation data model

`src/main/java/org/saturn/app/agent/api/AgentInvocation.java:7-12` currently stores only:

- request ID
- `AgentContext`
- prompt
- `AgentInvocationMode`
- current message text

Validation is at `:13-22`; compatibility constructors are at `:24-39`. None records command origin.

`src/main/java/org/saturn/app/agent/api/AgentInvocationMode.java:4-18` has only `DIRECT`, `MENTION`, `AMBIENT`, and `MODERATION`; its sole property is `requiresReply()`. There is no `TALK`, `UNCLASSIFIED`, command-intent, or command-origin mode.

### 4. Service/router boundary

`src/main/java/org/saturn/app/service/impl/AgentServiceImpl.java:41-53` accepts the immutable invocation and routes non-ambient work; `:108-121` logs the mode, calls `router.route(invocation)`, and replies if the result requires it. No origin information is added or inferred.

`src/main/java/org/saturn/app/agent/routing/DefaultAgentRouter.java:136-154` carries the same invocation through request assembly, tool definition setup, and per-turn state. The tool loop records results at `:217-232` and exits when the model has no more tool calls at `:204-206`.

At the finalization seam, `DefaultAgentRouter.java:235-243` calls the explicit `AgentResponseFinalizer.prepare` overload and currently passes:

```java
turnState.successfulToolResults().isEmpty()
```

That existing predicate correctly distinguishes successful tool-grounded turns from ungrounded turns, but it cannot distinguish a failed/no-tool explicit command from ordinary no-tool prose.

`src/main/java/org/saturn/app/agent/routing/AgentResponseFinalizer.java:34-50` has a default overload whose quote policy is `invocation.mode() != AgentInvocationMode.MODERATION`. The router does **not** use that default; it uses the explicit boolean overload at `:52-59`. The finalizer invokes strict quote correction only at `:78-80` when `quoteOnlyRequired` is true. Moderation is silenced first at `:71-73`.

### 5. Why the observed failure occurs

`src/main/java/org/saturn/app/agent/tool/execution/AgentToolResultCoordinator.java:46-75` records each tool outcome. Successful results enter `AgentTurnState.successfulToolResults` at `:65-67`; only successful `run_command` calls additionally enter the command-name ledger at `:68-70`.

`src/main/java/org/saturn/app/agent/turn/AgentTurnState.java:19-22,89-131` therefore maintains two different facts:

- `successfulCommands`: successful command names, specifically the `run_command` bookkeeping path.
- `successfulToolResults`: every non-error tool result, including `room_users`, SDK/read-only, history, database, and future tools.

For `*l ice and fire` in the reported container evidence, command recognition and enqueueing succeeded and the invocation was `DIRECT`, but no tool succeeded. At `DefaultAgentRouter.java:241-243`, the successful-result list is empty, so quote-only is requested. `AgentResponseFinalizer.java:78-80` invokes quote correction, and the ordinary failure prose is rejected as `Quote correction failed validation` (the externally observed symptom).

The root cause is a missing **request-origin bit**, not a failure of alias recognition, command enqueueing, tool result recording, or quote-catalog validation. The current successful-tool predicate is correct for the previous “tool-grounded turns bypass quote-only” requirement; it is insufficient for the new rule that explicit command-originated turns bypass quote-only even without successful tool evidence.

## Authoritative-signal analysis

| Candidate signal | Exists? | Why it is insufficient or authoritative |
|---|---:|---|
| `AgentInvocationMode.DIRECT` | Yes | Not authoritative: `DIRECT` is used by `LUserCommandImpl` and by compatibility constructors (`AgentInvocation.java:24-39`) for ordinary direct requests. |
| `AgentInvocationMode.MENTION` / `AMBIENT` | Yes | These classify room participation/reply behavior, not explicit command origin. |
| `currentMessageText` beginning with `*l` | Yes, for some invocations | Not authoritative: the command wrapper has already parsed the command, prefix is configurable, message text may be absent in compatibility constructors, and text inspection would duplicate dispatch semantics. |
| `AgentInvocationFactory.create(..., mode)` caller path | Yes | The call site knows the origin, but the API drops it at construction. This is the correct seam to preserve it. |
| `successfulCommands()` / `hasSuccessfulCommands()` | Yes | Only post-execution `run_command` success; false for no-tool/failed-command turns, exactly the bug case. |
| `successfulToolResults()` | Yes | Authoritative for successful tool evidence, but intentionally cannot answer whether the request originated at `*l`. |
| `TALK` / `UNCLASSIFIED` classifier | No | No such enum or classifier exists in production source; search found no declarations/usages. |

## Recommended smallest backward-compatible API change

1. Extend `AgentInvocation` with a final `boolean commandOriginated` component.
2. Keep every existing constructor and make each delegate with `false`, preserving source compatibility and ordinary behavior.
3. Add an overload to `AgentInvocationFactory.create` with the same existing parameters plus `boolean commandOriginated`; keep the current four-argument method delegating to `false`.
4. Change only `LUserCommandImpl.java:38-40` to call the new overload with `true`.
5. At `DefaultAgentRouter.java:235-243`, compute quote-only as:

   ```java
   !invocation.commandOriginated()
       && turnState.successfulToolResults().isEmpty()
   ```

No inference from alias text, prompt content, `DIRECT`, or tool names should be introduced. No change is needed to `AgentInvocationMode`; its reply semantics remain independent of origin.

A source-compatible alternative is a small `AgentInvocationOrigin` enum with `ROOM`/`COMMAND`, defaulting all old constructors to `ROOM`. It is more extensible but is not the smallest change for this one policy bit.

## Truth table

“Successful results” means non-error `AgentToolResult` entries recorded by `AgentToolResultCoordinator:65-67`.

| Request shape | Origin bit | Tool calls / results | Current quote-only predicate | Required quote-only | Expected finalization |
|---|---:|---|---:|---:|---|
| Ordinary room TALK / unclassified prose | false | no tool / 0 successful | Yes | **Yes** | strict verified-catalog quote correction |
| Ordinary `DIRECT` compatibility invocation | false | no tool / 0 successful | Yes | **Yes** | quote-only; preserves existing behavior |
| Ordinary `MENTION` with no tool | false | no tool / 0 successful | Yes | **Yes** | quote-only, subject to mention reply rules |
| Explicit `*l ...` command, no tool selected | true | no tool / 0 successful | Yes (bug) | **No** | ordinary command response; do not quote-correct |
| Explicit `*l ...` command, all tools fail | true | tool calls / 0 successful | Yes (bug) | **No** | normal failure/validation path, but no quote-only conversion |
| Explicit `*l ...` command, successful tool | true | 1+ / 1+ | No | **No** | ordinary grounded command response |
| Ordinary room request, successful `room_users`/SDK tool | false | 1+ / 1+ | No | **No** | ordinary grounded synthesis |
| Ordinary room request, successful `run_command` | false | 1+ / 1+ | No | **No** | existing command-channel behavior |
| Moderation invocation | false/irrelevant | any | implementation-dependent input | **n/a** | silent at `AgentResponseFinalizer.java:71-73`, unchanged |

## Impacted tests

- `src/test/java/org/saturn/app/command/impl/user/LUserCommandImplTest.java:36-55`: assert that the submitted `*l` invocation carries the new origin signal. Existing assertions for `DIRECT`, prompt, context, capabilities, and whisper must remain.
- `src/test/java/org/saturn/app/agent/routing/AgentResponseFinalizerTest.java:48-66`: preserve the ordinary no-evidence quote-only rejection. The existing successful-tool case at `:68-84` preserves the non-quote path. Add a command-origin case only if the finalizer API is made responsible for the predicate; otherwise keep the origin policy at the router seam.
- `src/test/java/org/saturn/app/agent/routing/DefaultAgentRouterTest.java:56-94`: retain the successful `room_users` ordinary-prose regression. Add the minimal no-tool explicit-command route test, using a scripted ordinary response that would fail quote correction, and assert it is returned unchanged.
- `src/test/java/org/saturn/app/agent/routing/AgentInvocationFactoryTest.java` (if present or introduced with the implementation): assert old factory overload defaults to non-command and the new command overload preserves `true`. If no factory test class exists, the L command submission test is the direct boundary test.
- `src/test/java/org/saturn/app/agent/turn/AgentTurnStateTest.java` and `src/test/java/org/saturn/app/agent/tool/execution/AgentToolResultCoordinatorTest.java`: retain the ledger distinction: ordinary successful tools populate `successfulToolResults`; only successful `run_command` populates `successfulCommands`. Do not repurpose either ledger as origin.
- `src/test/java/org/saturn/app/service/impl/AgentServiceImplTest.java`: existing constructor compatibility tests should continue compiling and passing with default `commandOriginated == false`.

## Tight RED test plan (implementation phase; do not apply in Phase 0)

1. **Write one end-to-end router RED test first.** Construct an `AgentInvocation` representing `*l ice and fire` through the proposed command-origin API, use a scripted provider that returns ordinary prose and makes no tool call, and route it through `DefaultAgentRouter`. Assert the ordinary prose is returned. Against current HEAD, the test must fail with quote-correction validation because the origin bit is absent/ignored.
2. **Add the command-wrapper boundary assertion.** In `LUserCommandImplTest`, execute `*l ice and fire` with a recording service and assert the submitted invocation is command-originated. This proves the signal is set at the only authoritative source: the wrapper call site.
3. **Add the ordinary no-tool control.** Route an existing-style `new AgentInvocation(context, "ordinary prose")` with no origin flag and assert quote-only behavior remains. This prevents broadening the bypass to all `DIRECT` invocations.
4. **Add the failed-tool command case.** Script a command-originated request whose tool call returns an error, then an ordinary failure response. Assert quote-only is not requested; retain normal failure-placeholder, sanitization, and required-response validation.
5. **Retain successful-tool and moderation controls.** Existing `room_users` and moderation tests must remain unchanged in meaning: successful tool evidence bypasses quote-only, and moderation remains silent.
6. **Run only after the RED is observed:** focused routing/command tests, then formatting and the project suite prescribed by `AGENTS.md`. Phase 0 deliberately does not modify or run a newly added test.

## Verification performed for this investigation

- Read `AGENTS.md`, the repository command skill, and the systematic-debugging/TDD guidance.
- Inspected the command wrapper, reflective command factory, command base dispatcher, invocation factory/model/mode, room pipeline, service boundary, router, finalizer, result coordinator, turn state, and focused tests.
- Confirmed HEAD is `428fa3becee1877368ee920075889d9844efcf0a`; existing unrelated diagnostic/runtime artifacts remain untouched.
- Confirmed no production/test source files were modified by this Phase 0 investigation.

## Conclusion

The explicit `*l` path has a strong origin at the command-wrapper call site, but that fact is discarded when `AgentInvocationFactory.create(..., AgentInvocationMode.DIRECT)` constructs the invocation. There is no existing authoritative signal at router finalization. Preserve the fact explicitly with the smallest backward-compatible `commandOriginated` field plus factory overload, and make quote-only conditional on both **not command-originated** and **no successful tool results**. This is the narrowest change that fixes the observed no-tool command failure without weakening ordinary TALK/UNCLASSIFIED quote-only behavior.
