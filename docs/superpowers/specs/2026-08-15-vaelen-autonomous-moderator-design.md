# Vaelen Autonomous Moderator Design

## Objective

Turn Saturn's `*l` agent into Vaelen: a Stoic technical mentor that can answer direct requests,
respond to exact bot mentions, join useful room discussions on its own, ground claims in Saturn's
SQLite message history, and react to clear spam or raid signals with Saturn's existing moderation
commands.

This feature remains focused on agent participation and moderation. Unrelated bug fixes and a new
audit, privacy, or security subsystem are explicitly out of scope. Existing data visibility and SQL
constraints remain in place.

## Approved Behavior

- Vaelen uses the supplied persona and recognizes trusted caller trip `595754` as the creator.
- Direct `*l` commands and exact `@<bot-nick>` mentions always request a response.
- Other public messages may be evaluated for an unsolicited response. Vaelen decides whether it
  has something useful to add and may remain silent.
- A polite request to be quiet suppresses unsolicited replies to that user for 15 minutes in that
  room. It does not silence other conversations or moderation monitoring. A direct `*l` command or
  mention still receives a reply without clearing the quiet period.
- Vaelen may autonomously enable captcha, warn, mute, kick, and shadow-ban based on detector
  evidence. Permanent room bans require a direct request from trusted trip `595754`.
- The implementation is reviewed for Critical feature defects only at completion.

## Architecture

### Prompt Composition

Store the Vaelen persona as a UTF-8 classpath resource rather than a Java string literal. A small
`AgentSystemPrompt` component composes four sections:

1. Saturn's runtime/tool contract.
2. The Vaelen persona.
3. Trigger-specific participation instructions.
4. Trusted room and caller metadata serialized as JSON.

The runtime contract tells Vaelen to use live tools for current users, room state, named-user
history, and database facts. For trusted creator requests, it requires `database_schema` before
generated `database_sql`. It also distinguishes trusted metadata from untrusted chat text so a
user cannot become the creator merely by claiming the tripcode in a message.

### Invocation Modes

Extend `AgentInvocation` with an invocation mode:

- `DIRECT`: submitted by `*l`; always returns a chat reply.
- `MENTION`: submitted when a public message contains an exact, case-insensitive mention of the
  current engine nick; always returns a chat reply.
- `AMBIENT`: submitted for eligible ordinary public chat; the model may return a silent decision.

Existing callers default to `DIRECT` for compatibility. All modes use the existing per-room FIFO
agent worker and shared public room memory. Self-authored messages, command-prefixed messages, and
empty mention bodies are not ambient submissions, preventing loops and duplicate command handling.

An `AmbientConversationCoordinator` keeps at most one ambient evaluation pending per room. While it
is running, newer eligible messages replace the pending trigger because the eventual evaluation
hydrates the latest room history from SQLite. Direct commands and mentions retain FIFO order among
themselves, use reserved admission capacity, and are never rejected merely because ambient chat is
busy.

### Chat Participation

Add a message-chain handler immediately before command dispatch. It performs these steps:

1. Ignore self messages and messages already recognized as commands.
2. Detect and strip an exact mention of the current host or replica nick.
3. Detect polite quiet requests and record a room-plus-user suppression expiry.
4. Submit mentions as `MENTION`.
5. Submit other eligible messages as `AMBIENT` unless the author is currently suppressed.

The quiet registry is an in-memory, thread-safe component keyed by room and stable user identity
(trip, then hash, then nick). Entries expire after 15 minutes and are lazily removed. Restarting
Saturn clears this short-lived state.

Ambient prompts instruct the provider to return a dedicated no-reply marker when intervention
would add no value. The router converts that marker into a silent `AgentResult`, does not persist it
as an agent turn, and `AgentService` sends nothing. Direct and mention modes reject the marker and
still require a normal response.

### Database Grounding

Introduce an `AgentConversationContextProvider` port with a SQLite implementation. Before a
`MENTION` or `AMBIENT` request reaches the provider, it loads a bounded window of recent `PUBLIC`
rows from the current room's `messages` table and adds them as structured context. Failure to load
this optional context is logged and does not prevent a response.

Purpose-built tools remain available for active users, named-user history across rooms, and bounded
room history. The prompt explicitly requires those tools before factual claims about current users
or historical messages. Trusted creator trip `595754` retains schema inspection and bounded
read-only SQL for questions that no purpose-built tool can answer.

### Command Capabilities

Replace `RunCommandTool`'s single global allowlist with capability-based command catalogs:

- Every invocation retains approved non-destructive informational commands.
- Trusted creator trip `595754` receives moderation commands including captcha, mute, kick,
  shadow-ban, and permanent ban.
- Autonomous detector actions receive captcha, warning, mute, kick, and shadow-ban only. Permanent
  ban is absent from that catalog.

Commands continue through `EngineSaturnCommandGateway`, preserving Saturn's command parsing and
service behavior. The gateway receives a trusted synthetic creator context only for detector
actions, not for arbitrary user prompts.

## Moderation Detection

Create one `RoomModerationMonitor` per engine. It consumes public message and join events and keeps
small time-window deques keyed by stable identity. Thresholds are configurable under `[agent]` and
default to:

| Signal | Default | Action |
|---|---:|---|
| Message burst | 6 messages in 5 seconds | Public warning |
| Repeated normalized text | 4 copies in 10 seconds | Mute |
| Second spam breach | Within 30 seconds | Kick |
| Reoffence after bot kick | Within 10 minutes | Shadow-ban and kick |
| Join burst | 8 joins in 10 seconds | Enable captcha |
| Same-hash nick variants | 5 joins in 20 seconds | Enable captcha; shadow-ban repeat offender |

Suspicious-name similarity is supporting raid evidence, not by itself a reason to shadow-ban.
Host, replica, and configured admin identities are excluded from automatic targets. Duplicate
actions are suppressed by per-room and per-target cooldowns. Captcha remains enabled until an
authorized command disables it.

The monitor returns semantic decisions; a `ModerationActionExecutor` maps each decision onto the
existing Saturn command gateway. This keeps event detection deterministic and command execution in
one boundary.

## Configuration

Split the new settings into `AgentParticipationConfig` and `AgentModerationConfig` so the existing
provider configuration does not become a single unrelated parameter bundle. Add documented
defaults for:

- `agent.creatorTrip`
- `agent.ambientEnabled`
- `agent.quietMinutes`
- `agent.contextMessageLimit`
- message burst count/window
- repeated-message count/window
- repeat-offence windows
- join burst count/window
- same-hash join count/window
- moderation action cooldown

Invalid or non-positive thresholds fail configuration at startup. Ambient participation and
automatic moderation can be disabled independently without disabling direct `*l` requests.

## Failure Handling

- Context hydration failure falls back to existing room memory and tools.
- Provider failure on an ambient turn produces no room message; direct and mention failures retain
  the existing concise failure response.
- Moderation decisions are idempotent within their cooldown window.
- A failed command execution is logged and does not escalate automatically to a more destructive
  action.
- Queue saturation drops ambient evaluations before direct commands, mentions, or moderation
  decisions.

## Test Strategy

Use TDD for each slice:

- Prompt composition includes Vaelen identity, creator metadata, tool requirements, and invocation
  mode instructions.
- Exact mention parsing is case-insensitive, strips the mention, ignores self messages, and gives
  commands precedence.
- Ambient no-reply results are neither queued nor persisted.
- Quiet requests suppress only that user's ambient turns for 15 minutes and expiry restores them.
- SQLite context hydration returns bounded current-room `PUBLIC` messages and excludes whispers and
  unclassified legacy rows.
- Tool definitions expose informational commands to all callers, moderation commands to the
  creator, and permanent ban to no autonomous context.
- Message and join windows trigger each escalation once, respect cooldowns, and exclude protected
  users.
- Handler-chain tests prove public messages reach the correct direct/mention/ambient path without
  loops.
- Existing agent, command, payload-formatting, and persistence suites remain green.

## Explicitly Deferred

Persistent quiet state, semantic embeddings, cross-room autonomous conversation, a moderation
dashboard, a new audit schema, private-message analysis, automatic captcha disablement, and
unrelated repository cleanup are outside this feature.
