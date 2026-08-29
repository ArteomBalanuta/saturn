# H2 Persistence Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move Saturn's active embedded persistence from SQLite to H2 file mode while automatically preserving data from the legacy SQLite file.

**Architecture:** `dbPath` becomes the H2 database stem and H2 creates `<stem>.mv.db`. At startup, a bootstrapper creates an empty H2 schema or migrates a sibling legacy `<stem>.db` SQLite database transactionally, validates per-table row counts, then archives the legacy source. SQLite JDBC remains packaged for this migration release only; normal repositories use H2 connections exclusively.

**Tech Stack:** Java 23, JDBC, H2 embedded file mode, SQLite JDBC transition reader, Maven, JUnit 5.

## Global Constraints

- H2 URL is `jdbc:h2:file:<absolute-stem>;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1`; H2 does not provide a SQLite compatibility mode, so SQLite dialect constructs are translated explicitly.
- A legacy SQLite database is never deleted before the H2 copy is committed and verified.
- Existing public database APIs retain their signatures where possible.
- The build, Docker, Makefile, examples, and README describe the H2 `.mv.db` file and legacy migration behavior.

---

### Task 1: Introduce H2 connection and bootstrap boundaries

**Files:**
- Create: `src/main/java/org/saturn/app/persistence/H2Database.java`
- Create: `src/main/java/org/saturn/app/persistence/H2SchemaBootstrapper.java`
- Modify: `src/main/java/org/saturn/app/service/impl/DataBaseServiceImpl.java`
- Test: `src/test/java/org/saturn/app/persistence/H2DatabaseTest.java`

- [ ] Add failing tests for deterministic H2 URL construction and fresh schema creation.
- [ ] Verify the tests fail because the H2 boundary does not exist.
- [ ] Implement the H2 URL and schema bootstrapper.
- [ ] Run the focused test class.

### Task 2: Migrate legacy SQLite data safely

**Files:**
- Create: `src/main/java/org/saturn/app/persistence/SqliteToH2Migrator.java`
- Test: `src/test/java/org/saturn/app/persistence/SqliteToH2MigratorTest.java`

- [ ] Add a failing integration test that creates a SQLite fixture, migrates it, and asserts rows, indexes, and legacy archival.
- [ ] Verify the test fails before the migrator exists.
- [ ] Implement transactional schema translation, data copying, verification, and archival.
- [ ] Run the focused test class.

### Task 3: Move agent persistence to H2 JDBC behavior

**Files:**
- Modify: `src/main/java/org/saturn/app/agent/persistence/*`
- Modify: `src/main/java/org/saturn/app/agent/AgentRuntimeFactory.java`
- Modify: `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java`
- Test: `src/test/java/org/saturn/app/agent/persistence/*Test.java`

- [ ] Replace SQLite connection URLs, pragmas, driver-specific limits, and metadata reads.
- [ ] Use standard JDBC query timeouts and `DatabaseMetaData` schema discovery.
- [ ] Update focused tests to run against H2 files.

### Task 4: Convert SQL and operational workflow

**Files:**
- Modify: `schema.sql`, `database/migrations/*.sql`, `deploy/create_db.sh`, `Dockerfile`, `Makefile`, `config.example.toml`, `README.md`
- Modify: SQLite-specific query constants and schema checks.
- Test: persistence and application bootstrap tests.

- [ ] Convert SQLite-only DDL and functions to H2-compatible SQL.
- [ ] Replace SQLite command-line maintenance with application/H2-safe lifecycle commands.
- [ ] Document database stem, `.mv.db`, one-time migration, backup, and verification.
- [ ] Run formatting, full tests, package, and a Docker configuration check.
