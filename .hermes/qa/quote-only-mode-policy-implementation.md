# Quote-only mode policy implementation

**Phase:** 2 — implementation and regression coverage
**Repository:** `/Users/ab/workspace/projects/saturn`
**Branch:** `develop`
**Date:** 2026-08-19

## Result

The prior task-owned router seam was already the required minimal production change:

```java
turnState.successfulToolResults().isEmpty()
```

No additional production change was needed. No `TALK` or `UNCLASSIFIED` enum/classifier was introduced.

## Strict TDD evidence

- **RED:** Temporarily ran `DefaultAgentRouterTest#routesToolResultsBackToModelAndPersistsCompletedTurn` against the old predicate `!turnState.hasSuccessfulCommands()`. The successful `room_users` case attempted quote-only correction and failed with `AgentRoutingException: Agent provider failed: No scripted response`, proving the regression was meaningful.
- Restored the task-owned predicate `turnState.successfulToolResults().isEmpty()`.
- **GREEN:** Focused regression tests passed with the successful-result ledger predicate.

## Coverage added or strengthened

- No-tool ordinary prose remains strict quote-only through the existing `rejectsOrdinaryDirectProse` test; this represents the TALK/UNCLASSIFIED semantics without inventing enums.
- Finalizer explicit positive seam test proves ordinary prose is accepted when a successful `room_users` result is present.
- Existing router `room_users` success coverage continues to assert ordinary prose, tool round-trip, memory persistence, and tool evidence.
- Existing successful `run_command` router/coordinator coverage remains green and continues to assert command bookkeeping and ordinary grounded output.
- Added router coverage proving failed-only `room_users` execution still triggers quote-only finalization.
- Added coordinator coverage proving error results do not populate `successfulToolResults()`.
- Moderation finalizer coverage now includes successful tool evidence while still asserting silent output.

## Touched paths

- `src/main/java/org/saturn/app/agent/routing/DefaultAgentRouter.java` — preserved prior task-owned predicate; no net new production behavior beyond the existing diff.
- `src/test/java/org/saturn/app/agent/routing/DefaultAgentRouterTest.java` — failed-only router regression coverage.
- `src/test/java/org/saturn/app/agent/routing/AgentResponseFinalizerTest.java` — successful-evidence bypass and moderation-silence coverage.
- `src/test/java/org/saturn/app/agent/tool/execution/AgentToolResultCoordinatorTest.java` — failed-only ledger coverage.
- `.hermes/qa/quote-only-mode-policy-implementation.md` — this report.

Pre-existing unrelated dirty/untracked paths were preserved and not modified.

## Verification

All commands completed successfully (exit code 0):

```text
./mvnw -q -Dtest=DefaultAgentRouterTest,AgentResponseFinalizerTest,AgentToolResultCoordinatorTest test
./mvnw -q -DskipTests compile
./mvnw -q spotless:check
./mvnw -q test
./mvnw -q package
```

`git diff --check` was clean before and after the implementation work. Full tests and package emitted existing test/runtime log warnings, but no test failures or build errors.
