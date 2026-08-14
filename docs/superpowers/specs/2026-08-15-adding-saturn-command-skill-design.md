# Adding Saturn Command Skill Design

## Goal

Create repository-local guidance that lets an agent add a Saturn bot command without missing architectural integration, persistence, payload formatting, documentation, or tests.

## Deliverables

- `.skills/adding-saturn-command/SKILL.md`: concise, decision-driven implementation workflow.
- `.skills/adding-saturn-command/references/command-architecture.md`: Saturn-specific file map, integration details, and examples.
- `.skills/adding-saturn-command/agents/openai.yaml`: discoverable skill metadata.
- `AGENTS.md`: repository-wide engineering guidance and a pointer to the command skill.

## Skill Behavior

The skill triggers when adding, changing, reviewing, or debugging a Saturn chat command. It starts by classifying the command so that agents apply only relevant integration steps.

Every command requires:

1. A role-appropriate class under `command/impl`, a unique `@CommandAliases` declaration, the expected reflective constructor, and explicit authorization behavior.
2. Argument validation and output through existing command helpers and `OutService`.
3. A concise entry in `HelpUserCommandImpl` when user-facing.
4. Focused tests written before implementation, including success, missing/invalid input, authorization, whisper/public output, and meaningful side effects.
5. Formatting and full-suite verification.

Conditional paths cover:

- Service interfaces and implementations when logic is reusable or owns I/O/domain behavior.
- `Base` or `EngineImpl` wiring when a new dependency must be available to commands.
- Message-handler chains or payload listeners only when behavior is driven by incoming protocol events rather than explicit command execution.
- DTOs, prepared SQL, `schema.sql`, and an idempotent migration when durable state changes.
- `config.example.toml` and README updates when runtime configuration changes.
- JSON construction through Gson or `JsonPayloads`, preserving real chat newlines and the required `\u2009` help/weather spacing.

## Repository Guidance

`AGENTS.md` will document the project architecture, Java compatibility, build commands, TDD expectations, persistence rules, payload safety, formatting invariants, configuration hygiene, and Git safety. It will avoid duplicating the detailed command recipe and instead direct command work to the skill.

## Validation

- Validate skill metadata with the skill-creator validator.
- Check all documented paths, symbols, and commands against the repository.
- Search for placeholders and contradictions.
- Run `./mvnw spotless:check` and `./mvnw test`.
- Confirm the skill stays concise and the reference holds detailed project knowledge.

## Scope

This work adds guidance only. It does not refactor command architecture, alter runtime behavior, modify schema, or add a bot command.
