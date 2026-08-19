# Recent Join Message — Final QA

## Verdict

**PASS for the task-owned change.** No task-owned defects were found and no source fixes were made during QA.

The unrelated OpenAI-compatible client test remains a separate **FAIL**; its failure is not caused by this task.

## Scope and diff inspection

Task-owned paths inspected:

- `src/main/java/org/saturn/app/service/impl/UserServiceImpl.java`
- `src/test/java/org/saturn/app/service/impl/UserServiceImplTest.java`

The production diff is limited to the requested message phrase. `git diff --numstat` reports `1 insertion, 2 deletions` because the original string and formatting call were split across two lines; semantically the only change is:

- `in the last 15 minutes` → `recently`

The production diff preserves:

- the SQL query and its 15-minute window (`created_on >= ... - 900000`);
- prepared-statement parameters and query execution;
- result iteration and alias collection;
- case-insensitive filtering of the joining user's nick;
- punctuation and alias formatting;
- escaped `\\n` separators at both message boundaries;
- listener, queue, and payload behavior (no related code changed).

The focused H2 test asserts the exact complete output:

`"\\n @new-user, has been seen as: _Alice_ recently. \\n"`

It also explicitly checks that the old phrase is absent, `NEW-USER` and `new-user_` are absent, the escaped separator `\\n` is present, and an actual newline is absent. The fixture includes both `Alice` and a case-variant `NEW-USER`, exercising alias filtering.

## Required commands and results

1. `./mvnw -Dtest=UserServiceImplTest test`
   - **PASS** — 1 test run, 0 failures, 0 errors, 0 skipped; Maven `BUILD SUCCESS`.

2. `./mvnw spotless:check`
   - **PASS** — Spotless reports all checked Java files clean; Maven `BUILD SUCCESS`.

3. `git diff --check`
   - **PASS** — exit code 0; no whitespace errors reported.

## Unrelated suite failure (clearly separated)

4. `./mvnw -Dtest=OpenAiCompatibleClientTest#mapsOpenAiRequestAndToolCallResponseWithoutRequiringModel test`
   - **FAIL, unrelated to this task** — `OpenAiCompatibleClientTest.java:87` reports `expected: <768> but was: <1024>`.
   - This test is in `org.saturn.app.agent.llm.provider.openai`, while the task-owned production change is confined to `UserServiceImpl.isSeenRecently`; the failure concerns an OpenAI request mapping value and does not exercise the changed service or notification message.

## Repository hygiene

No commit or push was performed. Existing unrelated dirty/untracked files were preserved. The only task-owned source/test paths observed are the two paths listed above; this QA report is additionally created at:

- `.hermes/qa/recent-join-message-final-qa.md`
