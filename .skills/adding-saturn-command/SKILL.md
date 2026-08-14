---
name: adding-saturn-command
description: Use when adding or changing a Saturn bot command, including its authorization, help, services, persistence, protocol handling, and tests.
---

# Adding a Saturn Command

Read [the command architecture reference](references/command-architecture.md) before editing a command. It maps the current source of truth, including the deployment schema duplicate that persistent changes must not overlook.

## Workflow

1. **Classify the command.** Choose one primary type: pure, service-backed, persistent, external-API, or protocol-event-driven. Identify only the conditional integration points required by that type.
2. **Write focused failing tests before implementation.** Add the smallest direct command, service, discovery, or persistence test that proves the behavior. Run it and confirm that it fails for the intended missing behavior; then implement, rerun it, and broaden validation.
3. **Add the command.** Put it in the role-appropriate `command.impl` package, give `@CommandAliases` unique aliases, and expose the reflective constructor `(EngineImpl, ChatMessage, List<String>)`. Extend `UserCommandBaseImpl`, set aliases, choose explicit role authorization, validate arguments, use reply/failure helpers, and retain command logging through the base dispatcher plus useful execution logs.
4. **Add only needed integrations.** Based on the classification, add the service interface and implementation, `Base` construction, DTOs, configuration, persistence, external API calls, or a payload listener. Ordinary chat and whisper commands are already dispatched; do not add a listener unless handling a new protocol event.
5. **Update help.** Add a short description to the correct `HelpUserCommandImpl` section. Keep its Java `\\n` separators and `\u2009` thin spaces, including runtime prefix examples. `OutService.normalizeForChatPayload` must turn each `\\n` separator into a real line-feed before queueing, then `EngineImpl.buildChatPayload`/Gson JSON-encodes that line feed for the socket. Never leave escaped backslash-n text in the outgoing queue or socket payload.
6. **Verify.** Run focused tests, discovery/factory coverage when aliases change, formatting, and the full suite. For persistence, validate both fresh-database paths and migration behavior.

## Classification Guide

- **Pure:** command state and existing `EngineImpl` helpers are sufficient; add no new service or listener.
- **Service-backed:** keep command orchestration thin and add behavior behind a service interface and implementation.
- **Persistent:** use prepared SQL, schema and migration work, indexes as needed, cleanup, and persistence tests. Also inspect the hand-maintained schema in `deploy/create_db.sh`.
- **External-API:** add configuration and DTO parsing only when required; use repository JSON conventions and test failure paths.
- **Protocol-event-driven:** register a payload listener only for a new inbound protocol `cmd`; normal commands continue through existing chat/whisper handler chains.

## Guardrails

- Aliases are discovered reflectively and matched with an anagram check. Check for exact and anagram collisions across all command aliases.
- `UserCommandBaseImpl` parses space-separated arguments and treats literal `\\n` specially. Validate inputs before side effects and return an explicit `Status`.
- The shared base dispatcher checks authorization and writes `executed_commands`; individual commands should still log enough context to diagnose an outcome.
- Treat `schema.sql` as the fresh-database source, migrations as upgrades for existing databases, and `deploy/create_db.sh` as a separate duplicated deployment schema that must remain consistent.
- Do not add generated Java boilerplate to this skill. Inspect the nearest existing command and service for current patterns before coding.

## Validation

Run the narrowest relevant tests first, then:

```bash
./mvnw spotless:check
./mvnw test
python3 /Users/ab/.codex/skills/.system/skill-creator/scripts/quick_validate.py \
  .skills/adding-saturn-command
```

For persistent changes, run `make fresh-db` and exercise the migration sequence against an existing database. If deployment uses `deploy/create_db.sh`, validate its generated database too.
