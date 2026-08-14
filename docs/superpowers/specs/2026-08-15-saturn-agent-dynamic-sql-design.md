# Saturn Agent Dynamic SQL Design

**Status:** Approved for implementation on 2026-08-15.

## Goal

Give Saturn's `*l` agent an admin-only fallback that can inspect the current SQLite schema and
generate read-only SQL when no purpose-built SDK tool can answer a request. Existing named queries
remain the preferred path.

## Decisions

| Concern | Decision |
| --- | --- |
| Access | Only configured admin trips and database users with the `ADMIN` role receive dynamic SQL capabilities. |
| Data scope | Every Saturn application table and column is queryable, including cross-room messages, trip/hash fields, mail, notes, moderation records, command history, and agent memory. |
| Mutation | Generated SQL is strictly read-only. Only one `SELECT` or read-only `WITH ... SELECT` statement is accepted. |
| System scope | SQLite internal tables remain hidden and inaccessible. |
| Fallback order | The model must prefer purpose-built tools, then inspect schema, then use generated SQL only when needed. |
| Execution | Query the live database through a dedicated SQLite connection opened in true read-only mode. |

## Architecture

### Capability-Aware Tool Catalog

`AgentContext` carries a small immutable capability set. The command boundary resolves those
capabilities from Saturn's existing configured admin trips and database roles. `AgentTool` gains an
availability predicate, and the registry generates definitions for the current context. This keeps
the dynamic tools entirely out of non-admin model requests rather than exposing them and rejecting
them later.

The new capability is `DYNAMIC_SQL`. It grants two tools:

- `database_schema` returns user-table metadata: columns, declared types, nullability, primary keys,
  indexes, and foreign keys.
- `database_sql` accepts one SQL string and returns bounded structured rows. It requires a successful
  `database_schema` call earlier in the same invocation.

The existing `database_query` named-query tool remains available to normal callers and remains the
preferred fast path for known operations.

### Ports And Adapters

The SDK adds narrow provider-neutral contracts:

- `AgentSchemaRepository` describes the queryable schema.
- `AgentSqlRepository` executes an already-approved read query and returns a typed result.
- `AgentSqlPolicy` parses and validates generated SQL before execution.

SQLite-specific implementations remain under the persistence adapter package. The composition root
registers both dynamic tools, while context-aware catalog filtering enforces availability.

JSQLParser 5.3 supplies the SQL AST. The policy accepts only a single `Select` statement, traverses
referenced tables, rejects SQLite internal tables, and denies input that cannot be parsed. String or
regular-expression filtering is not used as the security boundary.

## Request Flow

1. `LUserCommandImpl` resolves whether the caller is an admin and builds immutable capabilities.
2. The router sends normal tools for every caller; admin callers also receive `database_schema` and
   `database_sql` definitions.
3. The system prompt directs the model to use a purpose-built tool first.
4. If no purpose-built tool fits, the model calls `database_schema`.
5. A successful schema call unlocks `database_sql` for that invocation.
6. The SQL policy parses and validates the generated statement.
7. The SQLite adapter executes it on a dedicated read-only connection and returns bounded JSON.
8. The router feeds the structured result to the model for final synthesis.

## Defense In Depth

The SQL AST is the first policy layer, not the only one:

- Accept exactly one `SELECT` or read-only CTE statement.
- Reject DML, DDL, `PRAGMA`, `ATTACH`, `DETACH`, `VACUUM`, transaction control, and extension loading.
- Reject references to `sqlite_%` internal tables.
- Open the database with `SQLiteConfig.setReadOnly(true)` and enable `PRAGMA query_only = ON`.
- Keep extension loading disabled.
- Apply SQLite limits for SQL length, columns, expression depth, compound selects, attached
  databases, and worker threads.
- Use `ProgressHandler` with a monotonic deadline to interrupt expensive queries.
- Apply JDBC maximum rows and enforce configured row, column, cell, and total-result limits while
  serializing.
- Use a dedicated connection for every query; never share Saturn's mutable JDBC connection with
  virtual threads.
- Log a SHA-256 query fingerprint, elapsed time, row count, and outcome. Do not log raw generated SQL.

Default limits will be conservative and configurable under `[agent]`: 4,000 SQL characters, 50
rows, 32 columns, 2,000 characters per cell, 32,000 result characters, and a one-second execution
deadline.

## Errors

Tool errors are structured and safe for model correction. Parse rejection, forbidden statement
type, internal-table access, timeout, and oversized results have distinct stable error codes. JDBC
or filesystem details stay in logs and are not returned to chat. A failed schema or SQL call counts
toward the existing per-tool failure and call budgets.

## Testing

Tests use temporary SQLite databases and never call the real LLM endpoint. Coverage includes:

- Capability resolution for configured admins, database `ADMIN` roles, and non-admin callers.
- Tool-definition filtering so non-admin requests cannot see dynamic SQL tools.
- Schema metadata across all application tables while excluding SQLite internals.
- Required schema-before-query ordering within one invocation.
- Accepted joins, aggregates, nested selects, and CTEs.
- Rejected multi-statements, DML, DDL, pragmas, attach/detach, internal tables, and malformed SQL.
- Read-only enforcement even if AST policy is bypassed in a repository-level test.
- Row, column, cell, total-result, SQL-length, and timeout limits.
- Structured null, numeric, text, and blob serialization.
- Router fallback flow and preservation of existing named database queries.

## Documentation And Operations

`config.example.toml` and the README will document dynamic SQL limits, full-schema visibility, and
the admin-only contract. No migration is required. The endpoint still selects the model unless an
explicit model is configured, and API keys remain environment-only.

## Non-Goals

- Generated inserts, updates, deletes, migrations, or schema changes.
- Dynamic SQL for regular, user, trusted, or moderator roles.
- SQLite internal catalog access.
- Replacing existing named queries or authorization-preserving Saturn command tools.
- Persisting or displaying raw generated SQL in logs.
