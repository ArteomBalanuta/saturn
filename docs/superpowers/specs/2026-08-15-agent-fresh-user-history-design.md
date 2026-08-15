# Agent Fresh User-History Design

## Problem

Saturn can answer a repeated named-user request from shared agent memory without calling
`user_message_history` again. The latest Jill request loaded six conversation-memory entries,
made no tool call, and reused an old summary even though 325 public Jill messages were available.
The configured 500-row history limit therefore never participated in that response.

## Goals

- Keep the LLM responsible for resolving the target user and synthesizing the response.
- Require fresh persisted data for every request that asks about a named user's activity or
  message history.
- Never treat an earlier assistant answer or earlier tool output as satisfying the current
  invocation's freshness requirement.
- Make the evidence visible by reporting the number of returned messages and their time range.
- Fail honestly when the fresh lookup cannot complete instead of falling back to stale prose.

## Non-Goals

- Generate deterministic user profiles in Java.
- Force every conceptual or conversational request through a database tool.
- Make the response long merely because many rows were returned.
- Change SQL access, message visibility, whisper filtering, or the 500-row maximum.

## Design

Introduce an `AgentFreshnessPolicy` with one responsibility: determine whether the newest request
requires a fresh `user_message_history` result. It recognizes explicit named-user/history intents,
including requests such as "tell me about Jill", "who is Jill", and "what has Jill said". The
policy selects the required capability, not the nick or final answer; the LLM still resolves tool
arguments from the newest prompt and shared context.

`DefaultAgentRouter` will track successful tool names for the current invocation. Before accepting
a tool-free final response, it checks the freshness policy. If `user_message_history` is required
and has not succeeded during this invocation, the router adds a focused correction and requests
that tool. The correction exposes only the required tool contract. If the model still does not
produce that tool call, or the tool fails, routing fails rather than returning the ungrounded
answer. A tool result from an older memory turn never enters the successful-tool set.

After a successful call, the normal LLM loop receives the complete bounded result and writes the
analysis. Java does not create or template the profile. The existing repository default remains
500 rows for `recent_messages_for_user`.

`user_message_history` will add result metadata alongside `rows`: `returnedCount`,
`newestCreatedOn`, and `oldestCreatedOn`. The system policy will require a named-user analysis to
state the returned count and time range, then summarize representative themes from the complete
result. This provides observable grounding without requiring an artificially verbose response.

## Failure Handling

- A failed history tool call cannot satisfy freshness.
- A second prose-only answer after the focused correction is rejected.
- Empty history is valid fresh data and produces a concise "no public messages found" answer.
- Normal tool-call and output limits continue to apply.
- SQLite short-read retries remain limited to the existing one-retry persistence behavior.

## Testing

- Reproduce a repeated prompt with an old assistant summary in memory and an initial prose-only
  LLM response; verify the router requests and executes `user_message_history` before replying.
- Verify an old memory answer or old tool-related prose does not satisfy the current invocation.
- Verify a failed required tool does not return the stale answer.
- Verify a successful current history call permits LLM synthesis.
- Verify history results expose row count and oldest/newest timestamps for 0, 1, and many rows.
- Preserve existing tests for the 500-row default and maximum.

## Acceptance Criteria

For two consecutive `*l tell me about jill user` requests, each request logs a successful
`user_message_history` call. With the current database, the answer identifies that 325 public
messages were analyzed and gives their time range. No request can return the previous summary
solely because it exists in shared memory.
