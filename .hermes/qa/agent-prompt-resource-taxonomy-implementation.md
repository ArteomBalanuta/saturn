# Agent Prompt Resource Taxonomy Implementation QA

## Scope and migration reference

Implemented the exact one-to-one move map in `.hermes/specs/agent-prompt-resource-taxonomy-implementation-spec.md` (the exhaustive table in that specification, with the classification/rationale table in `.hermes/specs/agent-prompt-resource-taxonomy-mapping.md`). `AgentPromptCatalog.ROOT` remains `/agent/`; no loader behavior or prompt bytes were changed.

Moved all 25 `.txt` resources from the flat `src/main/resources/agent/` directory into `persona/`, `system/`, `input/`, and `correction/`, preserving every filename and byte.

## Changed Java files and resource-call line references

- `src/main/java/org/saturn/app/agent/routing/AgentSystemPrompt.java`: lines 74-75, 78-79, 82-87, 98, 103; database policy, participation policy, system policy, and persona paths now use `system/` or `persona/`.
- `src/main/java/org/saturn/app/agent/routing/DefaultAgentRouter.java`: line 55; finalization path now uses `system/router-finalize.txt`.
- `src/main/java/org/saturn/app/agent/routing/AgentCommandChannelPolicy.java`: lines 30, 32, 34, 144; command correction paths now use `correction/`.
- `src/main/java/org/saturn/app/agent/routing/AgentResponseCorrector.java`: lines 32, 34, 38, 40, 42, 44, 125; response correction paths now use `correction/`.
- `src/main/java/org/saturn/app/agent/routing/AgentRequestAssembler.java`: line 116; contextualized input path now uses `input/`.
- `src/main/java/org/saturn/app/agent/tool/execution/AgentModelVisibleToolResultRenderer.java`: line 17; room-delivery input path now uses `input/`.
- `src/main/java/org/saturn/app/agent/turn/AgentFreshDataCoordinator.java`: lines 25, 27; fresh-data correction paths now use `correction/`.
- `src/test/java/org/saturn/app/agent/routing/AgentPromptCatalogTest.java`: lines 31, 41, 51, 63, and 91; focused direct catalog tests now load the new `persona/`, `correction/`, `system/`, and `input/` paths. Synthetic `missing-prompt.txt`, `broken.txt`, and `tool-copy.json` fixtures remain unchanged.

## Raw-byte integrity

- Pre-move manifest: `/tmp/saturn-agent-prompt-before.tsv`, captured before edits using raw `Path.read_bytes()`.
- Post-move manifest: `/tmp/saturn-agent-prompt-after.tsv`.
- Before: 25 rows, 13,496 bytes.
- After: 25 rows, 13,496 bytes.
- Every one of the 25 move-map pairs matched the pre-move size and SHA-256.
- The `(size, SHA-256)` multiset was identical before and after.
- No flat `.txt` remains directly under `src/main/resources/agent/`; the four taxonomy directories contain exactly the mapped files.
- The flat old resource-path search over `src/main/java` and `src/test/java` returned no matches.
- Clean packaged artifact check: `target/saturn.jar` contains 25 taxonomy `.txt` resources and zero flat `agent/*.txt` resources.

## Test and quality results

- RED check before the move: `./mvnw -Dtest=AgentPromptCatalogTest test` failed as expected for the newly referenced paths because the resources had not yet moved.
- Focused catalog test after move: `./mvnw -Dtest=AgentPromptCatalogTest test` — **PASS**, 11 tests, 0 failures/errors.
- Focused runtime tests: `./mvnw -Dtest=AgentSystemPromptTest,AgentRequestAssemblerTest,AgentCommandChannelPolicyTest,AgentResponseCorrectorTest,AgentModelVisibleToolResultRendererTest,AgentFreshDataTurnPolicyTest test` — **PASS**, 43 tests, 0 failures/errors.
- Clean compile: `./mvnw clean compile` — **PASS**.
- Spotless: `./mvnw spotless:check` — **PASS**.
- Full tests: `./mvnw test` — **PASS**, 615 tests, 0 failures, 0 errors, 5 skipped.
- Package: `./mvnw package` — **PASS**.
- Redundant package: `./mvnw -q -DskipTests package` — **PASS**.
- Diff whitespace: `git diff --check` — **PASS**.

## Historical documentation intentionally untouched

The following historical plans retain their original flat resource references by design, per the implementation specification:

- `docs/superpowers/plans/2026-08-15-vaelen-autonomous-moderator-implementation.md`
- `docs/superpowers/plans/2026-08-15-agent-tool-contract.md`
- `docs/superpowers/plans/2026-08-15-agent-command-tool-enforcement.md`
- `docs/superpowers/plans/2026-08-15-agent-fresh-user-history.md`
- `docs/superpowers/plans/2026-08-16-agent-command-tool-catalog.md`
- `docs/superpowers/plans/2026-08-15-agent-moderator-capabilities.md`

No commit or push was performed. Existing unrelated dirty/untracked files were preserved.
