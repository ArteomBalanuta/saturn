# Quote-only mode policy forensics

**Phase:** 0 — root-cause and policy analysis only
**Repository:** `/Users/ab/workspace/projects/saturn`
**Branch:** `develop`
**Date:** 2026-08-19
**Scope:** Determine the authoritative distinction between command/tool execution and ordinary TALK/UNCLASSIFIED-style turns, identify the smallest quote-only predicate, and plan regression coverage. No production or test source was edited.

## Executive finding

The current Java implementation has no `TALK` or `UNCLASSIFIED` enum/classifier. The authoritative runtime distinction at the quote-only decision point is **whether this invocation produced at least one successful tool result**. Invocation modes (`DIRECT`, `MENTION`, `AMBIENT`, `MODERATION`) describe admission/reply behavior, not user-intent classes such as TALK or UNCLASSIFIED.

The current dirty working tree contains this pending router change:

```java
// DefaultAgentRouter.java, current worktree, lines 235–243
turnState.successfulToolResults().isEmpty()
```

HEAD still contains the old expression:

```java
!turnState.hasSuccessfulCommands()
```

The smallest correct policy predicate for the new requirement is therefore:

```java
boolean quoteOnlyRequired = turnState.successfulToolResults().isEmpty();
```

Equivalent wording: apply quote-only only when **no successful tool/SDK execution occurred in this turn**. A successful `run_command`, reflected Saturn command tool, or ordinary SDK/read-only tool must bypass quote-only. Failed tool calls do not count as execution evidence and must not suppress quote-only.

## Authoritative source trace

### 1. Invocation modes are not TALK/UNCLASSIFIED

`src/main/java/org/saturn/app/agent/api/AgentInvocationMode.java:4-18` defines only:

- `DIRECT(true)`
- `MENTION(true)`
- `AMBIENT(false)`
- `MODERATION(false)`

The only property is `requiresReply()`. There is no `TALK`, `UNCLASSIFIED`, command-intent, or response-mode value. `AgentInvocation.java:7-22` stores the mode and current message text but does not classify the prompt.

`AgentInvocationFactory.java:24-62` constructs invocations from room routing and forwards the caller-selected mode. It assigns capabilities based on role/mode, but does not classify the request as TALK or UNCLASSIFIED.

### 2. Command exposure is a separate request-assembly policy

`src/main/java/org/saturn/app/agent/routing/AgentCommandIntentPolicy.java:15-27` filters reflected `saturn_` command definitions for non-moderation requests. `:30-37` recognizes explicit command syntax from the newest prompt. This controls which command definitions are offered to the model; it is not the quote-only predicate and does not classify all prompts into TALK/UNCLASSIFIED.

`AgentRequestAssembler.java:33-77` assembles definitions and invokes that filter. Ordinary tools such as `room_users` remain available; the command-intent gate only filters reflected `saturn_` definitions.

### 3. Successful execution state is recorded centrally

`src/main/java/org/saturn/app/agent/tool/execution/AgentToolResultCoordinator.java:46-75` is the authoritative result ledger:

- `:49-53` logs each tool outcome.
- `:54-58` treats required fresh-tool errors as failures.
- `:65-67` records every non-error result with `AgentTurnState.recordSuccessfulToolResult`.
- `:68-73` separately records successful/failed `run_command` command names.

`src/main/java/org/saturn/app/agent/turn/AgentTurnState.java:19-22,89-131` confirms two distinct ledgers:

- `successfulCommands` is command-name-specific and is populated only by the `run_command` branch above.
- `successfulToolResults` contains every successful tool result, including `room_users`, history, database, SDK, and future tools.

Thus `hasSuccessfulCommands()` is **not** a general “tool executed” predicate. It is narrower bookkeeping for command-channel correction. `successfulToolResults().isEmpty()` is the smallest existing-state predicate matching “a tool/SDK execution succeeded.”

### 4. Quote-only is selected at the router/finalizer boundary

`src/main/java/org/saturn/app/agent/routing/DefaultAgentRouter.java:204-232` exits the tool loop only after a response has no tool calls, and records tool results at `:217-228`. At `:235-253`, it calls `AgentResponseFinalizer.prepare(...)` and supplies the quote-only boolean as the final argument.

At HEAD (`git diff` confirms the pending worktree change), the old argument is:

```java
!turnState.hasSuccessfulCommands()
```

That makes a successful non-command tool such as `room_users` look like a no-command turn and incorrectly enables quote correction. The current worktree argument is `turnState.successfulToolResults().isEmpty()`, which matches the requirement.

`src/main/java/org/saturn/app/agent/routing/AgentResponseFinalizer.java:34-50` has a convenience overload that defaults quote-only to `invocation.mode() != MODERATION`; `DefaultAgentRouter` uses the explicit overload at `:52-59`, so the router-provided boolean is the operative policy for normal routed turns.

`AgentResponseFinalizer.java:61-93` performs final correction/validation and calls `correctQuoteOnly` only at `:78-80` when `quoteOnlyRequired` is true. Moderation is silenced earlier at `:71-73`; that behavior is independent and must remain unchanged.

### 5. Quote correction is intentionally strict

`src/main/java/org/saturn/app/agent/routing/AgentResponseCorrector.java:78-122` validates exact verified catalog entries and otherwise requests a quote-only correction. Ordinary tool-grounded prose fails this path (`:117-121`). The correction logic should not be weakened; only its invocation predicate needs to distinguish successful tool-grounded responses.

## Truth table

The table describes the desired finalizer input. “Successful result” means an `AgentToolResult` with `isError == false`, as recorded by `AgentToolResultCoordinator:65-67`.

| Invocation/request shape | Tool calls | Successful results | `hasSuccessfulCommands()` | Smallest predicate `successfulToolResults().isEmpty()` | Quote-only? | Expected final response |
|---|---:|---:|---:|---:|---:|---|
| TALK/ordinary prose, no tool needed | none | 0 | false | true | **Yes** | verified catalog quote only |
| UNCLASSIFIED/ordinary prose, no tool needed | none | 0 | false | true | **Yes** | verified catalog quote only |
| Successful read-only/SDK tool, e.g. `room_users` | 1+ | 1+ | false | false | **No** | ordinary grounded synthesis/list |
| Successful fresh-history/database tool | 1+ | 1+ | false | false | **No** | ordinary synthesis, subject to freshness validation |
| Successful `run_command` | 1+ | 1+ | true | false | **No** | command result/synthesis, subject to command-channel policy |
| Successful reflected `saturn_<alias>` command tool | 1+ | 1+ | usually false (unless routed through `run_command`) | false | **No** | ordinary tool-grounded synthesis; command exposure remains governed by intent gate |
| Tool call(s), all fail | 1+ | 0 | false | true | **Yes** | quote-only or existing failure/correction behavior; do not treat failure as evidence |
| Tool call succeeds, final model response is empty/invalid | 1+ | 1+ | any | false | **No** | finalizer still rejects empty/invalid response; bypassing quote mode is not bypassing validation |
| `MODERATION` invocation | any | any | any | depends, but finalizer returns silent first | n/a | silent, unchanged |

## Root cause

The old predicate conflated “successful command name recorded” with “any successful tool execution occurred.” `AgentToolResultCoordinator` deliberately records ordinary successful tools in `successfulToolResults`, while `successfulCommands` is populated only for `run_command`. Consequently, a successful `room_users`/SDK execution left `hasSuccessfulCommands() == false`; `DefaultAgentRouter` passed `quoteOnlyRequired == true`; `AgentResponseFinalizer` sent ordinary prose through strict quote correction.

This is a policy seam bug, not a quote-catalog, tool-execution, memory, or enqueue bug. The focused Maven tests currently pass in the worktree, and their logs show both successful non-command tools and quote-only correction paths, but the old test contract included a router case that deliberately expected a successful generic tool response to become a quote. That expectation is incompatible with the new requirement.

## Regression test plan (implementation phase; no edits in Phase 0)

1. **Finalizer seam test — new positive case.** Extend `AgentResponseFinalizerTest` with a direct invocation, ordinary prose, and one successful `AgentToolResult`. Use the explicit boolean overload or the production-equivalent predicate. Assert `shouldReply() == true`, exact ordinary prose, and no quote-correction request.
2. **Preserve genuine quote-only behavior.** Retain `AgentResponseFinalizerTest.rejectsOrdinaryDirectProse` with an empty successful-results list. It must continue to enter quote correction and reject non-quote prose.
3. **Router non-command tool regression.** Update/rename `DefaultAgentRouterTest.routesToolResultsBackToModelAndPersistsCompletedTurn` (currently around lines 56–94) to assert that a successful `room_users` result returns the model’s ordinary prose, not the catalog quote. Keep assertions that the tool message is sent back to the model and evidence is persisted.
4. **Command success regression.** Keep/add a `run_command` success case proving command execution bypasses quote-only. This protects compatibility with existing command-channel behavior and verifies the new predicate does not depend on `hasSuccessfulCommands()`.
5. **Failure non-regression.** Add/retain a tool-error case proving an error-only turn has no successful result and remains quote-only/subject to existing failure handling.
6. **Moderation non-regression.** Retain `AgentResponseFinalizerTest.suppressesModerationResponses`; moderation must remain silent regardless of the quote predicate.
7. **State-ledger boundary.** `AgentToolResultCoordinatorTest` should continue proving successful non-command results enter `successfulToolResults`, while only the `run_command` branch enters `successfulCommands`. This documents why the old predicate was insufficient.
8. **Run sequence.** First run the focused new test(s) RED against HEAD’s old predicate; then apply only the smallest predicate change, rerun focused tests GREEN, and run the relevant agent suite plus `./mvnw spotless:check`, `./mvnw test`, and `./mvnw package` as appropriate.

## Compatibility risks and boundaries

- **Broader ordinary responses:** Any successful tool, including a future tool that returns data but is not user-facing, will suppress quote-only. This follows the explicit requirement (“command/tool executions must bypass”), but each new tool should document whether its result is safe for ordinary synthesis.
- **Successful command tool vs `run_command`:** `successfulToolResults` covers both, while `successfulCommands` covers only `run_command`. Do not reuse `hasSuccessfulCommands()` for response mode; retain it for command-correction bookkeeping.
- **Failed tools:** Keeping failures out of `successfulToolResults` preserves quote-only behavior for turns where no execution actually succeeded. Do not switch to “any tool call occurred.”
- **Validation remains active:** Bypassing quote-only must not bypass empty-response, failure-placeholder, internal-evidence, freshness, sanitization, output-length, or moderation checks.
- **Invocation mode compatibility:** Do not introduce or infer `TALK`/`UNCLASSIFIED` from `DIRECT`, `MENTION`, or `AMBIENT` without a separately defined classifier. Those modes currently govern reply requirement and capabilities only.
- **Command-intent gate compatibility:** `AgentCommandIntentPolicy` controls tool-definition exposure, not final response style. A successful explicitly exposed command still needs ordinary command/tool output; the two policies should remain separate.
- **Docker parity:** `docker ps` showed `saturn` running and healthy (`running`, restarts `0`, image `sha256:734316…`, started `2026-08-19T16:37:00Z`). This is runtime evidence only; it does not establish source/image parity or prove the new worktree predicate is deployed. Rebuild/restart and compare the jar/image before claiming runtime verification.

## Files/evidence inspected

- `AGENTS.md`
- `src/main/java/org/saturn/app/agent/api/AgentInvocationMode.java`
- `src/main/java/org/saturn/app/agent/api/AgentInvocation.java`
- `src/main/java/org/saturn/app/agent/routing/AgentInvocationFactory.java`
- `src/main/java/org/saturn/app/agent/routing/AgentCommandIntentPolicy.java`
- `src/main/java/org/saturn/app/agent/routing/DefaultAgentRouter.java`
- `src/main/java/org/saturn/app/agent/routing/AgentResponseFinalizer.java`
- `src/main/java/org/saturn/app/agent/routing/AgentResponseCorrector.java`
- `src/main/java/org/saturn/app/agent/turn/AgentTurnState.java`
- `src/main/java/org/saturn/app/agent/tool/execution/AgentToolResultCoordinator.java`
- `src/test/java/org/saturn/app/agent/routing/DefaultAgentRouterTest.java`
- `src/test/java/org/saturn/app/agent/routing/AgentResponseFinalizerTest.java`
- Existing diagnostic `.hermes/diagnostics/tool-output-quote-routing-forensics.md`

Focused verification run:

```text
./mvnw -q -Dtest=DefaultAgentRouterTest,AgentResponseFinalizerTest,AgentToolResultCoordinatorTest test
exit code: 0
```

Only this diagnostic file was created by this Phase 0 task. Production and test source files were not modified.
