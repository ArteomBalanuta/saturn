# Full Agent Package Refactor — Routing Slice QA

## Scope

Completed the routing migration for all 18 requested declarations into `org.saturn.app.agent.routing`:

- `AgentCommandChannelPolicy`
- `AgentCommandIntentPolicy`
- `AgentCommandProseGuard`
- `AgentInfrastructure`
- `AgentInfrastructureFactory`
- `AgentInvocationFactory`
- `AgentPreparedRequest`
- `AgentPromptCatalog`
- `AgentRequestAssembler`
- `AgentResponseCorrector`
- `AgentResponseFinalizer`
- `AgentResponseSanitizer`
- `AgentRouterFactory`
- `AgentRuntimeFactory`
- `AgentSystemPrompt`
- `AgentTextBounds`
- `DefaultAgentRouter`
- `VerifiedQuoteCatalog`

The three previously partial routing files were normalized in place; no duplicate root declarations remain. Same-package routing tests were moved with the implementation classes. Callers across the facade, service, listener, command, tool, turn, and room areas now use routing imports. Nested types and package-private seams were preserved.

## Verification

All commands were run from `/Users/ab/workspace/projects/saturn` on branch `develop`:

- `./mvnw -q -DskipTests compile` — **PASS**
- `./mvnw -q -DskipTests test-compile` — **PASS**
- `./mvnw -q -Dtest='org.saturn.app.agent.routing.*Test' test` — **PASS**
- `./mvnw -q spotless:apply` — **PASS** (normalized import ordering after package move)
- `./mvnw -q spotless:check` — **PASS**
- `git diff --check` — **PASS**
- `./mvnw -q test` — **PASS**: 600 tests, 0 failures, 0 errors, 5 skipped

## Behavior and boundary checks

- Command-intent gating and command prose guard tests passed.
- Prompt catalog/system prompt and request assembly tests passed.
- Response correction, finalization, sanitization, verified quote matching/fallback tests passed.
- Router, router factory, runtime factory, infrastructure wiring, and integration/service tests passed.
- `CommandChannelPolicy.Result`, `PromptCatalog.ResourceSource`, `ResponseFinalizer.Result`, and `VerifiedQuoteCatalog.Entry` remained available with their existing ownership and behavior.
- No commit or push performed.
