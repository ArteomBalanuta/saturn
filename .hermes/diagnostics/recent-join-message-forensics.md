# Recent join-message forensics

## Scope

Investigated the new-user join notification only. No production or test source was modified. The worktree was already dirty with unrelated agent/resource/config/diagnostic artifacts; those were preserved.

## Exact message construction

The message is constructed in:

- `src/main/java/org/saturn/app/service/impl/UserServiceImpl.java:44-80`
  - `isSeenRecently(User user)` begins at line 45.
  - It executes `SqlUtil.SELECT_SEEN_RECENTLY_AS` at lines 48-51 and collects the returned `name` values at lines 53-55.
  - It suppresses notification when there are no names, or when the only name is the joining user's own nick, at lines 65-67.
  - It filters the joining nick case-insensitively, converts the remaining names to the existing comma-separated list representation, and removes the list brackets at lines 69-75.
  - The exact string literal is at lines 76-78:

    ```java
    "\\n @%s, has been seen as: _%s_ in the last 15 minutes. \\n"
        .formatted(user.getNick(), aliases)
    ```

  The requested minimal production change is therefore line 77 only: replace `in the last 15 minutes` with `recently`, preserving the existing `\\n` separators, spaces, punctuation, nick/alias formatting, and trailing `\\n`.

Expected post-change service value for nick `new-user` and aliases `Alice,Bob`:

```text
\\n @new-user, has been seen as: _Alice,Bob_ recently. \\n
```

(The source representation must retain the existing escaped backslash-n convention; `OutService` later normalizes it for the chat payload.)

## Listener and caller trace

1. Protocol registration:
   - `src/main/java/org/saturn/app/facade/impl/EngineImpl.java:66-73` creates `userJoinedListener` as `new UserJoinedListenerImpl(this)` at line 68.
   - `EngineImpl.java:95-101` registers it for the `onlineAdd` payload at line 97.
2. Listener entry point:
   - `src/main/java/org/saturn/app/listener/impl/UserJoinedListenerImpl.java:24-79`.
   - `notify(String jsonText)` is lines 36-79; JSON is parsed/deserialized at lines 38-40.
   - The listener records the new user via `engine.addActiveUser(user)` at line 48, runs room automation at line 49, shares subscriber info at line 50, and applies shadow-ban handling at line 51.
   - The recent-seen branch is lines 53-63. It excludes the bot itself, host, and replica at lines 54-58, then calls `engine.userService.isSeenRecently(user)` at lines 59-61 and enqueues the optional returned message at line 62.
3. Join audit persistence that supplies the recent query:
   - `src/main/java/org/saturn/app/facade/impl/EngineImpl.java:342-353`: `addActiveUser` logs a `JOINED` message at lines 345-352.
   - The corresponding leave audit is `EngineImpl.java:325-340`, especially lines 330-337, logging `LEFT`.
4. Query definition:
   - `src/main/java/org/saturn/app/util/SqlUtil.java:97-101`, `SELECT_SEEN_RECENTLY_AS`.
   - It selects distinct names matching hash or nonblank/non-`null` trip, restricts message type to `LEFT`/`JOINED`, and uses a 900000 ms (15-minute) cutoff with `limit 5`.
5. Interface boundary:
   - `src/main/java/org/saturn/app/service/UserService.java:8-12` declares `Optional<String> isSeenRecently(User user)` at line 10.
6. Payload normalization boundary:
   - `src/main/java/org/saturn/app/service/impl/OutService.java:22-40` queues outbound messages.
   - Addressed messages call normalization at lines 27 and 89-94; `normalizeForChatPayload` at lines 81-87 converts literal `\\n` to real line feeds at line 86. No change is needed there.

## Existing callers/tests and smallest change set

- Production caller: `UserJoinedListenerImpl.java:59-62` is the only call site found for `isSeenRecently`.
- No existing test directly covers `UserServiceImpl.isSeenRecently` or the `has been seen` message (`git grep` found no such test).
- Existing service tests are under `src/test/java/org/saturn/app/service/impl/`, including `H2CommandPersistenceCompatibilityTest.java`, but none currently exercises this method.
- Existing persistence setup example:
  - `src/test/java/org/saturn/app/service/impl/H2CommandPersistenceCompatibilityTest.java:19-46` boots a temporary H2 database, constructs `UserServiceImpl` at line 27, and uses a real repository/query path.
  - `src/test/java/org/saturn/app/service/impl/LogRepositoryImplTest.java:14-55` demonstrates the minimal inline `messages` table schema at lines 19-24 and real audit insertion.
- Smallest focused change set for the eventual behavior change:
  1. Production: `src/main/java/org/saturn/app/service/impl/UserServiceImpl.java` (one literal fragment at line 77).
  2. Test: add `src/test/java/org/saturn/app/service/impl/UserServiceImplTest.java` (no existing focused test file to edit). A focused test can use an in-memory H2 `messages` table matching `schema-h2.sql:15-20`, insert recent `JOINED`/`LEFT` rows, invoke `isSeenRecently`, and assert the exact source-level string. `SqlUtil.java`, the listener, `OutService.java`, and schema need not change.

## Focused RED/GREEN test plan

### RED

1. Add one focused test in `UserServiceImplTest` for a user with multiple recent historical names (for example current nick `new-user`, aliases `Alice` and `Bob`, same hash or trip).
2. Create the minimal H2 `messages` table (columns `trip`, `name`, `hash`, `message`, `created_on`; identity/visibility may follow `schema-h2.sql:15-20`) and insert `JOINED`/`LEFT` rows within the query window. Use a real `UserServiceImpl` and `LinkedBlockingQueue`, not a mock of the service or query.
3. Assert the exact `Optional` value contains `recently.` and does **not** contain `in the last 15 minutes`, while also asserting the unchanged prefix/suffix and `\\n` source-level separators. Run only the focused test with `./mvnw -Dtest=UserServiceImplTest test` and confirm it fails because production still emits the old phrase.
4. Keep a second assertion (or a second focused test) for existing behavior that the joining nick itself is filtered from aliases; this protects the requested “replace only” scope without changing normalization conventions.

### GREEN

1. Change only the phrase in `UserServiceImpl.java:77` from `in the last 15 minutes` to `recently`.
2. Rerun `./mvnw -Dtest=UserServiceImplTest test`; it should pass with the exact message and unchanged alias/payload formatting.
3. If the implementation phase requires broader verification, run `./mvnw spotless:check`, `./mvnw test`, and `./mvnw package` as appropriate. This investigation phase intentionally did not edit or run a new test.

## Files created/modified in this investigation

Created:

- `.hermes/diagnostics/recent-join-message-forensics.md` (this file).

Production and test source files: none modified.
