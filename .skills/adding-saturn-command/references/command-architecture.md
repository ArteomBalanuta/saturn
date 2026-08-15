# Saturn Command Architecture

Read this reference before adding, changing, reviewing, or debugging a command. Paths and symbols below reflect the current repository.

## Command Discovery and Dispatch

- Command classes live in `src/main/java/org/saturn/app/command/impl/{admin,moderator,user,dbz}/` and use `org.saturn.app.command.annotation.CommandAliases`.
- `src/main/java/org/saturn/app/command/factory/CommandFactory.java` ClassGraph-scans `org.saturn.app.command.impl` for `@CommandAliases`, caches the catalog, and instantiates the first declared constructor with `(EngineImpl, ChatMessage, List<String>)`.
- `CommandFactory.getCommand` matches aliases through `Util.checkAnagrams`. Choose aliases that are unique and not anagrams of an existing alias; test factory/discovery whenever aliases change.
- `src/main/java/org/saturn/app/command/UserCommandBaseImpl.java` strips the configured prefix, parses arguments, invokes the factory, authorizes the resolved command, executes it, and writes to `logRepository.logCommand`.
- Chat commands arrive through `UserMessageListenerImpl` and its chain ending in `DispatchUserCommandHandler`. Whispers arrive through `InfoMessageListenerImpl` and its chain ending in `DispatchWhisperCommandHandler`. An ordinary command needs no new listener.
- `src/main/java/org/saturn/app/facade/impl/EngineImpl.java` owns `payloadListeners` and registers `onlineSet`, `onlineAdd`, `onlineRemove`, `chat`, and `info`. Register a payload listener only for a new inbound protocol event, not for a normal command alias.

## Command Shape and Authorization

- Extend `UserCommandBaseImpl`; representative commands are `NoteUserCommandImpl`, `BanUserCommandImpl`, and `HelpUserCommandImpl`.
- Use the constructor shape `(EngineImpl, ChatMessage, List<String>)`, call `super(message, engine, authorizedTrips)`, and then `super.setAliases(aliases)`.
- Place commands in the role package that matches access: `admin`, `moderator`, `user`, or `dbz`. Override `getAuthorizedRole()` explicitly with the intended `Role`.
- `UserCommandBaseImpl` exposes `hasArguments()`, `firstArgument()`, `author()`, `replyToAuthor()`, `fail()`, `failWithUsage()`, and `successful()`. Validate before side effects and return `Status` deliberately.
- The base dispatcher performs central authorization and execution logging. Keep the command's `@Slf4j` execution logging specific enough to identify user, target, and failed validation where useful.

## Integration Decision Table

Treat these as composable traits rather than mutually exclusive command types. A persistent or external-API command is normally also service-backed; combine the rows that apply.

| Command type | Add only when needed | Do not add |
| --- | --- | --- |
| Pure | Command, focused test, help | Service, listener, schema |
| Service-backed | Interface in `service/`, implementation in `service/impl/`, `Base` construction with only needed dependencies, tests | Schema unless state is persisted |
| Persistent | Service boundary, `SqlUtil`, DTO if needed, `src/main/resources/schema-h2.sql`, idempotent H2 bootstrap upgrade, indexes, cleanup, persistence tests | A new listener for a normal command |
| External-API | Service interface/implementation for HTTP or other I/O, configuration, DTOs, parsing with Gson/`Util.gson`, response/error tests | HTTP logic in commands; database work unless it persists data |
| Protocol-event-driven | Listener/handler and `EngineImpl.registerPayloadListener`, DTO and tests | A command alias unless users invoke it |

## Services, Protocol, and Configuration

- `src/main/java/org/saturn/app/facade/Base.java` constructs shared services. When adding a service, add its interface and implementation, then construct and expose it in `Base` with only the dependencies it actually needs.
- Services that write user-visible text commonly extend `OutService` and receive an outgoing message queue. Raw hack.chat protocol commands use `JsonPayloads` and the raw queue; see `ModServiceImpl` and `src/main/java/org/saturn/app/util/JsonPayloads.java`.
- Use `EngineImpl` when the command needs engine state, replicas, payload-listener registration, active users, or output queues. Put reusable or domain behavior and external I/O, including HTTP, behind a service interface and implementation; commands should orchestrate rather than perform that work directly.
- Put request/response models under `src/main/java/org/saturn/app/model/dto/` or its `payload/` subpackage. Gson is shared as `Util.gson`; follow existing parsing rather than hand-building JSON. `JsonPayloads` escapes protocol-command values.
- Add a config key to `config.example.toml` and use `Base` configuration only when the classification requires a configurable external/API behavior.

## Persistence Checklist

Use this complete checklist for a persistent command:

- [ ] Write a failing direct command or service test before implementation, then a persistence integration test that proves the SQL behavior.
- [ ] Define a service interface in `src/main/java/org/saturn/app/service/` and implementation in `service/impl/`; wire it in `Base` with only the dependencies it actually needs, rather than unconditionally passing a `Connection` and queues.
- [ ] Add prepared-statement SQL constants to `src/main/java/org/saturn/app/util/SqlUtil.java`; bind values rather than concatenating user input.
- [ ] For a persisted counter, increment atomically in one SQL statement (for example, `SET value = value + 1` or H2 `MERGE`); never `SELECT` a value into Java and then write an incremented replacement.
- [ ] Add a DTO under `src/main/java/org/saturn/app/model/dto/` only when data crosses the command/service boundary as a model.
- [ ] Add the current table definition and needed indexes to `src/main/resources/schema-h2.sql` for a fresh database.
- [ ] Add an idempotent upgrade in `H2SchemaBootstrapper` for existing H2 files when the schema change is not covered by `CREATE ... IF NOT EXISTS`.
- [ ] Close `PreparedStatement` and `ResultSet` resources on every path; prefer structured cleanup when modifying code. Add indexes for new query patterns and test the service against H2.
- [ ] Run `make fresh-db`, verify bootstrap idempotence, run relevant service integration tests, and run the full Maven suite.

## Help and Tests

- Help constants are in `src/main/java/org/saturn/app/command/impl/user/HelpUserCommandImpl.java`. Add the alias/argument synopsis and short description to the right role section.
- Preserve the Java `\\n` separators and `\u2009` thin spaces in `HelpUserCommandImpl`; `Util.alignWithWhiteSpace` formats the help sections and prefix examples are runtime-formatted with `engine.getPrefix()`.
- Follow the complete newline lifecycle: `OutService.normalizeForChatPayload` converts Java `\\n` separators to real line-feed characters before adding text to `outgoingMessageQueue`; `EngineImpl.buildChatPayload`/Gson JSON-encodes those real line feeds for the socket. Never leave escaped backslash-n text in the outgoing queue or socket payload, and do not replace the `\u2009` thin spaces.
- Direct command tests live beside commands under `src/test/java/org/saturn/app/command/impl/...`; use `src/test/java/org/saturn/app/support/TestSupport.java` or the local `CommandTestSupport` pattern to create an engine and message.
- Start with the narrowest focused test. Then cover a matrix of success, missing or invalid input, authorization, public output, whisper output, and meaningful side effects; add factory/discovery coverage when aliases change and service integration tests for persistence or external boundaries.
- Validate with the focused test first, then `./mvnw spotless:check` and `./mvnw test`. Use `./mvnw package` when the change needs an assembled artifact.
