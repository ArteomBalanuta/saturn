# Command Invocation Quote Policy — Phase 2 Implementation QA

**Baseline:** `428fa3becee1877368ee920075889d9844efcf0a0`

## Implementation

- Added `commandOriginated` as the final `AgentInvocation` record component.
- Preserved all existing constructors with `false` defaults and retained the prior five-argument constructor.
- Added the boolean `AgentInvocationFactory.create` overload; the existing overload delegates with `false`.
- Changed only `LUserCommandImpl` to pass `true`.
- Updated the router finalization predicate to:

```java
!invocation.commandOriginated() && turnState.successfulToolResults().isEmpty()
```

- Preserved mode, capabilities, current message text, prompt/context construction, tool ledgers, finalizer APIs, and moderation behavior.

## TDD evidence

### RED

Command:

```text
./mvnw -Dtest=DefaultAgentRouterTest#commandOriginatedNoToolResponseBypassesQuoteOnlyPolicy test
```

Result: **RED**. Test compilation failed because the six-argument `AgentInvocation` constructor did not exist. This was the expected missing-origin API failure against the baseline.

### GREEN

Command:

```text
./mvnw -Dtest=LUserCommandImplTest,DefaultAgentRouterTest,AgentInvocationTest,AgentInvocationFactoryTest test
```

Result: **GREEN** — 80 tests run, 0 failures, 0 errors, 5 skipped.

Expanded focused command/routing/finalizer/coordinator run:

```text
./mvnw -Dtest=LUserCommandImplTest,DefaultAgentRouterTest,AgentInvocationTest,AgentInvocationFactoryTest,AgentResponseFinalizerTest,AgentToolResultCoordinatorTest test
```

Result: **GREEN** — 92 tests run, 0 failures, 0 errors, 5 skipped.

## Verification

- `./mvnw clean compile` — **BUILD SUCCESS**
- `./mvnw spotless:check` — **BUILD SUCCESS**, 0 files needing changes
- `./mvnw test` — **BUILD SUCCESS**, 605 tests run, 0 failures, 0 errors, 5 skipped
- `./mvnw package` — **BUILD SUCCESS**, shaded JAR produced at `target/saturn.jar`
- `git diff --check` — **PASS**

## Task-owned paths

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

- `.hermes/qa/command-invocation-quote-policy-implementation.md`

Existing unrelated dirty/untracked files were preserved and not modified.
