# Saturn Command Tool Inventory

## Source Of Truth

Every concrete command handler annotated with `@CommandAliases` under
`org.saturn.app.command.impl` is exposed as one agent tool by
`SaturnCommandToolCatalog`. The catalog reflects aliases from the handler annotation, uses its first
alias as the canonical command, and exposes the tool as `saturn_<canonical-alias>`. It rejects
duplicate generated tool names, while `SaturnCommandToolCatalogTest` asserts exact equality between
the reflected command-handler set and catalog entries.

This means new commands cannot be silently omitted: adding an annotated command changes the
reflected set and fails the catalog coverage test until its agent metadata is accepted.

## Contract Standard

All `saturn_*` tools use this closed schema:

```json
{
  "type": "object",
  "properties": {
    "arguments": {
      "type": "string",
      "maxLength": 4000,
      "description": "Exact text after the command alias, without the Saturn prefix."
    }
  },
  "required": [],
  "additionalProperties": false
}
```

`arguments` is optional because some Saturn commands take no arguments. When supplied, it is
validated as a bounded string and rendered after the reflected canonical alias. Saturn’s normal
command parser remains the final command-specific validation and authorization boundary.

Every command tool is `readOnly=false`, `isIdempotent=false`, has a 10-second timeout, and executes
sequentially. Successful and failed calls use the standard SDK response envelope.

## Exposure Matrix

| Handler package | Tool name | Required capability | Effect | Execution |
| --- | --- | --- | --- | --- |
| `command.impl.user` | `saturn_<first alias>` | None | `ROOM_MESSAGE` | Sequential |
| `command.impl.dbz` | `saturn_<first alias>` | None | `ROOM_MESSAGE` | Sequential |
| `command.impl.moderator` | `saturn_<first alias>` | `MODERATION_COMMANDS` | `MODERATION` | Sequential |
| `BanUserCommandImpl`, `UnBanUserCommandImpl`, `UnBanAllUserCommandImpl` | `saturn_ban`, `saturn_unban`, `saturn_unbanall` | `PERMANENT_BAN` | `MODERATION` | Sequential |
| `command.impl.admin` | `saturn_<first alias>` | `ADMIN_COMMANDS` | `ROOM_MESSAGE` | Sequential |

`ADMIN_COMMANDS` is granted only when a direct `*l` invocation originates from the configured
creator trip. It is never granted for mentions, ambient participation, automated moderation, or
non-creator callers. Moderators therefore never receive creator-administration definitions such as
`saturn_restart`, `saturn_shutdown`, `saturn_prefix`, or replica tools.

## Compatibility

`run_command` remains registered as a compatibility bridge for existing routing correction and
moderation automation flows. New provider-facing command selection should prefer the explicit
`saturn_*` contracts because they expose a single canonical command, capability metadata, and a
closed input schema.
