# Command Invocation Quote Policy — Phase 1 Architecture Specification

**Status:** architecture specification only; implementation is intentionally deferred.

**Repository:** `/Users/ab/workspace/projects/saturn`

**Baseline:** `428fa3becee1877368ee920075889d9844efcf0a`

## Scope and invariant

Fix the finalization policy for explicit `*l` command invocations that produce no successful tool result. The required predicate is:

```java
boolean quoteOnlyRequired =
    !invocation.commandOriginated() && turnState.successfulToolResults().isEmpty();
```

The origin must be carried explicitly from the command wrapper. Do **not** infer it from `DIRECT`, prompt text, `currentMessageText`, aliases, mode, or tool names. Do **not** add `TALK`, `UNCLASSIFIED`, or any other invocation mode/classifier. `AgentInvocationMode` semantics remain unchanged.

The change must preserve quote-only behavior for ordinary no-tool invocations, successful-tool bypass, moderation silence, capabilities, prompt/context construction, current-message text, fresh-data handling, tool ledgers, finalizer behavior, and response correction behavior outside this predicate.

## Root cause and selected seam

`LUserCommandImpl` currently calls the four-argument factory with `AgentInvocationMode.DIRECT`. `DIRECT` is also used by compatibility constructors and ordinary programmatic requests. `AgentInvocationFactory` preserves message text but drops the fact that the command wrapper created the request. At `DefaultAgentRouter` finalization, an explicit command with zero successful tools is therefore indistinguishable from an ordinary direct/no-tool request.

The authoritative source is the `LUserCommandImpl` call site. Preserve that fact in `AgentInvocation` through a backward-compatible boolean component and factory overload. Keep the policy at the router seam; do not move it into command parsing or alter the finalizer API.

## Exact constructor and factory compatibility map

### `AgentInvocation`

Current record components:

```java
(String requestId,
 AgentContext context,
 String prompt,
 AgentInvocationMode mode,
 String currentMessageText)
```

Proposed record components, in this order:

```java
(String requestId,
 AgentContext context,
 String prompt,
 AgentInvocationMode mode,
 String currentMessageText,
 boolean commandOriginated)
```

The six-argument record canonical constructor is the new implementation constructor. Its existing validation remains unchanged; `commandOriginated` requires no validation.

Because changing record components changes the generated canonical constructor, explicitly retain the old five-argument constructor as a source-compatible overload:

| Signature | Compatibility/default | Delegation |
|---|---|---|
| `AgentInvocation(String, AgentContext, String, AgentInvocationMode, String)` | Existing caller behavior; defaults `commandOriginated = false` | `this(requestId, context, prompt, mode, currentMessageText, false)` |
| `AgentInvocation(String, AgentContext, String, AgentInvocationMode, String, boolean)` | New canonical constructor; explicit origin | Performs existing validation |
| `AgentInvocation(String, AgentContext, String)` | Existing compatibility constructor; defaults `DIRECT`, `null`, `false` | `this(requestId, context, prompt, DIRECT, null, false)` |
| `AgentInvocation(String, AgentContext, String, AgentInvocationMode)` | Existing compatibility constructor; defaults `currentMessageText = null`, `false` | `this(requestId, context, prompt, mode, null, false)` |
| `AgentInvocation(AgentContext, String)` | Existing convenience constructor; generates UUID, defaults `DIRECT`, `null`, `false` | `this(UUID.randomUUID().toString(), context, prompt, DIRECT, null, false)` |
| `AgentInvocation(AgentContext, String, AgentInvocationMode)` | Existing convenience constructor; generates UUID, defaults `currentMessageText = null`, `false` | `this(UUID.randomUUID().toString(), context, prompt, mode, null, false)` |

The generated accessor `commandOriginated()` is the only new model API. All existing accessors and record equality/component order before the new component remain intact. Existing construction sites compile unchanged and represent non-command-originated invocations.

### `AgentInvocationFactory`

Retain the existing overload and add exactly one overload with the origin bit:

| Signature | Compatibility/default | Result |
|---|---|---|
| `create(EngineImpl engine, ChatMessage message, String prompt, AgentInvocationMode mode)` | Existing API; defaults `commandOriginated = false` | Delegates to the five-argument overload |
| `create(EngineImpl engine, ChatMessage message, String prompt, AgentInvocationMode mode, boolean commandOriginated)` | New API | Builds the same context/capabilities/current message text and passes the explicit bit to `AgentInvocation` |

Only `LUserCommandImpl` changes caller behavior: call the new overload with `true`. All room-pipeline, automation, moderation, and other factory callers continue using the old overload and therefore remain `false`.

## Minimal production change map (implementation phase)

No production files are changed in Phase 1. The implementation phase should touch only:

1. `src/main/java/org/saturn/app/agent/api/AgentInvocation.java`
   - Add `boolean commandOriginated` as the final record component.
   - Add the explicit five-argument compatibility constructor and update all existing convenience constructors to delegate with `false`.
   - Preserve validation and all existing component values.
2. `src/main/java/org/saturn/app/agent/routing/AgentInvocationFactory.java`
   - Keep the existing four-argument `create` method.
   - Add the five-argument boolean overload.
   - Make the old overload delegate with `false`; pass the boolean only into the new invocation constructor.
3. `src/main/java/org/saturn/app/command/impl/user/LUserCommandImpl.java`
   - Change only the factory call to pass `true`.
   - Keep `AgentInvocationMode.DIRECT`, prompt rendering, authorization, availability checks, submission, and logging unchanged.
4. `src/main/java/org/saturn/app/agent/routing/DefaultAgentRouter.java`
   - Change only the final `quoteOnlyRequired` argument from `turnState.successfulToolResults().isEmpty()` to `!invocation.commandOriginated() && turnState.successfulToolResults().isEmpty()` (prefer a local boolean if needed for readability).
   - Do not alter tool-loop, ledger, fresh-data, finalizer, or corrector logic.

No changes are required to `AgentInvocationMode`, `AgentResponseFinalizer`, `AgentTurnState`, `AgentToolResultCoordinator`, service boundaries, prompt assembly, or persistence.

## Test-first plan: RED → minimal GREEN

Tests are to be added/changed only in the implementation phase. Each RED must be run and fail for the missing origin propagation/predicate before production changes; then implement the smallest corresponding slice and rerun GREEN.

### RED/GREEN 1 — explicit command, no tool (primary regression)

**Test location:** `src/test/java/org/saturn/app/agent/routing/DefaultAgentRouterTest.java`

- **RED:** construct an invocation through the proposed explicit-origin constructor/API with `commandOriginated = true`; use a scripted LLM response containing ordinary prose and no tool call. Route it. Assert the ordinary prose is returned unchanged (rather than quote correction being requested/failing validation). On baseline, this must fail because the router sees zero successful tools and requests quote-only.
- **GREEN:** after the minimal model/router change, the same command-originated no-tool turn returns ordinary prose and does not enter strict quote-only correction.
- Keep the test at the router/finalizer boundary and use the existing test fixtures. Do not assert implementation details beyond the observable finalization behavior.

### RED/GREEN 2 — command wrapper propagates origin

**Test location:** `src/test/java/org/saturn/app/command/impl/user/LUserCommandImplTest.java`, existing `submitsPromptAndRoomContextToAgentService`.

- **RED:** add `assertTrue(invocation.commandOriginated())`; baseline does not compile until the accessor exists, or fails once the accessor is temporarily represented as the expected missing API.
- **GREEN:** `LUserCommandImpl` uses the boolean factory overload with `true`; retain all existing assertions for prompt, `DIRECT`, room/context, users, whisper, and capabilities.

This proves the bit is set only at the authoritative command-wrapper boundary, not inferred later.

### RED/GREEN 3 — ordinary direct/no-tool control

**Test location:** `src/test/java/org/saturn/app/agent/routing/DefaultAgentRouterTest.java` (or the nearest existing no-tool finalization test).

- **RED:** route an ordinary `new AgentInvocation(context(), "ordinary prose")` using the existing compatibility constructor, with no tool call. Assert existing quote-only behavior remains (use the established quote fixture/assertion, such as the expected quote-correction validation/rejection for non-catalog prose).
- **GREEN:** old constructors default `commandOriginated` to `false`, and the router still requests quote-only when successful tool results are empty.

This prevents accidentally bypassing quote-only for every `DIRECT` invocation.

### Compatibility and preservation coverage

- `src/test/java/org/saturn/app/agent/routing/AgentInvocationFactoryTest.java`: add coverage that the old four-argument factory overload yields `commandOriginated() == false` and the new five-argument overload preserves `true`; retain current mode, context, capability, and current-message assertions.
- `src/test/java/org/saturn/app/agent/AgentInvocationTest.java`: cover the old five-argument constructor defaulting false if constructor compatibility is not already covered by factory tests; retain validation tests.
- `DefaultAgentRouterTest`: retain the existing successful `room_users` case (successful tool results bypass quote-only), and add/retain the all-tools-failed command case to prove `true` bypasses quote-only even with zero successful results while normal failure validation remains intact.
- Existing finalizer/coordinator/turn-state/service tests remain unchanged in meaning. In particular, do not repurpose `successfulCommands()` or `successfulToolResults()` as origin signals; they remain execution ledgers.
- Moderation tests remain unchanged: moderation is silent at the existing finalizer path, regardless of the new bit.

## Truth table

`successfulToolResults().isEmpty()` means no non-error tool result was recorded. “Quote-only” means strict verified-catalog quote correction is requested by the router/finalizer path.

| Invocation shape | `commandOriginated` | Successful tool results | Required quote-only | Expected result |
|---|---:|---:|---:|---|
| Ordinary room prose / ambient or compatibility request | `false` | `0` | **Yes** | Existing quote-only behavior |
| Ordinary `DIRECT` compatibility invocation | `false` | `0` | **Yes** | Existing quote-only behavior; proves `DIRECT` is not origin |
| Ordinary `MENTION` with no tool | `false` | `0` | **Yes** | Existing quote-only behavior, subject to reply rules |
| Explicit `*l ...`, no tool selected | `true` | `0` | **No** | Return ordinary command response; no quote-only conversion |
| Explicit `*l ...`, tool calls all fail | `true` | `0` | **No** | Preserve normal failure/response validation, but no quote-only conversion |
| Explicit `*l ...`, one or more successful tools | `true` | `1+` | **No** | Normal grounded command response |
| Ordinary room request with successful non-command tool | `false` | `1+` | **No** | Existing grounded synthesis |
| Ordinary request with successful `run_command` | `false` | `1+` | **No** | Existing command-channel behavior |
| Moderation invocation | any / irrelevant | any | **n/a** | Existing moderation silence remains unchanged |

Equivalent policy:

| `commandOriginated` | `successfulToolResults().isEmpty()` | `quoteOnlyRequired` |
|---:|---:|---:|
| `false` | `true` | `true` |
| `false` | `false` | `false` |
| `true` | `true` | `false` |
| `true` | `false` | `false` |

## Verification commands

Phase 1 verification must not modify production or test source. Verify only the specification artifact and repository hygiene:

```bash
cd /Users/ab/workspace/projects/saturn
git diff --check -- .hermes/specs/command-invocation-quote-policy-spec.md
git status --short -- .hermes/specs/command-invocation-quote-policy-spec.md src/main src/test
```

Implementation-phase verification, after RED/GREEN changes exist, in the prescribed order:

```bash
cd /Users/ab/workspace/projects/saturn
./mvnw -Dtest=LUserCommandImplTest,DefaultAgentRouterTest,AgentInvocationTest,AgentInvocationFactoryTest test
./mvnw spotless:check
./mvnw test
./mvnw package
git diff --check
git status --short
```

If a test class is absent in a future checkout, omit only that class from the focused `-Dtest` selector; do not replace it with broad or fabricated coverage. Phase 1 does not claim these implementation tests were run because no source changes were made.

## Explicit non-goals

- No `TALK`, `UNCLASSIFIED`, `COMMAND`, or other new `AgentInvocationMode` values.
- No origin inference from text, prefixes, aliases, prompts, `DIRECT`, message mode, or tool names.
- No changes to capabilities, authorization, moderation, fresh-data requirements, tool result ledgers, prompts, current-message text, finalizer/corrector contracts, or room automation.
- No production/test source edits in this phase.
