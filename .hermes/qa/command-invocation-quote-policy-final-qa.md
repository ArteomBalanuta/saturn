# Command Invocation Quote Policy — Phase 3 Final QA

**Result:** PASS
**Repository:** `/Users/ab/workspace/projects/saturn`
**Baseline:** `428fa3becee1877368ee920075889d9844efcf0a`

## Commands and exact results

All commands were run from the repository root and returned exit code 0:

```text
./mvnw -q -Dtest=LUserCommandImplTest,DefaultAgentRouterTest,AgentInvocationTest,AgentInvocationFactoryTest,AgentResponseFinalizerTest,AgentToolResultCoordinatorTest,AgentFreshDataCoordinatorTest test
focused tests=102 failures=0 errors=0 skipped=5

./mvnw -q clean compile
BUILD SUCCESS (exit code 0)

./mvnw -q test
BUILD SUCCESS (605 tests, 0 failures, 0 errors, 5 skipped)

./mvnw -q package
BUILD SUCCESS; shaded artifact produced at target/saturn.jar

./mvnw -q spotless:check
BUILD SUCCESS; no formatting violations

git diff --check
PASS; no whitespace errors
```

The final full-gate command was also run as one verified chain:

```text
./mvnw -q clean compile && ./mvnw -q test && ./mvnw -q package && ./mvnw -q spotless:check && git diff --check
```

It returned exit code 0.

## Policy and compatibility review

- `AgentInvocation.commandOriginated` is the final record component; the prior constructors remain available and default to `false`.
- `AgentInvocationFactory.create(..., mode)` remains available and delegates with `false`; the boolean overload preserves explicit origin.
- Only `LUserCommandImpl` supplies `true` at the command-wrapper boundary.
- The router predicate is exactly `!invocation.commandOriginated() && turnState.successfulToolResults().isEmpty()`.
- Ordinary compatibility/DIRECT requests retain quote-only behavior when there is no successful tool evidence.
- Existing MENTION and AMBIENT routing coverage was exercised in `DefaultAgentRouterTest`; ordinary invocations do not gain a bypass from their mode.
- Successful tool evidence bypasses quote-only as before, including successful `run_command` coverage.
- A command-originated response with no tool call bypasses quote-only.
- A command-originated response after an all-failed tool batch bypasses quote-only; regression test `commandOriginatedAllFailedToolsBypassQuoteOnlyFinalization` passes.
- Failed tool results remain absent from the successful evidence ledger; ordinary failed-only invocations still require quote-only finalization.
- Moderation remains silent through the existing finalizer path, regardless of origin/evidence.
- No changes were made to invocation modes, capabilities, context/current-message construction, fresh-data coordination, tool ledgers, or finalizer contracts.

## Touched paths owned by this task

### Production

- `src/main/java/org/saturn/app/agent/api/AgentInvocation.java`
- `src/main/java/org/saturn/app/agent/routing/AgentInvocationFactory.java`
- `src/main/java/org/saturn/app/agent/routing/DefaultAgentRouter.java`
- `src/main/java/org/saturn/app/command/impl/user/LUserCommandImpl.java`

### Tests

- `src/test/java/org/saturn/app/agent/AgentInvocationTest.java`
- `src/test/java/org/saturn/app/agent/routing/AgentInvocationFactoryTest.java`
- `src/test/java/org/saturn/app/agent/routing/DefaultAgentRouterTest.java`
- `src/test/java/org/saturn/app/command/impl/user/LUserCommandImplTest.java`

### QA

- `.hermes/qa/command-invocation-quote-policy-final-qa.md`

## Repository hygiene

- `git diff --check` passed.
- The complete diff contains only the four intended production files and four intended test files; this QA report is the only additional task-owned path.
- Existing unrelated dirty/untracked files (IDE metadata, local configuration, database/runtime artifacts, diagnostics/specs/QA documents, and caches) were preserved and not modified.
- A scan of changed task-owned paths found no API-key, secret, password, or token assignment patterns.
- No commit or push was performed.

**Final QA disposition: PASS.**
