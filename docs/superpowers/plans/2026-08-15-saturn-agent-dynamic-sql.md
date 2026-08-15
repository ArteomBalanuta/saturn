# Saturn Agent Dynamic SQL Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add admin-only schema introspection and bounded, generated read-only SQL as a fallback for Saturn's `*l` agent.

**Architecture:** Extend the agent SDK with immutable capabilities and context-filtered tools. Parse generated SQL into an AST before executing it through a dedicated SQLite connection opened in true read-only mode, with schema, execution, and result limits layered around the query.

**Tech Stack:** Java 23, JSQLParser 5.3, Gson, SQLite JDBC 3.41.2.2, JUnit 5.

## Global Constraints

- Work directly on `develop` as requested and preserve all existing uncommitted agent-runtime work.
- Dynamic SQL is visible only to configured admin trips and database users with the `ADMIN` role.
- Every Saturn application table and column is queryable for admins; `sqlite_%` internals are not.
- Accept exactly one `SELECT` or read-only `WITH ... SELECT`; generated writes and schema changes are never allowed.
- Keep existing named `database_query` operations as the preferred path.
- Do not call the real LLM endpoint from tests.
- Do not log generated SQL; log only its SHA-256 fingerprint and bounded execution metadata.
- Defaults: 4,000 SQL characters, 50 rows, 32 columns, 2,000 characters per cell, 32,000 result characters, and a one-second deadline.

---

### Task 1: Dynamic SQL Configuration And Parser Dependency

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/org/saturn/app/agent/AgentSqlConfig.java`
- Create: `src/test/java/org/saturn/app/agent/AgentSqlConfigTest.java`
- Modify: `config.example.toml`

**Interfaces:**
- Consumes: root `Toml` configuration.
- Produces: `AgentSqlConfig.from(Toml)` and JSQLParser on the application classpath.

- [x] **Step 1: Write failing configuration tests**

```java
@Test
void readsDynamicSqlLimitsAndAppliesDefaults() {
  AgentSqlConfig defaults = AgentSqlConfig.from(new Toml());
  assertTrue(defaults.enabled());
  assertEquals(4_000, defaults.maxSqlChars());
  assertEquals(50, defaults.maxRows());
  assertEquals(Duration.ofSeconds(1), defaults.timeout());
}

@Test
void rejectsNonPositiveDynamicSqlLimits() {
  Toml config = new Toml().read("""
      [agent]
      dynamicSqlMaxRows = 0
      """);
  assertThrows(IllegalArgumentException.class, () -> AgentSqlConfig.from(config));
}
```

- [x] **Step 2: Run the focused test and confirm the missing type failure**

Run: `./mvnw -q -Dtest=AgentSqlConfigTest test`

Expected: test compilation fails because `AgentSqlConfig` does not exist.

- [x] **Step 3: Add immutable validated configuration and JSQLParser 5.3**

```java
public record AgentSqlConfig(
    boolean enabled,
    int maxSqlChars,
    int maxRows,
    int maxColumns,
    int maxCellChars,
    int maxResultChars,
    Duration timeout) {
  public static AgentSqlConfig from(Toml root) {
    return new AgentSqlConfig(
        root.getBoolean("agent.dynamicSqlEnabled", true),
        Math.toIntExact(root.getLong("agent.dynamicSqlMaxSqlChars", 4_000L)),
        Math.toIntExact(root.getLong("agent.dynamicSqlMaxRows", 50L)),
        Math.toIntExact(root.getLong("agent.dynamicSqlMaxColumns", 32L)),
        Math.toIntExact(root.getLong("agent.dynamicSqlMaxCellChars", 2_000L)),
        Math.toIntExact(root.getLong("agent.dynamicSqlMaxResultChars", 32_000L)),
        Duration.ofMillis(root.getLong("agent.dynamicSqlTimeoutMillis", 1_000L)));
  }
}
```

Add `com.github.jsqlparser:jsqlparser:5.3` and document all seven `[agent]` keys in
`config.example.toml`.

- [x] **Step 4: Run focused tests**

Run: `./mvnw -q -Dtest=AgentSqlConfigTest test`

Expected: PASS.

---

### Task 2: Capabilities, Admin Resolution, And Tool Preconditions

**Files:**
- Create: `src/main/java/org/saturn/app/agent/AgentCapability.java`
- Modify: `src/main/java/org/saturn/app/agent/AgentContext.java`
- Modify: `src/main/java/org/saturn/app/agent/AgentTool.java`
- Modify: `src/main/java/org/saturn/app/agent/AgentToolRegistry.java`
- Modify: `src/main/java/org/saturn/app/agent/AgentToolExecutor.java`
- Modify: `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java`
- Modify: `src/main/java/org/saturn/app/service/AuthorizationService.java`
- Modify: `src/main/java/org/saturn/app/service/impl/AuthorizationServiceImpl.java`
- Modify: `src/main/java/org/saturn/app/command/impl/user/LUserCommandImpl.java`
- Test: `src/test/java/org/saturn/app/agent/AgentToolRegistryTest.java`
- Test: `src/test/java/org/saturn/app/agent/AgentToolExecutorTest.java`
- Test: `src/test/java/org/saturn/app/command/impl/user/LUserCommandImplTest.java`
- Create: `src/test/java/org/saturn/app/service/impl/AuthorizationServiceImplTest.java`

**Interfaces:**
- Produces: `AgentContext.hasCapability(AgentCapability)`, `AgentTool.isAvailableTo(context)`,
  `AgentTool.requiredSuccessfulTools()`, and `AuthorizationService.resolveRole(trip)`.
- Consumes: these capabilities in all later dynamic tools.

- [x] **Step 1: Write failing capability and precondition tests**

```java
AgentTool adminTool = new TestTool("database_sql") {
  public boolean isAvailableTo(AgentContext context) {
    return context.hasCapability(AgentCapability.DYNAMIC_SQL);
  }
  public Set<String> requiredSuccessfulTools() {
    return Set.of("database_schema");
  }
};

assertFalse(registry.definitions(regularContext()).toString().contains("database_sql"));
assertTrue(registry.definitions(adminContext()).toString().contains("database_sql"));
assertTrue(executor.execute(adminContext(), sqlCall).isError());
executor.execute(adminContext(), schemaCall);
assertFalse(executor.execute(adminContext(), sqlCall).isError());
```

Add command tests proving configured admins receive `DYNAMIC_SQL`, regular callers do not, and an
authorization-service test proving a persisted `ADMIN` role resolves to `Role.ADMIN`.

- [x] **Step 2: Run focused tests and confirm missing API failures**

Run: `./mvnw -q -Dtest=AgentToolRegistryTest,AgentToolExecutorTest,LUserCommandImplTest,AuthorizationServiceImplTest test`

Expected: compilation failures for capability-aware APIs.

- [x] **Step 3: Implement context-aware catalog and successful-tool tracking**

```java
public enum AgentCapability { DYNAMIC_SQL }

public interface AgentTool {
  default boolean isAvailableTo(AgentContext context) { return true; }
  default Set<String> requiredSuccessfulTools() { return Set.of(); }
}
```

Keep the existing six-argument `AgentContext` constructor as a compatibility overload that supplies
`Set.of()`. Filter both registry definitions and lookup by context. Add a successful-tool set to each
`AgentToolExecutor` invocation and reject unmet preconditions without invoking the tool.

Expose `Role resolveRole(String trip)` from `AuthorizationService`; return `REGULAR` when no role is
stored. `LUserCommandImpl` grants `DYNAMIC_SQL` when the trip is in `getAdminTrips(engine)` or resolves
to database role `ADMIN`.

- [x] **Step 4: Pass context through router definitions and run focused tests**

Run: `./mvnw -q -Dtest=AgentToolRegistryTest,AgentToolExecutorTest,DefaultAgentRouterTest,LUserCommandImplTest,AuthorizationServiceImplTest test`

Expected: PASS.

---

### Task 3: Full Application Schema Introspection

**Files:**
- Create: `src/main/java/org/saturn/app/agent/persistence/AgentDatabaseSchema.java`
- Create: `src/main/java/org/saturn/app/agent/persistence/AgentSchemaRepository.java`
- Create: `src/main/java/org/saturn/app/agent/persistence/SqliteReadOnlyConnectionFactory.java`
- Create: `src/main/java/org/saturn/app/agent/persistence/SqliteAgentSchemaRepository.java`
- Create: `src/main/java/org/saturn/app/agent/tool/DatabaseSchemaTool.java`
- Create: `src/test/java/org/saturn/app/agent/persistence/SqliteAgentSchemaRepositoryTest.java`
- Modify: `src/test/java/org/saturn/app/agent/tool/SaturnAgentToolsTest.java`

**Interfaces:**
- Produces: `AgentSchemaRepository.describe()` returning `AgentDatabaseSchema` and
  `AgentDatabaseSchema.tableNames()` / `findTable(String)`.
- Consumes: `AgentSqlConfig` and `AgentCapability.DYNAMIC_SQL`.

- [x] **Step 1: Write failing temporary-database introspection tests**

```java
AgentDatabaseSchema schema = repository.describe();
assertTrue(schema.tableNames().contains("messages"));
assertTrue(schema.findTable("messages").orElseThrow().columns().stream()
    .anyMatch(column -> column.name().equals("trip")));
assertFalse(schema.tableNames().stream().anyMatch(name -> name.startsWith("sqlite_")));
assertFalse(schema.findTable("trip_names").orElseThrow().foreignKeys().isEmpty());
```

Add a tool test proving `database_schema` is unavailable without `DYNAMIC_SQL` and returns structured
metadata for an admin.

- [x] **Step 2: Run focused tests and confirm missing repository failures**

Run: `./mvnw -q -Dtest=SqliteAgentSchemaRepositoryTest,SaturnAgentToolsTest test`

Expected: compilation failures for schema contracts.

- [x] **Step 3: Implement typed metadata and read-only connection creation**

`AgentDatabaseSchema` contains nested immutable `Table`, `Column`, `Index`, and `ForeignKey` records.
Use `sqlite_master`, `PRAGMA table_info`, `PRAGMA index_list/index_info`, and
`PRAGMA foreign_key_list`; identifiers originate only from `sqlite_master` and are quoted by doubling
embedded quotes.

`SqliteReadOnlyConnectionFactory.open()` uses `SQLiteConfig.setReadOnly(true)`, disables extension
loading, sets a busy timeout, and executes `PRAGMA query_only = ON`.

- [x] **Step 4: Implement `DatabaseSchemaTool` and run focused tests**

The tool has no arguments, requires `DYNAMIC_SQL`, and returns the typed schema through Gson.

Run: `./mvnw -q -Dtest=SqliteAgentSchemaRepositoryTest,SaturnAgentToolsTest test`

Expected: PASS.

---

### Task 4: AST SQL Policy

**Files:**
- Create: `src/main/java/org/saturn/app/agent/sql/AgentSqlErrorCode.java`
- Create: `src/main/java/org/saturn/app/agent/sql/AgentSqlPolicyException.java`
- Create: `src/main/java/org/saturn/app/agent/sql/ValidatedAgentSql.java`
- Create: `src/main/java/org/saturn/app/agent/sql/AgentSqlPolicy.java`
- Create: `src/main/java/org/saturn/app/agent/sql/JSqlParserAgentSqlPolicy.java`
- Create: `src/test/java/org/saturn/app/agent/sql/JSqlParserAgentSqlPolicyTest.java`

**Interfaces:**
- Consumes: raw SQL, `AgentSqlConfig`, and `AgentDatabaseSchema`.
- Produces: `ValidatedAgentSql(sql, fingerprint)` or a typed policy exception.

- [x] **Step 1: Write failing policy matrix tests**

Accepted cases include joins, aggregates, nested selects, `UNION`, and read-only CTEs. Rejected cases
include two statements, `INSERT`, `UPDATE`, `DELETE`, `CREATE`, `DROP`, `PRAGMA`, `ATTACH`, `DETACH`,
`VACUUM`, `load_extension(...)`, table-valued pragma functions, malformed input, overlong input,
unknown tables, and `sqlite_master`.

```java
assertDoesNotThrow(() -> policy.validate(
    "WITH recent AS (SELECT * FROM messages) SELECT count(*) FROM recent", schema));
assertEquals(AgentSqlErrorCode.FORBIDDEN_STATEMENT,
    assertThrows(AgentSqlPolicyException.class,
        () -> policy.validate("DELETE FROM messages", schema)).code());
assertEquals(AgentSqlErrorCode.FORBIDDEN_TABLE,
    assertThrows(AgentSqlPolicyException.class,
        () -> policy.validate("SELECT * FROM sqlite_master", schema)).code());
```

- [x] **Step 2: Run the focused test and confirm missing policy failures**

Run: `./mvnw -q -Dtest=JSqlParserAgentSqlPolicyTest test`

Expected: compilation failure.

- [x] **Step 3: Implement single-`Select` AST validation and table traversal**

Parse with `CCJSqlParserUtil.parse`, require `statement instanceof Select`, and use
`TablesNamesFinder` to collect physical tables. Normalize identifiers case-insensitively, account for
CTE aliases, reject extension-loading and pragma table functions found in the AST, and require every
physical table to exist in `AgentDatabaseSchema.tableNames()`.

Compute the fingerprint with SHA-256 over UTF-8 SQL and retain the original SQL only inside the
validated value passed to the executor.

- [x] **Step 4: Run policy tests**

Run: `./mvnw -q -Dtest=JSqlParserAgentSqlPolicyTest test`

Expected: PASS.

---

### Task 5: Bounded Read-Only SQL Execution

**Files:**
- Create: `src/main/java/org/saturn/app/agent/persistence/AgentSqlResult.java`
- Create: `src/main/java/org/saturn/app/agent/persistence/AgentSqlRepository.java`
- Create: `src/main/java/org/saturn/app/agent/persistence/SqliteAgentSqlRepository.java`
- Create: `src/test/java/org/saturn/app/agent/persistence/SqliteAgentSqlRepositoryTest.java`

**Interfaces:**
- Consumes: `ValidatedAgentSql` and `AgentSqlConfig`.
- Produces: `AgentSqlResult(columns, rows, truncated, elapsedMillis)` with JSON-safe values.

- [x] **Step 1: Write failing executor tests**

Cover null, integer, floating point, text, and blob values; max rows; max columns; Unicode-safe cell
truncation; total JSON size; timeout; and actual read-only enforcement using a deliberately constructed
`ValidatedAgentSql("DELETE ...")` that bypasses the policy.

```java
assertThrows(AgentPersistenceException.class,
    () -> repository.execute(new ValidatedAgentSql("DELETE FROM messages", "test"), config));
assertEquals(50, repository.execute(selectAll, config).rows().size());
assertTrue(repository.execute(selectAll, config).truncated());
```

- [x] **Step 2: Run the focused test and confirm missing executor failures**

Run: `./mvnw -q -Dtest=SqliteAgentSqlRepositoryTest test`

Expected: compilation failure.

- [x] **Step 3: Implement SQLite limits, progress cancellation, and bounded serialization**

Set SQLite limits for SQL length, columns, expression depth, compound selects, attached databases,
and worker threads. Register `ProgressHandler` with a monotonic deadline and clear it in `finally`.
Use `Statement.setMaxRows(maxRows + 1)` to detect truncation. Represent result rows as arrays aligned
with a separate columns array, Base64-encode blobs, and truncate strings by Unicode code point.

- [x] **Step 4: Run executor tests**

Run: `./mvnw -q -Dtest=SqliteAgentSqlRepositoryTest test`

Expected: PASS.

---

### Task 6: Dynamic SQL Tool And Runtime Wiring

**Files:**
- Create: `src/main/java/org/saturn/app/agent/tool/DatabaseSqlTool.java`
- Modify: `src/main/java/org/saturn/app/agent/AgentRuntimeFactory.java`
- Modify: `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java`
- Modify: `src/test/java/org/saturn/app/agent/tool/SaturnAgentToolsTest.java`
- Modify: `src/test/java/org/saturn/app/agent/DefaultAgentRouterTest.java`
- Modify: `README.md`

**Interfaces:**
- Consumes: schema repository, SQL policy, SQL repository, SQL config.
- Produces: admin-only `database_sql` fallback integrated with the existing router.

- [x] **Step 1: Write failing tool and router-flow tests**

Prove the tool requires `database_schema`, rejects missing SQL, returns policy error codes, emits
structured rows, and is absent from a regular caller's initial LLM request. Script an admin router
flow of `database_schema` then `database_sql` then final synthesis.

- [x] **Step 2: Run focused tests and confirm missing tool/wiring failures**

Run: `./mvnw -q -Dtest=SaturnAgentToolsTest,DefaultAgentRouterTest test`

Expected: compilation/test failures.

- [x] **Step 3: Implement and register the dynamic tools**

`DatabaseSqlTool` requires `Set.of("database_schema")`, is available only for
`DYNAMIC_SQL`, introspects the current allowed table set, validates SQL, executes it, and maps typed
errors to stable structured tool results. Register schema and SQL tools after the existing named
database tool.

Extend the system prompt with: prefer purpose-built tools; admins may inspect schema and use generated
read-only SQL only when no purpose-built tool can answer.

- [x] **Step 4: Update documentation and focused tests**

Document admin-only full-schema visibility, read-only behavior, configuration limits, and the fact
that raw SQL is not logged.

Run: `./mvnw -q -Dtest=SaturnAgentToolsTest,DefaultAgentRouterTest,LUserCommandImplTest test`

Expected: PASS.

---

### Task 7: Verification And Ledger Closeout

**Files:**
- Modify: `docs/superpowers/plans/2026-08-15-saturn-agent-dynamic-sql.md`

- [x] **Step 1: Format only files changed by this feature**

Use Google Java Format 1.24.0 on the dynamic SQL source/tests and touched agent files. Do not run a
repository-wide apply because unrelated files have pre-existing format debt.

- [x] **Step 2: Run all tests and package**

Run: `./mvnw -q test`

Run: `./mvnw -q package`

Expected: both exit 0 without contacting the real LLM endpoint.

- [x] **Step 3: Run static and SQL safety checks**

Run: `git diff --check`

Run: `./mvnw spotless:check`

Expected: changed files are clean; classify only unrelated pre-existing Spotless failures.

- [x] **Step 4: Review the final diff against the design**

Confirm admin-only visibility, schema-before-query enforcement, AST-only selects, true SQLite
read-only mode, internal-table rejection, all configured limits, no raw SQL logs, updated docs, and
no unrelated file changes.
