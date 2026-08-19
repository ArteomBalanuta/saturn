# Agent Prompt Resource Taxonomy Mapping

## Phase 0 scope

This is an inventory and migration proposal only. No production, test, or resource file was moved or edited. The only file created by this phase is this specification.

Inventory root: `src/main/resources/agent`

* Inventory: **25** recursive `.txt` files
* Total content size: **13,496 bytes**
* Hash algorithm: SHA-256 over each file's exact bytes
* Manifest verification digest (sorted `relative-path:sha256:size` lines): `29633260d5d76b36754fc9c23d2b31ed2baf7bba920c657a799adf52d2ec3d20`

## Taxonomy rules

* **persona/** — identity, voice, or role/objective prose supplied as the persona.
* **system/** — normative runtime policy and baseline orchestration instructions, including mode and capability policy fragments.
* **input/** — request/context/result-state material assembled as prompt input, or instructions describing an already-completed delivery.
* **correction/** — repair, guard, fallback, freshness, anti-duplication, or tool-routing correction instructions.

Destination paths below preserve every original filename and propose only a directory change.

## Exhaustive migration table

| Original relative path | Classification | Proposed destination | Rationale | Size (bytes) | SHA-256 |
|---|---|---|---|---:|---|
| `command-executed-result.txt` | input | `input/command-executed-result.txt` | Reports command execution state to the model; it is result/context input rather than policy or persona. | 101 | `adabb295e2b8f8ce7b10568246c77270ae0c30bc52cabc39d6eb685ed1c1e299` |
| `database-policy-disabled.txt` | system | `system/database-policy-disabled.txt` | Capability-dependent database-use policy that constrains tool selection. | 81 | `68a25e9d6680180ee358079bc1e03439eefb14abdec81e364778b67b05716116` |
| `database-policy-enabled.txt` | system | `system/database-policy-enabled.txt` | Capability-dependent database-use policy that constrains tool selection and SQL safety. | 127 | `b50def4a25b016f32da3eac67a3837efb435c052e7e14d1a5b3dad9530c6d538` |
| `participation-ambient.txt` | system | `system/participation-ambient.txt` | Mode-specific participation policy defining when to answer and the exact silence markers. | 436 | `9f76ffab316fafcbbf4ffac0d0c67cd7e2f1af979c8bfc78db0f57164a677f52` |
| `participation-direct.txt` | system | `system/participation-direct.txt` | Mode-specific policy requiring an answer to the caller. | 69 | `a8b33bddb38ae37c11f919dcdb701bef87d38df7c0c829336bf62515e74728ff` |
| `participation-mention.txt` | system | `system/participation-mention.txt` | Mode-specific policy requiring an answer after a direct mention. | 94 | `37d11160a5ef52d20056f474ff030d0e811ef30ba9cf6ed76415e3bc56554761` |
| `participation-moderation.txt` | system | `system/participation-moderation.txt` | Normative silent-moderation behavior and authority boundaries. | 845 | `484dc73786799c477df8b207b9e7584372271c6bc66fddbd077b94ecf8d3455a` |
| `router-command-not-executed-correction.txt` | correction | `correction/router-command-not-executed-correction.txt` | Corrects the model's response after a command failed to execute and tools are unavailable. | 225 | `bc5069812cb7f6c3ea64745f1d6b5a61b48224e23242c4d18d739c9f601ef1fc` |
| `router-command-output-correction.txt` | correction | `correction/router-command-output-correction.txt` | Corrects post-command response behavior when the command already executed. | 157 | `0cdf47fcf59ede8d2079e167a7ceda9e5e616f01c72589b9cf4b39ec7f13af24` |
| `router-command-tool-correction.txt` | correction | `correction/router-command-tool-correction.txt` | Repairs Markdown-command hallucinations and routes the model to the proper tool/response function. | 772 | `f6602c8188667077b916dc486a56eba8a5e991aa3c85f826c2143a0d903e73f7` |
| `router-contextualized-prompt.txt` | input | `input/router-contextualized-prompt.txt` | Template that assembles the newest request with room, author, visibility, and conversation context. | 130 | `bb7863f4f2cabdfb6fa996ed70d534256c826bdeb4559a50e5338dcc4b9dffe2` |
| `router-failure-placeholder-correction.txt` | correction | `correction/router-failure-placeholder-correction.txt` | Repairs a placeholder completion by requiring a grounded answer from existing evidence. | 308 | `501d9ca79d34937eca1c7b34fec695534b9e1d7ed91eb883e44154ade929bde0` |
| `router-finalize.txt` | system | `system/router-finalize.txt` | Baseline finalization instruction: answer from supplied tool results and do not call tools. | 86 | `3433ac75cf15b0f88debbc3a35229e1a596fbbebc3e5916e668cff301df95fd4` |
| `router-fresh-synthesis-correction.txt` | correction | `correction/router-fresh-synthesis-correction.txt` | Repairs synthesis after a fresh-history lookup by requiring complete returned data and range reporting. | 379 | `aca595a498918043a2383040df77a6fdb1ca110a9225d75fd984d1e2d6ec0fd0` |
| `router-fresh-tool-correction.txt` | correction | `correction/router-fresh-tool-correction.txt` | Forces a required fresh-data tool call instead of relying on stale memory or prior summaries. | 291 | `6e198e182c4076acc4eb3bf80fe3aa03ab90f9f1550b26cf782e4d214bad7164` |
| `router-internal-evidence-correction.txt` | correction | `correction/router-internal-evidence-correction.txt` | Prevents exposure of internal tool-memory envelopes and stale-evidence claims. | 392 | `6db3f1108052b86428cebd6afc80f25b811218c7e183edd608e38f391a183afe` |
| `router-non-command-correction.txt` | correction | `correction/router-non-command-correction.txt` | Corrects handling of a referenced Markdown command that must not execute. | 86 | `b7c4e5e5e71159e2e52490349b7f801d49451c02ec147854877d65b4d2c4c3c1` |
| `router-quote-only-correction.txt` | correction | `correction/router-quote-only-correction.txt` | Enforces exact verified-quotation output after quote-mode drift. | 800 | `8c49857b51981dda04d171b2c40ee95290f3e09c041ec25e414bea5d92dab384` |
| `router-room-delivery.txt` | input | `input/router-room-delivery.txt` | Tells the model that tool output was already delivered; this is delivery-state input. | 71 | `0a3009c00fa437371c61b620841ce63dc8a6519a6e5e0b9c7fa892cfe06e58cd` |
| `router-stale-response-correction.txt` | correction | `correction/router-stale-response-correction.txt` | Prevents duplicating an earlier assistant response and reorients to the newest message. | 207 | `c8bb2744a92835234d35f1cb004c207396337f3db0ea54a29e54e840ba7daa8c` |
| `router-unavailable-action-response.txt` | correction | `correction/router-unavailable-action-response.txt` | Safe fallback for an unsupported live operation; explicitly prevents false claims of execution or lookup. | 168 | `aa118fb357c5e4e8e075129ae5bfed8ef73a33f4e02b16b68e8bb6c566f995d4` |
| `router-unverified-action-correction.txt` | correction | `correction/router-unverified-action-correction.txt` | Repairs narrated-but-unexecuted actions and requires a matching tool call or honest limitation. | 314 | `3224e82b89210004940c90d58f29b60d1ae1c76234ae440dd305863a16dde802` |
| `router-unverified-action-final-correction.txt` | correction | `correction/router-unverified-action-final-correction.txt` | Final guard against promising an action instead of returning the tool call or capability limitation. | 257 | `b7d5892ab0ac13235d7877536e41ca29d5bd1f3dffd79db957221fad8fe1943b` |
| `system-policy.txt` | system | `system/system-policy.txt` | Primary Saturn runtime policy governing priority, tools, continuity, output style, authority, and safety. | 6,236 | `c9bfaecc33b1b5ae1c2dabe833540e989197c66c53228c9b72d1cf31f6debb9c` |
| `vaelen-system-prompt.txt` | persona | `persona/vaelen-system-prompt.txt` | Literary quotation-engine role, objective, output voice, and persona constraints. | 864 | `656e08daaabcceb7f5fa903df923797ada7964c919fc981dc9b9a644d4fc562b` |

**Table verification note:** the table contains all 25 files returned by recursive `*.txt` enumeration. The proposed migration is one-to-one: no rename, deletion, or content transformation is proposed.

## Resource-loading and old-path reference audit

### Production resource loading

`AgentPromptCatalog` currently defines `ROOT = "/agent/"` and loads arbitrary catalog names through `AgentPromptCatalog.class.getResourceAsStream(ROOT + resource)`. All `.txt` callers currently pass flat filenames; those callers must eventually pass the proposed subdirectory path (for example, `system-policy.txt` becomes `system/system-policy.txt`). The loader root can remain `/agent/` if the future implementation passes the relative subdirectory path.

Current `.txt` call sites found under `src/main/java`:

| Source | Current resource name(s) | Proposed resource name(s) |
|---|---|---|
| `src/main/java/org/saturn/app/agent/AgentSystemPrompt.java` | `database-policy-enabled.txt`, `database-policy-disabled.txt`, `participation-direct.txt`, `participation-mention.txt`, `participation-ambient.txt`, `participation-moderation.txt`, `system-policy.txt`, `vaelen-system-prompt.txt` | `system/database-policy-enabled.txt`, `system/database-policy-disabled.txt`, `system/participation-direct.txt`, `system/participation-mention.txt`, `system/participation-ambient.txt`, `system/participation-moderation.txt`, `system/system-policy.txt`, `persona/vaelen-system-prompt.txt` |
| `src/main/java/org/saturn/app/agent/routing/DefaultAgentRouter.java` | `router-finalize.txt` | `system/router-finalize.txt` |
| `src/main/java/org/saturn/app/agent/routing/AgentCommandChannelPolicy.java` | `router-command-tool-correction.txt`, `router-command-output-correction.txt`, `router-command-not-executed-correction.txt`, `router-non-command-correction.txt` | Corresponding `correction/` paths |
| `src/main/java/org/saturn/app/agent/routing/AgentResponseCorrector.java` | `router-failure-placeholder-correction.txt`, `router-internal-evidence-correction.txt`, `router-quote-only-correction.txt`, `router-unverified-action-correction.txt`, `router-unverified-action-final-correction.txt`, `router-unavailable-action-response.txt`, `router-stale-response-correction.txt` | Corresponding `correction/` paths |
| `src/main/java/org/saturn/app/agent/routing/AgentRequestAssembler.java` | `router-contextualized-prompt.txt` | `input/router-contextualized-prompt.txt` |
| `src/main/java/org/saturn/app/agent/tool/execution/AgentModelVisibleToolResultRenderer.java` | `router-room-delivery.txt` | `input/router-room-delivery.txt` |
| `src/main/java/org/saturn/app/agent/turn/AgentFreshDataCoordinator.java` | `router-fresh-tool-correction.txt`, `router-fresh-synthesis-correction.txt` | Corresponding `correction/` paths |

Other `getResourceAsStream` uses found under `src/main/java` are unrelated to these `.txt` files: `VerifiedQuoteCatalog` loads `/agent/verified-quotes.json`, `H2SchemaBootstrapper` loads the H2 schema, and `VersionUserCommandImpl` loads `/VERSION`. They should not be changed as part of this `.txt` taxonomy migration.

### Test references

`src/test/java/org/saturn/app/agent/routing/AgentPromptCatalogTest.java` directly names the flat resources `vaelen-system-prompt.txt`, `router-quote-only-correction.txt`, `system-policy.txt`, and `command-executed-result.txt`; its eventual assertions and any resource-path fixtures should be updated to the proposed subdirectory names. Its intentional missing/broken-resource cases use synthetic names and error-message assertions (`/agent/missing-prompt.txt`, `/agent/broken.txt`, `/agent/tool-copy.json`); these are not inventory files, but their expected messages may need review if loader diagnostics change.

### Documentation references

The scanned `docs/` tree contains old flat `.txt` references in historical plans, notably:

* `docs/superpowers/plans/2026-08-15-vaelen-autonomous-moderator-implementation.md` references `src/main/resources/agent/vaelen-system-prompt.txt` and `/agent/vaelen-system-prompt.txt`.
* `docs/superpowers/plans/2026-08-15-agent-tool-contract.md` references `src/main/resources/agent/vaelen-system-prompt.txt`.
* `docs/superpowers/plans/2026-08-15-agent-command-tool-enforcement.md` references `src/main/resources/agent/vaelen-system-prompt.txt`.
* `docs/superpowers/plans/2026-08-15-agent-fresh-user-history.md` references `src/main/resources/agent/router-fresh-tool-correction.txt` and `src/main/resources/agent/system-policy.txt`.
* `docs/superpowers/plans/2026-08-16-agent-command-tool-catalog.md` references `src/main/resources/agent/system-policy.txt`, `participation-moderation.txt`, and `router-command-tool-correction.txt`.
* `docs/superpowers/plans/2026-08-15-agent-moderator-capabilities.md` references `src/main/resources/agent/system-policy.txt`.

These are historical plan documents, not runtime loaders. They should be updated only if the migration task explicitly includes historical documentation; otherwise record them as stale historical paths.

### Search coverage and exclusions

Search coverage included `src/` (production and tests), `docs/`, `AGENTS.md`, and `pom.xml`, looking for `agent/`, `.txt`, `getResourceAsStream`, and resource-loading references. No production/test/resource file was modified. The repository also contains unrelated dirty/untracked IDE, database, configuration, and `.hermes` artifacts; they were preserved untouched.

## Phase 1 implementation implications

1. Create the four directories and move the 25 files one-to-one without changing bytes.
2. Update every production caller in the table and the direct test references to the proposed relative paths.
3. Decide explicitly whether historical plan paths are updated; do not silently rewrite them as part of production migration.
4. Add/adjust resource-loading tests so each proposed path resolves and hashes/contents remain unchanged.
5. Run focused tests, formatting checks, the full Maven test suite, and package verification after edits; those validations are intentionally deferred from this Phase 0 inventory.
