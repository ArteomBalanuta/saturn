# Agent Moderator Capabilities Design

## Goal

Allow trusted Saturn moderators and administrators to execute the safe moderation subset through agent prompts, while retaining creator-only permanent bans.

## Design

`AgentInvocationFactory` remains the authority that maps trusted message metadata to agent capabilities. A non-ambient invocation from a caller whose resolved Saturn role is `MODERATOR` or `ADMIN` receives `MODERATION_COMMANDS`; the configured creator continues to receive that capability in every invocation. `PERMANENT_BAN` remains available only to the creator for direct `*l` invocations.

`RunCommandTool` remains unchanged as the enforcement boundary. Its context-sensitive command enum exposes `captcha`, `mute`, `kick`, and `shadowban` when `MODERATION_COMMANDS` is present, and exposes `ban` only when `PERMANENT_BAN` is present.

The runtime policy will tell the model to describe the actions currently exposed by `run_command` as executable capabilities. Capability questions must not trigger an action, but the answer must not incorrectly deny available moderation authority.

## Verification

Focused tests cover moderator, administrator, regular-user, creator-direct, and creator-mention capability assignment. A resource test verifies that the policy directs the model to state exposed moderation abilities accurately.
