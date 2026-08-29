# Quote-only mode policy specification

**Phase:** 1 — architecture specification
**Repository:** `/Users/ab/workspace/projects/saturn`
**Branch:** `develop`
**Scope:** Define the smallest policy change that makes quote-only mode apply to no-tool TALK/UNCLASSIFIED-style turns while allowing successful command/tool executions to return ordinary grounded output. This phase must not modify production or test source.

## 1. Decision and invariants

The policy seam is the finalizer argument assembled by `DefaultAgentRouter`:

```java
boolean quoteOnlyRequired = turnState.successfulToolResults().isEmpty();
```

Pass that boolean to the explicit `AgentResponseFinalizer.prepare(...)` overload. Do not introduce `TALK` or `UNCLASSIFIED` into `AgentInvocationMode` and do not add a new classifier: source inspection proves that the current modes (`DIRECT`, `MENTION`, `AMBIENT`, `MODERATION`) describe admission/reply behavior and capabilities, not intent classes. `AgentCommandIntentPolicy` controls command-definition exposure, not final response style.

`successfulToolResults()` is the authoritative execution ledger. `AgentToolResultCoordinator` records every non-error result there, including ordinary SDK/read-only tools and reflected tools. `successfulCommands()`/`hasSuccessfulCommands()` is narrower bookkeeping populated only by the `run_command` branch and must remain available for command-prose correction; it must not determine quote-only mode.

A successful result means an `AgentToolResult` with `isError == false`. A tool call alone, a failed result, a command name, or an available tool definition is not success evidence.

The policy change must preserve:

- strict quote validation/correction for no-success ordinary prose;
- ordinary grounded synthesis after any successful tool execution;
- successful `room_users` delivery and tool evidence persistence;
- successful `run_command` behavior and command-channel bookkeeping;
- failed-tool handling, including required-fresh-tool failure behavior;
- freshness validation, failure-placeholder correction, internal-evidence correction, sanitization, output bounds, no-reply handling, and enqueue/persistence paths;
- moderation silence, regardless of tool results or quote-only predicate.

Bypassing quote-only is not bypassing final validation.

## 2. Exact truth table

The table specifies the boolean passed to the explicit finalizer overload and the expected externally visible behavior. “Successful results” counts entries in `turnState.successfulToolResults()` after coordination.

| Request/turn shape | Tool calls | Successful results | `hasSuccessfulCommands()` | `successfulToolResults().isEmpty()` | Quote-only required? | Expected behavior |
|---|---:|---:|---:|---:|---:|---|
| TALK/ordinary prose, no tool needed | 0 | 0 | false | true | **Yes** | Require a verified catalog quote; ordinary prose remains rejected/corrected. |
| UNCLASSIFIED/ordinary prose, no tool needed | 0 | 0 | false | true | **Yes** | Same strict quote-only behavior as TALK; no new enum/classifier is implied. |
| Successful read-only/SDK tool, specifically `room_users` | 1+ | 1+ | false | false | **No** | Return the model’s ordinary grounded list/synthesis; preserve tool message sent back to the model and persisted tool evidence. |
| Successful fresh-history/database tool | 1+ | 1+ | false | false | **No** | Return ordinary synthesis after required freshness validation; retain fresh-data correction/validation. |
| Successful `run_command` | 1+ | 1+ | true | false | **No** | Return command result/synthesis; retain `successfulCommands` and command-prose policy behavior. |
| Successful reflected `saturn_<alias>` tool not routed through `run_command` | 1+ | 1+ | commonly false | false | **No** | Return ordinary tool-grounded synthesis; command exposure remains governed separately by `AgentCommandIntentPolicy`. |
| Tool call(s), all failed | 1+ | 0 | false (unless prior successful command in same turn) | true | **Yes** when no prior success | Preserve existing failure/correction behavior; failure is not execution evidence. A required fresh-data failure may still throw the existing routing error. |
| Mixed results: at least one success and at least one failure | 1+ | 1+ | any | false | **No** | Successful evidence bypasses quote-only, while existing failure placeholders, freshness, and validation rules remain active. |
| Tool succeeds, final model response is empty/invalid | 1+ | 1+ | any | false | **No** | Do not invoke quote-only solely because of the success, but still reject/correct empty, invalid, leaked, or otherwise disallowed output through existing finalizer checks. |
| `MODERATION` invocation, with any tool/result combination | any | any | any | any | **n/a** | Return silent before reply delivery; moderation silence is independent of quote-only mode and must not regress. |

The router must evaluate the predicate from the turn’s final accumulated ledger, after all tool-result coordination for that turn. Do not use `!turnState.hasSuccessfulCommands()` and do not use “any tool call occurred.”

## 3. Minimal production seam

### Required change

The only intended production behavior change is at `src/main/java/org/saturn/app/agent/routing/DefaultAgentRouter.java`, where the finalizer call currently supplies the quote-only boolean. The seam should remain explicit and local:

```java
responseFinalizer.prepare(
    invocation,
    response,
    messages,
    requiredFreshTool,
    turnState.successfulToolResults(),
    correlationId,
    turnState.successfulToolResults().isEmpty());
```

If readability requires a local variable, it must be a direct alias of the same predicate and must not add mode or prompt classification.

### Explicitly out of scope

- No changes to `AgentInvocationMode`, `AgentInvocation`, or `AgentInvocationFactory`.
- No `TALK` or `UNCLASSIFIED` enum/classifier.
- No changes to `AgentCommandIntentPolicy` or command-definition exposure.
- No changes to `AgentResponseCorrector` quote catalog or correction algorithm.
- No changes to `AgentResponseFinalizer` validation/sanitization/order; its explicit boolean overload is already the correct seam.
- No changes to `AgentTurnState` or `AgentToolResultCoordinator`; their separate ledgers are already sufficient and intentional.
- No transport, memory, enqueue, Docker, or schema changes.

The existing worktree already contains the intended router predicate change. Phase 1 documents the architecture; implementation must separately confirm the diff is task-owned and must not overwrite unrelated dirty files.

## 4. Test-first regression plan

Tests must be written or updated before any production implementation change. For each new/changed test, run it against the old predicate first and confirm a meaningful RED failure; then make only the minimal seam change and rerun GREEN. Do not weaken assertions to accommodate the old behavior.

### A. Finalizer seam: preserve strict no-tool prose rejection

**File:** `src/test/java/org/saturn/app/agent/routing/AgentResponseFinalizerTest.java`

Retain/rename `rejectsOrdinaryDirectProse` as the no-success contract:

- direct/required-reply invocation;
- ordinary response such as `There are users in the room.`;
- empty successful-result list (and the default/explicit quote-only=true path as appropriate);
- assert `AgentRoutingException` with the existing non-quote-prose message and that ordinary prose is not accepted.

Add the complementary positive finalizer seam case:

- same ordinary response and direct invocation;
- one successful `AgentToolResult` in `successfulToolResults`;
- call the explicit overload with `quoteOnlyRequired=false` (the production-equivalent value from the ledger);
- assert `shouldReply() == true`, exact ordinary prose content, and no quote-correction request/extra client response.

This pair proves that the policy changes only the predicate, not quote correction itself.

### B. Router: successful `room_users`

**File:** `src/test/java/org/saturn/app/agent/routing/DefaultAgentRouterTest.java`

Use the existing `routesToolResultsBackToModelAndPersistsCompletedTurn` scenario, with `room_users` returning success and the second model response containing ordinary prose:

- assert the ordinary prose is returned, not a catalog quote;
- assert two LLM requests and that the second request ends with a `tool` message;
- assert the user prompt/context memory append remains present;
- assert the ordinary prose is the final memory append;
- assert tool evidence contains `room_users:room` (or the exact existing evidence representation).

This is the primary regression for the old `hasSuccessfulCommands()` bug: `room_users` succeeds while `hasSuccessfulCommands()` remains false.

### C. Router/coordinator: successful `run_command`

**Files:** `src/test/java/org/saturn/app/agent/routing/DefaultAgentRouterTest.java`; `src/test/java/org/saturn/app/agent/tool/execution/AgentToolResultCoordinatorTest.java`

Retain or add a successful `run_command` case that asserts:

- the command result is recorded in `successfulToolResults()`;
- the command name is recorded in the separate successful-command ledger;
- the final ordinary command result/synthesis bypasses quote-only;
- existing command-prose correction behavior is unchanged.

The coordinator test should continue proving that the result ledger is populated for success and that `run_command` bookkeeping is an additional, narrower side effect—not a replacement for the general result ledger.

### D. Failed tool

**Files:** `src/test/java/org/saturn/app/agent/tool/execution/AgentToolResultCoordinatorTest.java`; router test if needed for end-to-end coverage

Add/retain an error-only tool case:

- coordinate an `AgentToolResult.error(...)` result;
- assert `successfulToolResults().isEmpty()`;
- assert the existing rendered tool message/failure path is preserved;
- for a required fresh-data tool, assert the existing `AgentRoutingException` remains unchanged;
- for a non-required failure followed by ordinary final prose, assert no successful execution evidence means quote-only remains required/subject to existing correction behavior.

Do not change the predicate to “tool calls were present.”

### E. TALK/UNCLASSIFIED/no-tool policy coverage

There is no current TALK/UNCLASSIFIED enum or classifier. Cover the policy at the existing finalizer/router seam rather than inventing those types:

- no tool calls, zero successful results, ordinary prose → quote-only required;
- document the two semantic labels (TALK and UNCLASSIFIED) in test names/comments or a parameterized policy test only if the existing test structure supports it;
- use existing invocation modes only where required by the current API, without asserting that `DIRECT`, `MENTION`, or `AMBIENT` equals TALK/UNCLASSIFIED.

### F. Moderation silence

**File:** `src/test/java/org/saturn/app/agent/routing/AgentResponseFinalizerTest.java`

Retain `suppressesModerationResponses` and strengthen only if necessary to cover a non-empty response and/or successful results. Assert `shouldReply() == false` and empty content. The moderation branch must remain before quote-only correction and independent of the boolean.

### G. Preservation checks

Keep or add focused assertions for:

- required fresh-data validation after successful tool execution;
- sanitization and output-length bounds;
- failure-placeholder and internal-evidence corrections;
- memory append and tool-evidence append/enqueue paths.

These are not alternate policy seams and should not be bypassed by the successful-result predicate.

## 5. Impacted files

### Production (intended)

- `src/main/java/org/saturn/app/agent/routing/DefaultAgentRouter.java` — finalizer call-site predicate only.

### Production (inspected, no intended edits)

- `src/main/java/org/saturn/app/agent/routing/AgentResponseFinalizer.java` — explicit quote-only overload and validation/moderation ordering.
- `src/main/java/org/saturn/app/agent/routing/AgentResponseCorrector.java` — strict quote correction; preserve.
- `src/main/java/org/saturn/app/agent/turn/AgentTurnState.java` — separate `successfulCommands` and `successfulToolResults` ledgers; preserve.
- `src/main/java/org/saturn/app/agent/tool/execution/AgentToolResultCoordinator.java` — authoritative success recording and `run_command` bookkeeping; preserve.
- `src/main/java/org/saturn/app/agent/api/AgentInvocationMode.java` — proves no TALK/UNCLASSIFIED classifier exists; do not extend.
- `src/main/java/org/saturn/app/agent/routing/AgentCommandIntentPolicy.java` — command exposure only; do not conflate with response style.

### Tests (intended updates/additions)

- `src/test/java/org/saturn/app/agent/routing/DefaultAgentRouterTest.java`
- `src/test/java/org/saturn/app/agent/routing/AgentResponseFinalizerTest.java`
- `src/test/java/org/saturn/app/agent/tool/execution/AgentToolResultCoordinatorTest.java`

### Documentation artifact created by this phase

- `.hermes/specs/quote-only-mode-policy-spec.md`

No production or test source is to be modified in Phase 1.

## 6. Verification commands

### Phase 1 documentation-only verification

Run from `/Users/ab/workspace/projects/saturn`:

```bash
git diff --check

git status --short

git diff -- .hermes/specs/quote-only-mode-policy-spec.md
```

Confirm that only the requested specification is newly created by this phase and that pre-existing dirty files remain untouched.

### Implementation-phase RED/GREEN sequence

```bash
./mvnw -q -Dtest=AgentResponseFinalizerTest#rejectsOrdinaryDirectProse test
./mvnw -q -Dtest=DefaultAgentRouterTest#routesToolResultsBackToModelAndPersistsCompletedTurn test
./mvnw -q -Dtest=AgentToolResultCoordinatorTest test
```

The relevant new/changed test must fail against the old `!turnState.hasSuccessfulCommands()` predicate for successful `room_users`, then pass after the minimal predicate change. The no-tool, failed-tool, command-success, and moderation cases must remain green.

Then run the focused suite and project gates:

```bash
./mvnw -q -Dtest=DefaultAgentRouterTest,AgentResponseFinalizerTest,AgentToolResultCoordinatorTest test
./mvnw spotless:check
./mvnw test
./mvnw package
```

Use `./mvnw package` when the assembled artifact is relevant. Do not claim Docker/runtime parity from these source tests alone; if deployment verification is later requested, rebuild/restart the intended image and compare its jar/image identity with the tested source.

## 7. Acceptance criteria

- No new TALK/UNCLASSIFIED enum or classifier exists.
- Quote-only is true exactly when the final successful-result ledger is empty, except that moderation remains silent independently.
- Successful `room_users`, successful `run_command`, and any other successful tool result bypass quote-only.
- No-tool TALK/UNCLASSIFIED semantics still reject ordinary prose and require a verified quote.
- Failed tools do not count as success evidence.
- Freshness, sanitization, correction, moderation, persistence, and enqueue behavior remain intact.
- Focused tests demonstrate the old predicate’s failure and the new predicate’s minimal correction; full Maven verification passes.
