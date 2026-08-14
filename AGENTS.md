# Saturn Agent Guidance

## Project Map

- `src/main/java/org/saturn/app/command/` contains command contracts, discovery, and role-specific implementations; keep command orchestration there.
- `src/main/java/org/saturn/app/service/` and `service/impl/` hold business and persistence boundaries; `facade/Base.java` wires shared services and `facade/impl/EngineImpl.java` owns runtime state and protocol queues.
- `src/main/java/org/saturn/app/listener/` handles inbound protocol events, `model/dto/` contains data models, and `util/` contains shared utilities and SQL constants.
- Tests mirror production code under `src/test/java/`. Fresh schema lives in `schema.sql`, upgrades in `database/migrations/`, and `deploy/create_db.sh` maintains a separate deployment schema.

## Java And Commands

- Keep source compatible with Java 23, as configured in `pom.xml`, and follow the existing Google Java Format style.
- For any command addition or change, read and follow [`.skills/adding-saturn-command/SKILL.md`](.skills/adding-saturn-command/SKILL.md) before editing. It owns the command-specific recipe; do not duplicate it here.
- Use TDD: add the smallest focused failing test, run it, implement, then rerun it. Run focused tests with `./mvnw -Dtest=<TestClass> test`, then `./mvnw spotless:check`, `./mvnw test`, and `./mvnw package` when an assembled artifact is relevant.

## Persistence

- Bind all values with prepared statements. Use transactions for multi-statement writes, roll back on failure, and restore connection state; close `PreparedStatement` and `ResultSet` resources on every path, preferably with try-with-resources.
- Keep fresh and upgrade paths in sync: update `schema.sql`, add an idempotent dated migration in `database/migrations/`, and update the hand-maintained duplicate in `deploy/create_db.sh`. Run `make fresh-db` and add persistence coverage.
- Add indexes for new query patterns and preserve appropriate foreign keys. Do not bypass connection setup that enables foreign keys, WAL mode, and the 5000 ms busy timeout.

## Payloads

- Let `EngineImpl.buildChatPayload` and Gson serialize normal chat messages. Use `JsonPayloads` for raw protocol commands so values, including a trailing backslash, remain JSON-escaped; never hand-build unescaped JSON.
- Preserve `\\n` separators in source text where existing help or weather formatting uses them, but ensure `OutService.normalizeForChatPayload` converts them to real line feeds before queuing. Do not leave literal backslash-n text in queued or socket payloads.
- Retain required `\u2009` thin-space formatting in help and weather output.

## Configuration And Repository Hygiene

- Add documented defaults to `config.example.toml`; never commit local credentials or runtime values from ignored `config.toml`. Keep the ignored `database/` contents, including SQLite files, out of commits.
- Update documentation when behavior, configuration, or operations change. Use repository-relative paths in docs and agent guidance.
- Preserve unrelated work in a dirty tree. Do not use destructive Git commands such as `git reset --hard` or `git checkout --`; stage and commit only task-owned files.
