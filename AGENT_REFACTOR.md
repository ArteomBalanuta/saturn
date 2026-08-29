# Agent Package Refactor Plan

> **Status: completed.** This is the implementation ledger and evidence record for the refactor.
> Use [`AGENTIC_ARCHITECTURE.md`](AGENTIC_ARCHITECTURE.md) for the current request lifecycle,
> package map, extension guide, tests, and troubleshooting.

## Purpose

This document records the implemented object-oriented refactor of
`org.saturn.app.agent`. The goal is to reduce orchestration complexity while preserving Saturn's
current routing, command-delivery, memory, moderation, and tool-ordering behaviour.

This is deliberately incremental. The agent controls user-visible behaviour, so each stage must
preserve public contracts and land with focused regression coverage before the next begins.

## Constraints

- Keep `AgentRouter`, `AgentTool`, `AgentToolRegistry`, and command-gateway public APIs backward
  compatible unless a bug cannot be fixed otherwise.
- Preserve deterministic serialization by memory key through the fixed striped-lock manager and
  keep execution state request-local.
- Keep command and other action tools sequential. Only independent, read-only, idempotent calls
  may fan out.
- Retain tool observations in provider order, even when a read batch runs in parallel.
- Keep prompt text in `src/main/resources/agent`; Java classes select prompts but must not embed
  agent instructions.
- Avoid changing persistence, command implementations, or protocol delivery while refactoring the
  agent package.

## Completed Foundations

These changes are already implemented in the current working tree and form the base for the
completed stages.

| Area | Extracted collaborator | Responsibility |
| --- | --- | --- |
| Turn state | `AgentTurnState` | Request-local budgets, correction flags, command outcomes, and tool evidence. |
| Session locking | `AgentSessionLockManager` | Fair striped locking and unlock lifecycle for shared memory keys. |
| Tool-result rendering | `AgentModelVisibleToolResultRenderer` | Converts executed tool outcomes into model-visible envelopes, including room-delivery results. |
| Fresh-data final validation | `AgentFreshDataFinalValidator` | Validates that required fresh-data evidence is present before response finalization. |
| Fresh-data policy gating | `AgentFreshDataTurnPolicy` | Stops later response policies until the required fresh-data tool has succeeded. |
| Tool scheduling | `AgentToolCallScheduler` | Sequential barriers plus ordered fan-out for contiguous safe read calls. |
| Response recovery | `AgentResponseCorrector` | Bounded recovery from failure placeholders and narrated, unverified actions. |
| Configuration values | `AgentConfigValueReader` | Shared TOML/environment scalar parsing and checked numeric conversion for agent configuration records. |
| Tool-call validation | `AgentToolCallValidator` | Contextual tool resolution, authorization ordering, argument/schema validation, and canonical invocation identity. |
| Tool arguments | `AgentToolArgumentReader` | Shared trimmed, non-blank JSON string extraction without taking ownership of tool-specific error policy. |

## Implemented Stages

### 1. Complete Response-Recovery Boundary

**Status: completed.** `AgentResponseCorrector` owns
initial completion, stale duplicate detection, and the single cache-bypass retry. The router only
delegates this recovery decision. Focused tests cover the collaborator directly and router tests
cover the end-to-end behaviour.

**Problem:** `DefaultAgentRouter` still owns stale-response detection, fresh-synthesis checks, and
several correction prompt branches. These policies are logically response recovery, but are mixed
with the tool-loop control flow.

**Design:** Expand `AgentResponseCorrector` into a cohesive recovery policy collaborator. Move
stale duplicate detection and cache-bypass retry there first. Then extract fresh-synthesis response
validation as a small policy interface, for example `AgentResponseRequirement`, with focused
implementations for required fresh tool output and fresh user-history synthesis.

**Steps:**

1. Move `completeInitialRequest`, stale duplicate detection, and cache-bypass retry from
   `DefaultAgentRouter` to `AgentResponseCorrector`.
2. Introduce `AgentResponseRequirement` only after the direct extraction is covered by tests.
3. Model each requirement as `validate(response, turnState)` plus an optional correction request;
   preserve the current one-retry bounds in `AgentTurnState`.
4. Keep final response sanitation outside recovery; recovery decides whether a response is valid,
   while `AgentResponseSanitizer` changes presentation only.

**Acceptance criteria:**

- Stale cached replies are retried exactly once without prompt caching.
- Fresh profile answers still require successful `user_message_history` evidence.
- The router no longer contains response-content matching logic other than final no-reply handling.
- Existing stale-response, profile, and failure-placeholder tests pass.

### 2. Extract Router Turn Policies

**Status: completed.** `AgentCommandChannelPolicy`, `AgentFreshDataPolicy`,
`AgentToolBudgetPolicy`, `AgentResponseCorrector`, and `AgentTurnState` isolate command correction,
fresh evidence, budget exhaustion, response recovery, and mutable turn state. `AgentTurnPolicyChain`
now owns deterministic policy ordering, response propagation, and explicit short-circuiting. It
applies `AgentFreshDataTurnPolicy`, unverified-action correction, and command-channel enforcement in
that order. The router no longer owns the fresh-evidence gate, while provider calls, tool execution,
and observations remain in one stateful session loop.

**Problem:** `DefaultAgentRouter.routeInSession` contains routing, tool-loop progression,
fresh-data enforcement, command prose correction, response finalization, and persistence. It is
still the largest and most branch-heavy class in the package.

**Design:** Keep `DefaultAgentRouter` as the session coordinator, but introduce an ordered
Chain-of-Responsibility for turn policies. A policy receives an immutable turn input plus
`AgentTurnState`, and may accept the current response, request a correction, require a tool call,
or terminate with a routing error.

**Proposed collaborators:**

- `AgentFreshDataPolicy`: requires and validates a configured fresh lookup before synthesis.
- `AgentCommandChannelPolicy`: prevents command prose and requires `run_command` when needed.
- `AgentToolBudgetPolicy`: handles tool budget exhaustion and finalization without exposing tools.
- `AgentTurnPolicyChain`: applies policies in deterministic order.

**Steps:**

1. Introduce a package-private `AgentTurnPolicy` contract with an explicit result type instead of
   Boolean flags or nullable replacements.
2. Move one existing branch at a time, beginning with command prose enforcement because it already
   has clear inputs and outcomes.
3. Make policy ordering explicit in a factory owned by the router or runtime composition root.
4. Leave provider calls and tool execution in the router until policy inputs and outputs are proven
   stable; do not create policies that own hidden LLM session state.

**Acceptance criteria:**

- The order of fresh-data, unverified-action, and command-channel enforcement is documented and
  test-covered.
- A policy cannot execute a tool or persist memory directly.
- `DefaultAgentRouter` reads as a short session loop: assemble, complete, apply policy chain,
  execute calls, observe, finalize, persist.

### 3. Split Tool Invocation into Validation, Execution, and Accounting

**Status: completed.** `AgentToolCallValidator` produces immutable `ValidatedToolCall` values,
`AgentToolExecutionLedger` owns synchronized request accounting, and `AgentToolInvoker` owns
timeout-bound virtual-thread invocation. The scheduler and invoker share one request-scoped
thread-per-task virtual-thread executor. The scheduler owns batch fan-out and ordered collection but
does not close the injected executor; the invoker performs the single shutdown when the facade
closes.

**Problem:** `AgentToolExecutor` currently combines registry lookup, descriptor retrieval, JSON
parsing, schema validation, duplicate detection, prerequisite checks, timeouts, failure accounting,
and execution. This makes isolated extension and diagnostics harder.

**Design:** Keep `AgentToolExecutor` as the public request-scoped facade. Delegate to narrowly
defined package-private collaborators:

- `AgentToolCallValidator`: resolves a tool and returns a validated invocation or a standard
  `AgentToolResult` error.
- `AgentToolExecutionLedger`: owns duplicates, in-flight calls, success prerequisites, limits, and
  failure-disable state.
- `AgentToolInvoker`: applies per-tool timeout and converts thrown exceptions into the standard
  result envelope.
- `AgentToolCallScheduler`: remains the scheduling Strategy and consumes the facade callback.

**Steps:**

1. Introduce an immutable `ValidatedToolCall` carrying tool, descriptor, parsed arguments, and
   canonical invocation key.
2. Move the current validation path without changing error codes or error messages.
3. Move mutable maps and sets into the ledger; preserve the existing internal locking semantics.
4. Extract timeout execution into the invoker and use one request-scoped virtual-thread executor.
5. Remove the executor's duplicate scheduling executor if the scheduler can share the request
   executor safely; otherwise document why separate pools remain necessary.

**Acceptance criteria:**

- Schema/argument failures occur before invocation and retain current envelope codes.
- Timeout, interruption, and exception paths are test-covered independently of routing.
- Concurrent read-only scheduling cannot bypass duplicate or prerequisite checks.
- Commands remain strict sequential barriers.

### 4. Normalize Tool Capability Classification

**Status: completed.** `AgentToolExecutionPolicy` and `AgentToolExecutionMode` provide the
single classification source used by `AgentToolExecutor`. `AgentScheduledToolCall` carries that
classification into the scheduler, so scheduling never resolves tools or interprets metadata.
Stable tool identity is validated at registration; contextual descriptors validate when they are
materialized for a caller because availability and schemas may depend on caller context.

**Problem:** eligibility for parallel execution is derived ad hoc in `AgentToolExecutor`, while
tool descriptors already carry behaviour metadata. The separation is correct in principle but not
yet a named policy that can be independently audited.

**Design:** Introduce `AgentToolExecutionPolicy` as a Strategy that classifies a resolved
descriptor into `SEQUENTIAL_ACTION`, `SEQUENTIAL_DEPENDENT_READ`, or `PARALLEL_READ`.

**Steps:**

1. Make the policy the single source of truth for `readOnly`, `isIdempotent`, and prerequisite
   interpretation.
2. Validate stable identity during registration. Validate contextual descriptor consistency when a
   caller's definitions are built and when execution materializes the descriptor.
3. Have the scheduler receive already classified calls rather than re-resolving tools for its
   predicate.
4. Document each built-in tool's classification and expected ordering.

**Acceptance criteria:**

- Tool classification is unit-tested without LLM or executor setup.
- A read-only but non-idempotent tool never joins a parallel batch.
- Unknown or invalid descriptors safely become sequential errors, not parallel work.

### 5. Decompose Room Automation Event Handling

**Status: completed.** `AgentRoomMessagePipeline` applies moderation, eligibility, invocation
preparation, quiet requests, mentions, semantic moderation, and ambient participation as an
ordered chain. `DefaultAgentRoomAutomation` remains the compatibility facade.

**Problem:** `DefaultAgentRoomAutomation` handles moderation monitoring, bot filtering, mentions,
quiet requests, semantic moderation, ambient participation sampling, and submissions in one
method. This couples unrelated room-event policies and makes event precedence difficult to reason
about.

**Design:** Introduce a room-event Chain-of-Responsibility. Each handler returns an explicit
`PASS` or `CLAIMED` outcome and never calls unrelated handlers.

**Proposed handlers, in required order:**

1. `ModerationMonitorHandler`: observes all messages and executes deterministic moderation
   decisions.
2. `MessageEligibilityHandler`: skips empty, whispered, bot-authored, and command messages.
3. `QuietRequestHandler`: registers polite silence requests without generating a reply.
4. `MentionInvocationHandler`: submits direct mentions and claims the event.
5. `SemanticModerationHandler`: submits qualifying severe-abuse messages in moderation mode.
6. `AmbientParticipationHandler`: applies quiet state and sampling before an ambient submission.

**Acceptance criteria:**

- Event precedence matches current production behaviour and has table-driven tests.
- A quiet request produces no acknowledgement or later ambient reply during its duration.
- Mention handling remains direct; ambient sampling never steals a mention.
- Join monitoring is represented separately from message handling.

### 6. Make Runtime Composition Explicit

**Status: completed.** Infrastructure, registry, router, and room automation construction use
dedicated factories. `ProtectedPrincipalPolicy` centralizes creator, admin, host, bot, and replica
exemptions.

**Problem:** `AgentRuntimeFactory` is a static composition root that also owns protected-user
policy, tool assembly, persistence adapters, provider construction, and room-automation wiring.
Its long construction path is difficult to test or evolve.

**Design:** Keep `AgentRuntimeFactory.create(...)` as the compatibility entry point, but delegate
to factories with single composition responsibilities.

**Proposed collaborators:**

- `AgentInfrastructureFactory`: memory, query, schema, and SQL adapters.
- `AgentToolRegistryFactory`: built-in tools, command catalog registration, and registry freeze.
- `AgentRouterFactory`: provider client, request assembly, policies, and router construction.
- `AgentRoomAutomationFactory`: protected-principal policy, monitoring, and event-handler chain.
- `ProtectedPrincipalPolicy`: centralizes creator/admin/bot/replica exemption checks.

**Acceptance criteria:**

- The public runtime factory remains a small orchestration method.
- Each factory can be tested with fakes without an `EngineImpl` network session.
- Protection rules are not duplicated across moderation and room automation setup.

### 7. Tighten Contracts and Documentation

**Status: completed.** Stable identity fails during registration, contextual descriptors validate
when definitions are built for a caller, configuration scalar parsing is centralized, tool-call
authorization and canonical identity are regression-tested, and the architecture documents reflect
the implemented boundaries.

**Problem:** the package has strong records and descriptors, but contract invariants are divided
between constructors, validator code, and runtime execution paths.

**Design:** keep stable identity and uniqueness at registration, then validate caller-specific
descriptors at materialization and provider-payload boundaries.

**Steps:**

1. Add invariant tests for every descriptor field and tool-response envelope shape.
2. Document thread ownership for mutable request collaborators and idempotence/ordering semantics
   for each tool.
3. Update `AGENTIC_ARCHITECTURE.md`, `TOOL_ROUTING_ARCHITECTURE.md`, and `REFACTORING.md` after
   each completed stage; remove claims that do not match implementation.
4. Keep prompt resources versioned and list each policy that selects a prompt.
5. Keep coverage claims behavioral unless a JaCoCo-style percentage gate is explicitly configured;
   do not report an unmeasured percentage as complete coverage.

JaCoCo now produces a report for `org.saturn.app.agent` during Maven `verify` under
`target/site/jacoco/`. It is intentionally report-only until the first baseline is reviewed; no
percentage threshold is claimed or enforced yet.
The first measured baseline is 89.65% line, 73.92% branch, 89.75% instruction, 94.59% method,
and 70.81% complexity coverage across 140 instrumented agent classes.
After repository-query edge-case coverage, the measured baseline is 90.18% line, 74.49% branch,
90.23% instruction, 94.74% method, and 71.25% complexity coverage.
After provider transport and payload edge-case coverage, the measured baseline is 90.73% line,
74.89% branch, 90.79% instruction, 95.05% method, and 71.83% complexity coverage.
After the completed policy-chain extraction and all subsequent behavioral coverage, a clean full
verification measures 98.35% line, 87.07% branch, 98.12% instruction, 97.67% method, and 84.97%
complexity coverage across `org.saturn.app.agent` and its subpackages. This remains report-only.
After prompt-catalog resource-boundary hardening, the clean full-suite report measures 98.48% line,
87.12% branch, 98.23% instruction, 97.82% method, and 85.11% complexity coverage. The catalog
itself has no missed lines or branches.

`AgentPromptCatalog` now isolates classpath access behind a package-private resource source while
retaining its public constructor. Deterministic tests cover text and JSON I/O failure translation,
null JSON rejection during construction, missing resources, and malformed non-object tool entries.
The remaining low-coverage paths in `H2AgentSqlRepository` are intentional exclusions from
percentage-driven test work unless a production defect makes them observable. They are
JDBC-driver-dependent result states and defensive Base64-boundary loops that valid configured
limits do not reach. Production behavior is preserved; these paths are documented rather than
covered with artificial fixtures.

`SaturnCommandToolCatalog` has the largest remaining line gap after the completed policy-chain
work, but its misses are also defensive reflection failures: duplicate generated tool names,
annotated classes that do not implement `UserCommand`, empty or missing alias metadata, and class
loading exception translation. The live-catalog test already proves complete annotated-command
discovery, unique names, nonempty aliases, closed schemas, and argument rendering. These failure
paths require synthetic classpath manipulation and are intentionally excluded unless a real command
contract makes one observable.

**Acceptance criteria:**

- Stable malformed identity fails during registration. Invalid contextual contracts are omitted or
  returned as `INVALID_TOOL_CONTRACT` when materialized for a caller.
- Architectural documentation accurately reflects the final policy chain and execution pipeline.

## Recommended Order

1. Complete response-recovery extraction.
2. Extract command-channel policy, then fresh-data policy, into the router policy chain.
3. Split tool validation, ledger, and invocation.
4. Add centralized tool execution classification.
5. Decompose room automation.
6. Split runtime composition.
7. Finish contract hardening and documentation alignment.

Stages 1 through 4 reduce risk inside the LLM execution path before changing automation wiring.
Stages 5 and 6 should only begin after the router and tool-suite tests are green.

## Verification Strategy

For every stage:

1. Add a focused failing test that captures current expected behaviour.
2. Implement the smallest extraction or policy movement.
3. Run affected agent tests with `./mvnw -Dtest=<TestClass> test`.
4. Run `./mvnw spotless:check` and `./mvnw test` before considering the stage complete.
5. Update this plan and `REFACTORING.md` with the implemented boundary and any deliberately
   deferred decision.

No stage should mix functional behaviour changes with structural refactoring unless a regression
test demonstrates that the existing behaviour is incorrect.
