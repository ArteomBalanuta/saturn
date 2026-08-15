# Agent Moderator Capabilities Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let Saturn moderators and administrators issue supported moderation commands through the agent while preserving creator-only permanent bans.

**Architecture:** Extend capability assignment at `AgentInvocationFactory`, leaving `RunCommandTool` as the command allowlist and execution boundary. Update the externalized runtime policy so capability answers reflect the context-sensitive command schema.

**Tech Stack:** Java 23, JUnit 5, Maven, externalized text resources

## Global Constraints

- `captcha`, `mute`, `kick`, and `shadowban` are available to trusted moderators and administrators.
- `ban` remains available only to the configured creator in direct mode.
- Capability questions describe available actions but do not execute them.

---

### Task 1: Role-Based Moderation Capabilities

**Files:**
- Modify: `src/test/java/org/saturn/app/agent/AgentInvocationFactoryTest.java`
- Modify: `src/main/java/org/saturn/app/agent/AgentInvocationFactory.java`

**Interfaces:**
- Consumes: `AuthorizationService.resolveRole(String)` and `Role`
- Produces: `AgentInvocation.context().capabilities()` with role-appropriate moderation access

- [ ] Add failing tests for `MODERATOR`, `ADMIN`, and `REGULAR` callers.
- [ ] Run `mvn -Dtest=AgentInvocationFactoryTest test` and verify moderator/admin assertions fail.
- [ ] Add a focused role predicate and grant `MODERATION_COMMANDS` for non-ambient moderator/admin invocations.
- [ ] Run the focused test and verify it passes.

### Task 2: Capability Answer Contract

**Files:**
- Modify: `src/main/resources/agent/system-policy.txt`
- Test: `src/test/java/org/saturn/app/agent/AgentPromptCatalogTest.java`

**Interfaces:**
- Consumes: context-sensitive `run_command` enum
- Produces: policy language requiring accurate capability answers

- [ ] Add a failing resource assertion for accurate moderation capability descriptions.
- [ ] Run the focused prompt test and verify it fails.
- [ ] Add policy text stating that commands exposed by `run_command` can be performed now when explicitly requested.
- [ ] Run focused and full Maven tests.
