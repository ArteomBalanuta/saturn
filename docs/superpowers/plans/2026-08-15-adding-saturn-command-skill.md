# Adding Saturn Command Skill Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a repository-local skill and agent guidance that define the complete, correct workflow for adding a Saturn bot command.

**Architecture:** Keep the executable workflow concise in `SKILL.md`, place detailed Saturn architecture and file mappings in one reference, and reserve `AGENTS.md` for rules that apply to every repository task. Generate standard skill metadata with the skill-creator tooling and validate documentation against current source paths and symbols.

**Tech Stack:** Markdown, YAML, Java 23, Maven Wrapper, JUnit 5, SQLite, ClassGraph, Gson.

## Global Constraints

- Add guidance only; do not alter runtime behavior or schema.
- Preserve real chat newlines and required `\u2009` formatting in help and weather payloads.
- Require prepared SQL, synchronized `schema.sql` and idempotent migrations, and tests for persistence changes.
- Require tests before production changes and verify with `./mvnw spotless:check` and `./mvnw test`.
- Keep detailed command knowledge in the skill reference instead of duplicating it in `AGENTS.md`.

---

### Task 1: Scaffold and Author the Saturn Command Skill

**Files:**
- Create: `.skills/adding-saturn-command/SKILL.md`
- Create: `.skills/adding-saturn-command/agents/openai.yaml`
- Create: `.skills/adding-saturn-command/references/command-architecture.md`

**Interfaces:**
- Consumes: command annotations and factory discovery, `UserCommandBaseImpl`, `Base`, `EngineImpl`, handler chains, service interfaces/implementations, `SqlUtil`, `schema.sql`, migrations, help constants, and test support.
- Produces: `$adding-saturn-command`, a repository-local workflow for command implementation and review.

- [ ] **Step 1: Record the baseline behavior**

Run a fresh-agent read-only scenario asking for the exact changes needed by a persistent regular-user command. Record missing or incorrect integration steps before creating the skill.

- [ ] **Step 2: Initialize the skill**

Run:

```bash
CODEX_HOME="${CODEX_HOME:-$HOME/.codex}"
python3 "$CODEX_HOME/skills/.system/skill-creator/scripts/init_skill.py" \
  adding-saturn-command \
  --path .skills \
  --resources references \
  --interface display_name="Adding a Saturn Command" \
  --interface short_description="Add Saturn bot commands without missed wiring" \
  --interface default_prompt="Use $adding-saturn-command to add a new Saturn bot command end-to-end."
```

Expected: the skill directory contains `SKILL.md`, `agents/openai.yaml`, and `references/`.

- [ ] **Step 3: Write the core workflow**

Replace the generated `SKILL.md` with frontmatter whose name is `adding-saturn-command` and whose description starts with `Use when`. Define this ordered contract:

1. Classify the command with every applicable trait: pure, service-backed, persistent, external-API, or protocol-event-driven; the traits compose rather than selecting one exclusive type.
2. Write focused failing tests before implementation, then broaden to success, missing or invalid input, authorization, public output, whisper output, and meaningful side effects.
3. Add the role-appropriate command with unique aliases, the reflective constructor `(EngineImpl, ChatMessage, List<String>)`, explicit role authorization, argument validation, output helpers, and command logging.
4. Add only the conditional service, facade, listener, persistence, configuration, and DTO integration identified during classification.
5. Update user-facing help while retaining `\u2009` and newline conventions.
6. Verify focused tests, discovery, formatting, and the full suite.

Link directly to `references/command-architecture.md` and require reading it before editing a command.

- [ ] **Step 4: Write the architecture reference**

Document exact repository paths and symbols for:

- ClassGraph discovery through `@CommandAliases` and `CommandFactory`.
- Role packages, `UserCommandBaseImpl`, authorization trips, arguments, status, replies, and logging.
- `Base` service construction and when `EngineImpl` or its payload listener registry is relevant.
- Chat and whisper dispatch handler chains and the rule that ordinary commands need no new listener.
- Service interface/implementation boundaries and queue/connection dependencies.
- Prepared SQL in `SqlUtil`, DTOs, `schema.sql`, idempotent migrations, indexes, resource cleanup, and persistence tests.
- Help sections, short descriptions, runtime prefix examples, real newline handling, `\u2009`, Gson, and `JsonPayloads`.
- Test locations, `TestSupport`, direct command tests, factory/discovery tests when aliases change, service integration tests, and full validation commands.

Include one compact decision table and one complete persistent-command checklist; do not include generated Java boilerplate that can drift from the source.

- [ ] **Step 5: Validate the skill**

Run:

```bash
CODEX_HOME="${CODEX_HOME:-$HOME/.codex}"
python3 "$CODEX_HOME/skills/.system/skill-creator/scripts/quick_validate.py" \
  .skills/adding-saturn-command
```

Expected: `Skill is valid!`

- [ ] **Step 6: Commit the skill**

```bash
git add .skills/adding-saturn-command
git commit -m "docs: add Saturn command creation skill"
```

### Task 2: Add Repository Agent Guidance

**Files:**
- Create: `AGENTS.md`

**Interfaces:**
- Consumes: repository build/configuration conventions and `$adding-saturn-command`.
- Produces: default instructions for any agent working in Saturn.

- [ ] **Step 1: Write repository-wide guidance**

Create a concise `AGENTS.md` with these sections:

- Project map and architectural boundaries.
- Java 23 source compatibility and existing style conventions.
- Required use of `.skills/adding-saturn-command/SKILL.md` for command work.
- TDD, focused-test, `spotless:check`, full-test, and package commands.
- Persistence rules for prepared statements, transactions, resource closure, schema/migration parity, indexes, foreign keys, WAL, and busy timeout.
- Payload rules for Gson/`JsonPayloads`, real chat newlines, trailing backslashes, and required `\u2009` formatting.
- Configuration and secret hygiene for `config.example.toml`, ignored `config.toml`, and ignored `database/`.
- Documentation and Git rules, including preserving unrelated work and avoiding destructive commands.

- [ ] **Step 2: Check for duplication and ambiguity**

Confirm that `AGENTS.md` links to the skill instead of repeating the command recipe, uses repository-relative paths, and contains no instructions that conflict with `README.md`, `pom.xml`, or the design specification.

- [ ] **Step 3: Commit repository guidance**

```bash
git add AGENTS.md
git commit -m "docs: add Saturn agent guidance"
```

### Task 3: Forward-Test and Verify Documentation

**Files:**
- Modify if gaps are found: `.skills/adding-saturn-command/SKILL.md`
- Modify if gaps are found: `.skills/adding-saturn-command/references/command-architecture.md`
- Modify if gaps are found: `AGENTS.md`

**Interfaces:**
- Consumes: all documentation created by Tasks 1 and 2.
- Produces: validated guidance that another agent can apply to a realistic Saturn command.

- [ ] **Step 1: Run the original scenario with the skill**

Ask a fresh read-only agent to use `$adding-saturn-command` for the same persistent command scenario. Verify that it covers discovery, role authorization, parsing, services, `Base` wiring, no unnecessary listener, schema and migration parity, prepared SQL, help formatting, payload safety, and tests.

- [ ] **Step 2: Close observed documentation gaps**

Use `apply_patch` to correct only omissions or ambiguities demonstrated by the forward test. Re-run `quick_validate.py` after any skill edit.

- [ ] **Step 3: Verify referenced paths and symbols**

Run:

```bash
test -f AGENTS.md
test -f .skills/adding-saturn-command/SKILL.md
test -f .skills/adding-saturn-command/agents/openai.yaml
test -f .skills/adding-saturn-command/references/command-architecture.md
rg -n "CommandFactory|UserCommandBaseImpl|HelpUserCommandImpl|schema.sql|JsonPayloads|u2009" \
  .skills/adding-saturn-command AGENTS.md
rg -n "T[O]DO|T[B]D|F[I]XME|P[L]ACEHOLDER" \
  .skills/adding-saturn-command AGENTS.md docs/superpowers/plans/2026-08-15-adding-saturn-command-skill.md
```

Expected: all files exist, required architecture terms are present, and the placeholder search returns no matches.

- [ ] **Step 4: Run repository verification**

Run:

```bash
./mvnw spotless:check
./mvnw test
```

Expected: formatting check and all tests pass.

- [ ] **Step 5: Commit validation refinements**

```bash
git add .skills/adding-saturn-command AGENTS.md docs/superpowers/plans/2026-08-15-adding-saturn-command-skill.md
git commit -m "docs: validate Saturn command guidance"
```
