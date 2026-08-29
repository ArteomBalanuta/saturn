# Tool-contract migration slice QA

Date: 2026-08-19
Branch: `develop`
Scope: exactly four direct declarations moved from `org.saturn.app.agent` to `org.saturn.app.agent.tool.contract`.

## Moved declarations

- `AgentToolDefinitionJson`
- `AgentToolSchemas`
- `AgentToolSchemaValidator`
- `AgentToolDefinitionFactory`

The four package-private contract tests moved with their corresponding declarations. No execution, turn, room, or routing declaration was moved.

## Dependency-boundary changes

- Updated all production callers to explicit `org.saturn.app.agent.tool.contract.*` imports.
- Updated `agent.api.AgentTool` and `agent.api.AgentToolDescriptor` to import `AgentToolSchemas` from the contract package.
- Removed wildcard API imports from touched files.
- Preserved JSON generation, schema validation, and descriptor behavior.
- `AgentToolDefinitionJson` and `AgentToolSchemaValidator` became public, with only the cross-package entry points public, because compilation proved the existing root-package callers require access after the move. Internal helpers remain private.
- `AgentToolDescriptorTest` now exercises the public `AgentToolSchemas` validation boundary rather than directly coupling the API-package test to the package-private validator implementation.

## Exact verification commands and results

All commands ran from `/Users/ab/workspace/projects/saturn`.

1. Focused contract/schema/tool-definition/API tests:

```text
mvn -q -Dtest=org.saturn.app.agent.tool.contract.AgentToolDefinitionJsonTest,org.saturn.app.agent.tool.contract.AgentToolSchemasTest,org.saturn.app.agent.tool.contract.AgentToolSchemaValidatorTest,org.saturn.app.agent.tool.contract.AgentToolDefinitionFactoryTest,org.saturn.app.agent.AgentToolTest,org.saturn.app.agent.AgentToolDescriptorTest,org.saturn.app.agent.AgentToolRegistryTest test
```

Result: PASS (exit 0).

2. Clean production compile:

```text
mvn -q clean compile
```

Result: PASS (exit 0).

3. Spotless:

```text
mvn -q spotless:apply
mvn -q spotless:check
```

Result: PASS (exit 0).

4. Full test suite:

```text
mvn -q test
```

Result: PASS (exit 0); 600 tests, 0 failures, 0 errors, 5 skipped.

5. Diff validation:

```text
git diff --check
```

Result: PASS (no whitespace errors).

6. Structural checks:

- Root declaration files for all four names: absent.
- Old four-type imports: none.
- Wildcard imports in relevant touched files: none.

## Touched paths

### Production declarations

- `src/main/java/org/saturn/app/agent/tool/contract/AgentToolDefinitionFactory.java`
- `src/main/java/org/saturn/app/agent/tool/contract/AgentToolDefinitionJson.java`
- `src/main/java/org/saturn/app/agent/tool/contract/AgentToolSchemaValidator.java`
- `src/main/java/org/saturn/app/agent/tool/contract/AgentToolSchemas.java`

### Production callers/import boundaries

- `src/main/java/org/saturn/app/agent/AgentCommandChannelPolicy.java`
- `src/main/java/org/saturn/app/agent/AgentCommandIntentPolicy.java`
- `src/main/java/org/saturn/app/agent/AgentCommandProseGuard.java`
- `src/main/java/org/saturn/app/agent/AgentRequestAssembler.java`
- `src/main/java/org/saturn/app/agent/AgentToolCallValidator.java`
- `src/main/java/org/saturn/app/agent/AgentToolExecutor.java`
- `src/main/java/org/saturn/app/agent/AgentToolRegistry.java`
- `src/main/java/org/saturn/app/agent/DefaultAgentRouter.java`
- `src/main/java/org/saturn/app/agent/api/AgentTool.java`
- `src/main/java/org/saturn/app/agent/api/AgentToolDescriptor.java`
- `src/main/java/org/saturn/app/agent/tool/DatabaseQueryTool.java`
- `src/main/java/org/saturn/app/agent/tool/DatabaseSqlTool.java`
- `src/main/java/org/saturn/app/agent/tool/RoomUsersTool.java`
- `src/main/java/org/saturn/app/agent/tool/RunCommandTool.java`
- `src/main/java/org/saturn/app/agent/tool/UserMessageHistoryTool.java`

### Tests

- `src/test/java/org/saturn/app/agent/tool/contract/AgentToolDefinitionFactoryTest.java`
- `src/test/java/org/saturn/app/agent/tool/contract/AgentToolDefinitionJsonTest.java`
- `src/test/java/org/saturn/app/agent/tool/contract/AgentToolSchemaValidatorTest.java`
- `src/test/java/org/saturn/app/agent/tool/contract/AgentToolSchemasTest.java`
- `src/test/java/org/saturn/app/agent/AgentToolDescriptorTest.java`
- `src/test/java/org/saturn/app/agent/AgentModelVisibleToolResultRendererTest.java`
- `src/test/java/org/saturn/app/agent/AgentToolRegistryTest.java`

No commit or push was performed. Existing unrelated worktree artifacts and prior migration changes were preserved.
