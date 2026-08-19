# Agent Prompt Resource Taxonomy Implementation Specification

## Scope and invariants

This specification is the implementation contract for the `.txt` resource taxonomy migration proposed in `agent-prompt-resource-taxonomy-mapping.md`.

In scope:

- Move the 25 `.txt` files currently directly under `src/main/resources/agent/` into `persona/`, `system/`, `input/`, or `correction/`.
- Preserve every filename and every byte; this is a directory-only migration.
- Update runtime and direct test resource names from flat names to paths relative to the unchanged `/agent/` catalog root.
- Verify resource count, total size, per-file size, and per-file SHA-256 before and after the move.

Out of scope:

- `tool-copy.json`, `verified-quotes.json`, `/VERSION`, schema resources, or any non-`.txt` resource.
- Changes to prompt text, encoding, whitespace, newline style, loader behavior, or public APIs.
- Historical documentation edits unless a later task explicitly includes them.
- Any unrelated dirty or untracked file.

`AgentPromptCatalog.ROOT` remains `"/agent/"`. Its existing `getResourceAsStream(ROOT + resource)` behavior already accepts relative paths containing subdirectories, so callers should pass names such as `system/system-policy.txt`; no loader-root change is required.

## Exact one-to-one move map

All paths below are repository-relative. Execute each as a move, not a copy followed by an independently recreated file. Create the four destination directories first if needed.

| Current path | Destination path | Bytes | SHA-256 |
|---|---|---:|---|
| `src/main/resources/agent/command-executed-result.txt` | `src/main/resources/agent/input/command-executed-result.txt` | 101 | `adabb295e2b8f8ce7b10568246c77270ae0c30bc52cabc39d6eb685ed1c1e299` |
| `src/main/resources/agent/database-policy-disabled.txt` | `src/main/resources/agent/system/database-policy-disabled.txt` | 81 | `68a25e9d6680180ee358079bc1e03439eefb14abdec81e364778b67b05716116` |
| `src/main/resources/agent/database-policy-enabled.txt` | `src/main/resources/agent/system/database-policy-enabled.txt` | 127 | `b50def4a25b016f32da3eac67a3837efb435c052e7e14d1a5b3dad9530c6d538` |
| `src/main/resources/agent/participation-ambient.txt` | `src/main/resources/agent/system/participation-ambient.txt` | 436 | `9f76ffab316fafcbbf4ffac0d0c67cd7e2f1af979c8bfc78db0f57164a677f52` |
| `src/main/resources/agent/participation-direct.txt` | `src/main/resources/agent/system/participation-direct.txt` | 69 | `a8b33bddb38ae37c11f919dcdb701bef87d38df7c0c829336bf62515e74728ff` |
| `src/main/resources/agent/participation-mention.txt` | `src/main/resources/agent/system/participation-mention.txt` | 94 | `37d11160a5ef52d20056f474ff030d0e811ef30ba9cf6ed76415e3bc56554761` |
| `src/main/resources/agent/participation-moderation.txt` | `src/main/resources/agent/system/participation-moderation.txt` | 845 | `484dc73786799c477df8b207b9e7584372271c6bc66fddbd077b94ecf8d3455a` |
| `src/main/resources/agent/router-command-not-executed-correction.txt` | `src/main/resources/agent/correction/router-command-not-executed-correction.txt` | 225 | `bc5069812cb7f6c3ea64745f1d6b5a61b48224e23242c4d18d739c9f601ef1fc` |
| `src/main/resources/agent/router-command-output-correction.txt` | `src/main/resources/agent/correction/router-command-output-correction.txt` | 157 | `0cdf47fcf59ede8d2079e167a7ceda9e5e616f01c72589b9cf4b39ec7f13af24` |
| `src/main/resources/agent/router-command-tool-correction.txt` | `src/main/resources/agent/correction/router-command-tool-correction.txt` | 772 | `f6602c8188667077b916dc486a56eba8a5e991aa3c85f826c2143a0d903e73f7` |
| `src/main/resources/agent/router-contextualized-prompt.txt` | `src/main/resources/agent/input/router-contextualized-prompt.txt` | 130 | `bb7863f4f2cabdfb6fa996ed70d534256c826bdeb4559a50e5338dcc4b9dffe` |
| `src/main/resources/agent/router-failure-placeholder-correction.txt` | `src/main/resources/agent/correction/router-failure-placeholder-correction.txt` | 308 | `501d9ca79d34937eca1c7b34fec695534b9e1d7ed91eb883e44154ade929bde0` |
| `src/main/resources/agent/router-finalize.txt` | `src/main/resources/agent/system/router-finalize.txt` | 86 | `3433ac75cf15b0f88debbc3a35229e1a596fbbebc3e5916e668cff301df95fd4` |
| `src/main/resources/agent/router-fresh-synthesis-correction.txt` | `src/main/resources/agent/correction/router-fresh-synthesis-correction.txt` | 379 | `aca595a498918043a2383040df77a6fdb1ca110a9225d75fd984d1e2d6ec0fd0` |
| `src/main/resources/agent/router-fresh-tool-correction.txt` | `src/main/resources/agent/correction/router-fresh-tool-correction.txt` | 291 | `6e198e182c4076acc4eb3bf80fe3aa03ab90f9f1550b26cf782e4d214bad716` |
| `src/main/resources/agent/router-internal-evidence-correction.txt` | `src/main/resources/agent/correction/router-internal-evidence-correction.txt` | 392 | `6db3f1108052b86428cebd6afc80f25b811218c7e183edd608e38f391a183afe` |
| `src/main/resources/agent/router-non-command-correction.txt` | `src/main/resources/agent/correction/router-non-command-correction.txt` | 86 | `b7c4e5e5e71159e2e52490349b7f801d49451c02ec147854877d65b4d2c4c3c1` |
| `src/main/resources/agent/router-quote-only-correction.txt` | `src/main/resources/agent/correction/router-quote-only-correction.txt` | 800 | `8c49857b51981dda04d171b2c40ee95290f3e09c041ec25e414bea5d92dab384` |
| `src/main/resources/agent/router-room-delivery.txt` | `src/main/resources/agent/input/router-room-delivery.txt` | 71 | `0a3009c00fa437371c61b620841ce63dc8a6519a6e5e0b9c7fa892cfe06e58cd` |
| `src/main/resources/agent/router-stale-response-correction.txt` | `src/main/resources/agent/correction/router-stale-response-correction.txt` | 207 | `c8bb2744a92835234d35f1cb004c207396337f3db0ea54a29e54e840ba7daa8c` |
| `src/main/resources/agent/router-unavailable-action-response.txt` | `src/main/resources/agent/correction/router-unavailable-action-response.txt` | 168 | `aa118fb357c5e4e8e075129ae5bfed8ef73a33f4e02b16b68e8bb6c566f995d4` |
| `src/main/resources/agent/router-unverified-action-correction.txt` | `src/main/resources/agent/correction/router-unverified-action-correction.txt` | 314 | `3224e82b89210004940c90d58f29b60d1ae1c76234ae440dd305863a16dde802` |
| `src/main/resources/agent/router-unverified-action-final-correction.txt` | `src/main/resources/agent/correction/router-unverified-action-final-correction.txt` | 257 | `b7d5892ab0ac13235d7877536e41ca29d5bd1f3dffd79db957221fad8fe1943b` |
| `src/main/resources/agent/system-policy.txt` | `src/main/resources/agent/system/system-policy.txt` | 6,236 | `c9bfaecc33b1b5ae1c2dabe833540e989197c66c53228c9b72d1cf31f6debb9c` |
| `src/main/resources/agent/vaelen-system-prompt.txt` | `src/main/resources/agent/persona/vaelen-system-prompt.txt` | 864 | `656e08daaabcceb7f5fa903df923797ada7964c919fc981dc9b9a644d4fc562b` |

Expected post-move inventory: exactly 25 recursive `.txt` files, 13,496 total bytes. The sorted `relative-path:sha256:size` manifest digest from the mapping artifact is `29633260d5d76b36754fc9c23d2b31ed2baf7bba920c657a799adf52d2ec3d20` for the original flat-path manifest; after the move, compare hashes and sizes by basename/move-map pair rather than expecting the path-sensitive aggregate digest to remain identical.

## Exact runtime path replacements

Update only the string arguments passed to `AgentPromptCatalog.text(...)` or `.formatted(...)` as follows. Do not change the loader root or unrelated resource constants.

### `src/main/java/org/saturn/app/agent/routing/AgentSystemPrompt.java`

- `database-policy-enabled.txt` -> `system/database-policy-enabled.txt`
- `database-policy-disabled.txt` -> `system/database-policy-disabled.txt`
- `participation-direct.txt` -> `system/participation-direct.txt`
- `participation-mention.txt` -> `system/participation-mention.txt`
- `participation-ambient.txt` -> `system/participation-ambient.txt`
- `participation-moderation.txt` -> `system/participation-moderation.txt`
- `system-policy.txt` -> `system/system-policy.txt`
- `vaelen-system-prompt.txt` -> `persona/vaelen-system-prompt.txt`

### `src/main/java/org/saturn/app/agent/routing/DefaultAgentRouter.java`

- `router-finalize.txt` -> `system/router-finalize.txt`

### `src/main/java/org/saturn/app/agent/routing/AgentCommandChannelPolicy.java`

- `router-command-tool-correction.txt` -> `correction/router-command-tool-correction.txt`
- `router-command-output-correction.txt` -> `correction/router-command-output-correction.txt`
- `router-command-not-executed-correction.txt` -> `correction/router-command-not-executed-correction.txt`
- `router-non-command-correction.txt` -> `correction/router-non-command-correction.txt`

### `src/main/java/org/saturn/app/agent/routing/AgentResponseCorrector.java`

- `router-failure-placeholder-correction.txt` -> `correction/router-failure-placeholder-correction.txt`
- `router-internal-evidence-correction.txt` -> `correction/router-internal-evidence-correction.txt`
- `router-quote-only-correction.txt` -> `correction/router-quote-only-correction.txt`
- `router-unverified-action-correction.txt` -> `correction/router-unverified-action-correction.txt`
- `router-unverified-action-final-correction.txt` -> `correction/router-unverified-action-final-correction.txt`
- `router-unavailable-action-response.txt` -> `correction/router-unavailable-action-response.txt`
- `router-stale-response-correction.txt` -> `correction/router-stale-response-correction.txt`

### `src/main/java/org/saturn/app/agent/routing/AgentRequestAssembler.java`

- `router-contextualized-prompt.txt` -> `input/router-contextualized-prompt.txt`

### `src/main/java/org/saturn/app/agent/tool/execution/AgentModelVisibleToolResultRenderer.java`

- `router-room-delivery.txt` -> `input/router-room-delivery.txt`

### `src/main/java/org/saturn/app/agent/turn/AgentFreshDataCoordinator.java`

- `router-fresh-tool-correction.txt` -> `correction/router-fresh-tool-correction.txt`
- `router-fresh-synthesis-correction.txt` -> `correction/router-fresh-synthesis-correction.txt`

## Exact direct test replacements

In `src/test/java/org/saturn/app/agent/routing/AgentPromptCatalogTest.java`, replace:

- `vaelen-system-prompt.txt` -> `persona/vaelen-system-prompt.txt`
- `router-quote-only-correction.txt` -> `correction/router-quote-only-correction.txt`
- `system-policy.txt` -> `system/system-policy.txt` (both occurrences)
- `command-executed-result.txt` -> `input/command-executed-result.txt`

Keep synthetic failure fixtures unchanged unless the implementation separately changes diagnostics. These are not inventory files:

- `missing-prompt.txt` remains synthetic and should still produce `/agent/missing-prompt.txt`.
- `broken.txt` remains synthetic and should still produce `/agent/broken.txt`.
- `tool-copy.json` remains the unchanged JSON fixture/resource.

No other test path replacements are implied by this migration; search all `src/test` `.txt` references after implementation to confirm that no inventory basename remains flat.

## Resource integrity verification

Run from the repository root. Capture the pre-move manifest before any move and retain it outside the changed resource tree, for example `/tmp/saturn-agent-prompt-before.tsv`. The manifest must use raw bytes, not decoded text:

```sh
python3 - <<'PY' > /tmp/saturn-agent-prompt-before.tsv
from pathlib import Path
import hashlib
root = Path("src/main/resources/agent")
for path in sorted(root.rglob("*.txt")):
    data = path.read_bytes()
    rel = path.relative_to(root).as_posix()
    print(f"{rel}\t{len(data)}\t{hashlib.sha256(data).hexdigest()}")
PY
```

Before editing, require 25 rows, a total size of 13,496 bytes, and the per-file values in the move map. After moving resources and updating references, create `/tmp/saturn-agent-prompt-after.tsv` with the same command. Verify:

1. Exactly 25 rows exist and no `.txt` remains directly under `agent/`.
2. The total byte sum is 13,496.
3. For each move-map pair, destination size equals source size from the pre-manifest and destination SHA-256 equals the expected hash.
4. The multiset of `(size, SHA-256)` pairs is identical before and after, with no duplicate or missing file.
5. All four destination directories contain only the mapped `.txt` files.

A move-map-aware verification script should compare the two manifests by translating each original basename to its destination path; do not compare the path-sensitive aggregate digest as equal. The original mapping artifact's aggregate digest (`29633260d5d76b36754fc9c23d2b31ed2baf7bba920c657a799adf52d2ec3d20`) is useful as a pre-move audit check only.

Also run `git diff --check` and inspect `git status --short`; the resource changes should appear as renames where Git can detect them, and unrelated dirty/untracked files must remain untouched.

## Historical documentation handling

Do not edit historical plans as part of this implementation. Record their stale paths in the implementation/PR notes if needed. The known historical references are:

- `docs/superpowers/plans/2026-08-15-vaelen-autonomous-moderator-implementation.md`: `vaelen-system-prompt.txt`
- `docs/superpowers/plans/2026-08-15-agent-tool-contract.md`: `vaelen-system-prompt.txt`
- `docs/superpowers/plans/2026-08-15-agent-command-tool-enforcement.md`: `vaelen-system-prompt.txt`
- `docs/superpowers/plans/2026-08-15-agent-fresh-user-history.md`: `router-fresh-tool-correction.txt`, `system-policy.txt`
- `docs/superpowers/plans/2026-08-16-agent-command-tool-catalog.md`: `system-policy.txt`, `participation-moderation.txt`, `router-command-tool-correction.txt`
- `docs/superpowers/plans/2026-08-15-agent-moderator-capabilities.md`: `system-policy.txt`

If a future task explicitly includes historical docs, update those references to the corresponding repository paths at that time and verify that no runtime source or test changes are accidentally included. Do not treat historical references as evidence of live callers.

## Focused verification commands

After the resource move and exact caller updates:

```sh
./mvnw -Dtest=AgentPromptCatalogTest test
./mvnw -Dtest=AgentSystemPromptTest,AgentRequestAssemblerTest,AgentCommandChannelPolicyTest,AgentResponseCorrectorTest,AgentModelVisibleToolResultRendererTest,AgentFreshDataTurnPolicyTest test
./mvnw spotless:check
```

The first command proves direct catalog loading, formatted loading, missing-resource diagnostics, and synthetic I/O failures. The second exercises the main runtime caller families. If Maven's test selector does not accept the comma-separated form in the local Surefire version, run the same classes as separate `-Dtest=<Class>` invocations.

Before final reporting, confirm there are no flat references to the 25 moved basenames in `src/main/java` or `src/test/java`:

```sh
grep -RInE '"(command-executed-result|database-policy-(disabled|enabled)|participation-(ambient|direct|mention|moderation)|router-[^"]+|system-policy|vaelen-system-prompt)\.txt"' src/main/java src/test/java || true
```

The command is a search aid, not a substitute for reviewing the intended new subpaths; any remaining match should be inspected because a quoted basename may be synthetic or intentionally unrelated.

## Full verification commands

Run the repository-required quality gates after focused tests pass:

```sh
./mvnw spotless:check
./mvnw test
./mvnw package
./mvnw -q -DskipTests package

git diff --check
git status --short
```

`./mvnw package` is the assembled-artifact verification. The final `-DskipTests` package is optional redundancy if the ordinary package already completed; it must not replace `./mvnw test`. Inspect the packaged classes/resources if needed to confirm the four subdirectories are present and the old flat resources are absent:

```sh
jar tf target/*.jar | grep -E '^agent/(persona|system|input|correction)/.*\.txt$'
jar tf target/*.jar | grep -E '^agent/[^/]+\.txt$' && exit 1 || true
```

Do not commit or push as part of this phase or the implementation that follows unless explicitly requested.
