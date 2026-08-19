# Recent Join Message — Phase 2 Implementation QA

## Outcome

Implemented the requested one-fragment wording change and added a focused real-service H2 regression test. No commit or push was performed.

## Strict TDD evidence

### RED

Command:

```text
./mvnw -Dtest=UserServiceImplTest test
```

Result: **BUILD FAILURE** (expected).

- Tests run: 1
- Failures: 1
- Errors: 0
- Failure was the exact expected mismatch: the test expected `recently.` but the old production output contained `in the last 15 minutes.`
- The test exercised a real `UserServiceImpl`, real H2 query/database state, prepared inserts, and a real `LinkedBlockingQueue<String>`.

### GREEN

After the test was red, production was changed only by replacing `in the last 15 minutes` with `recently` in the existing `UserServiceImpl.isSeenRecently` message literal.

Command:

```text
./mvnw -Dtest=UserServiceImplTest test
```

Result: **BUILD SUCCESS**.

- Tests run: 1
- Failures: 0
- Errors: 0
- The exact message assertion passed, including literal `\\n` prefix/suffix, punctuation, alias formatting, `recently.`, absence of the old phrase, and case-insensitive filtering of `NEW-USER`.

## Verification

- `./mvnw spotless:check` — **BUILD SUCCESS**.
- `./mvnw test` — **BUILD FAILURE**, 616 tests run, 1 unrelated failure:
  `OpenAiCompatibleClientTest.mapsOpenAiRequestAndToolCallResponseWithoutRequiringModel:87 expected: <768> but was: <1024>`.
  The focused `UserServiceImplTest` passed in this run.
- `./mvnw package` — **BUILD FAILURE** for the same unrelated full-test failure before packaging.
- `./mvnw -DskipTests package` — **BUILD SUCCESS**; shaded artifact produced at `target/saturn.jar`.
- `git diff --check` — **clean**.

## Task-owned paths

- `src/main/java/org/saturn/app/service/impl/UserServiceImpl.java`
- `src/test/java/org/saturn/app/service/impl/UserServiceImplTest.java`
- `.hermes/qa/recent-join-message-implementation.md`

The existing unrelated dirty and untracked worktree paths were preserved; none were reset, committed, or pushed.
