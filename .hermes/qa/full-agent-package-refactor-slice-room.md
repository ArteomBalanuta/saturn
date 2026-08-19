# Full agent package refactor — room slice QA

Date: 2026-08-19
Branch: `develop`
Scope: seven room declarations and their same-package tests; no commit or push.

## Migration completed

Moved exactly these declarations from `org.saturn.app.agent` to `org.saturn.app.agent.room` using filesystem moves:

- `AgentMentionParser`
- `AgentQuietRegistry` (preserved nested `QuietKey`)
- `AgentRoomMessagePipeline` (preserved nested `Handler`, `Decision`, `Turn`)
- `AgentRoomAutomationFactory`
- `DefaultAgentRoomAutomation`
- `AgentSessionLockManager` (preserved nested `LockedOperation`)
- `ProtectedPrincipalPolicy`

Moved same-package tests with the implementation:

- `AgentMentionParserTest`
- `AgentQuietRegistryTest`
- `AgentSessionLockManagerTest`
- `DefaultAgentRoomAutomationTest`

Updated integration callers/imports in routing/runtime composition and retained explicit imports (no wildcard imports in the room slice or touched runtime test). Room behavior was not changed: mention matching, quiet identity/expiry, room pipeline admission/outcomes, protected principals, automation moderation, and fair striped session locking remain covered.

`AgentSessionLockManager` and `AgentRoomAutomationFactory` received only the visibility required for their cross-package routing/runtime callers: the lock manager API and factory/create method are public; implementation behavior is unchanged.

## Exact verification

All commands ran from `/Users/ab/workspace/projects/saturn`:

- `./mvnw -DskipTests compile` — **PASS**, 301 production source files.
- Focused room/integration tests:
  `./mvnw -Dtest='org.saturn.app.agent.room.AgentMentionParserTest,org.saturn.app.agent.room.AgentQuietRegistryTest,org.saturn.app.agent.room.AgentSessionLockManagerTest,org.saturn.app.agent.room.DefaultAgentRoomAutomationTest,org.saturn.app.agent.AgentRuntimeFactoryTest,org.saturn.app.agent.DefaultAgentRouterTest,org.saturn.app.listener.message.handler.AgentParticipationHandlerTest,org.saturn.app.service.impl.AgentServiceImplTest' test`
  — **PASS**, 101 tests, 0 failures, 0 errors, 5 skipped.
- `./mvnw spotless:check` — **PASS**.
- `git diff --check` — **PASS**.
- `./mvnw test` — **PASS**, 600 tests, 0 failures, 0 errors, 5 skipped.
- `./mvnw clean compile` — **PASS**, 301 production source files.

Structural checks also passed: all seven moved source declarations and four moved tests declare `org.saturn.app.agent.room`; all seven old source paths are absent; no stale old room FQN references were found.

## Task-owned touched paths

### New room production paths

- `src/main/java/org/saturn/app/agent/room/AgentMentionParser.java`
- `src/main/java/org/saturn/app/agent/room/AgentQuietRegistry.java`
- `src/main/java/org/saturn/app/agent/room/AgentRoomMessagePipeline.java`
- `src/main/java/org/saturn/app/agent/room/AgentRoomAutomationFactory.java`
- `src/main/java/org/saturn/app/agent/room/DefaultAgentRoomAutomation.java`
- `src/main/java/org/saturn/app/agent/room/AgentSessionLockManager.java`
- `src/main/java/org/saturn/app/agent/room/ProtectedPrincipalPolicy.java`

### New room test paths

- `src/test/java/org/saturn/app/agent/room/AgentMentionParserTest.java`
- `src/test/java/org/saturn/app/agent/room/AgentQuietRegistryTest.java`
- `src/test/java/org/saturn/app/agent/room/AgentSessionLockManagerTest.java`
- `src/test/java/org/saturn/app/agent/room/DefaultAgentRoomAutomationTest.java`

### Existing callers/tests updated for the room move

- `src/main/java/org/saturn/app/agent/AgentRuntimeFactory.java`
- `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java`
- `src/test/java/org/saturn/app/agent/AgentRuntimeFactoryTest.java`

The worktree contains substantial pre-existing migration and unrelated dirty/untracked artifacts; they were preserved and not reset, checked out, cleaned, committed, or pushed.
