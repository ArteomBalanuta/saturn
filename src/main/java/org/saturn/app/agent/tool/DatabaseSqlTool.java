package org.saturn.app.agent.tool;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.saturn.app.agent.AgentCapability;
import org.saturn.app.agent.AgentContext;
import org.saturn.app.agent.AgentPromptCatalog;
import org.saturn.app.agent.AgentSqlConfig;
import org.saturn.app.agent.AgentTool;
import org.saturn.app.agent.AgentToolDescriptor;
import org.saturn.app.agent.AgentToolResult;
import org.saturn.app.agent.AgentToolSchemas;
import org.saturn.app.agent.ToolAccess;
import org.saturn.app.agent.ToolEffect;
import org.saturn.app.agent.ToolExample;
import org.saturn.app.agent.ToolResultMode;
import org.saturn.app.agent.persistence.AgentPersistenceException;
import org.saturn.app.agent.persistence.AgentSchemaRepository;
import org.saturn.app.agent.persistence.AgentSqlRepository;
import org.saturn.app.agent.sql.AgentSqlErrorCode;
import org.saturn.app.agent.sql.AgentSqlPolicy;
import org.saturn.app.agent.sql.AgentSqlPolicyException;

/**
 * Executes one bounded, validated read-only SQL statement for dynamic-SQL-capable callers.
 *
 * <p>The descriptor requires a successful {@code database_schema} observation first. Despite being
 * read-only, this dependency keeps execution sequential so SQL is grounded in the current schema.
 */
public final class DatabaseSqlTool implements AgentTool {
  private final AgentSchemaRepository schemaRepository;
  private final AgentSqlPolicy policy;
  private final AgentSqlRepository sqlRepository;
  private final AgentSqlConfig config;
  private final Gson gson = new Gson();
  private static final AgentPromptCatalog PROMPTS = new AgentPromptCatalog();

  public DatabaseSqlTool(
      AgentSchemaRepository schemaRepository,
      AgentSqlPolicy policy,
      AgentSqlRepository sqlRepository,
      AgentSqlConfig config) {
    this.schemaRepository = Objects.requireNonNull(schemaRepository, "schemaRepository");
    this.policy = Objects.requireNonNull(policy, "policy");
    this.sqlRepository = Objects.requireNonNull(sqlRepository, "sqlRepository");
    this.config = Objects.requireNonNull(config, "config");
  }

  @Override
  public String name() {
    return "database_sql";
  }

  @Override
  public String description() {
    return PROMPTS.toolDescription(name());
  }

  @Override
  public AgentToolDescriptor descriptor(AgentContext context) {
    return new AgentToolDescriptor(
        name(),
        "Run bounded read-only SQL",
        description(),
        "database",
        ToolAccess.AUTHORIZED_CALLER,
        ToolEffect.READ_ONLY,
        ToolResultMode.MODEL_DATA,
        parameters(context),
        PROMPTS.toolGuidance(name(), "whenToUse"),
        PROMPTS.toolGuidance(name(), "whenNotToUse"),
        List.of(
            new ToolExample(
                name(),
                "{\"sql\":\"SELECT COUNT(*) FROM messages\"}",
                PROMPTS
                    .toolExample(name())
                    .substring(PROMPTS.toolExample(name()).indexOf(" - ") + 3))),
        Set.of(AgentCapability.DYNAMIC_SQL.name()),
        requiredSuccessfulTools());
  }

  @Override
  public JsonObject parameters() {
    JsonObject sql = new JsonObject();
    sql.addProperty("type", "string");
    sql.addProperty("minLength", 1);
    sql.addProperty("maxLength", config.maxSqlChars());
    JsonObject properties = new JsonObject();
    properties.add("sql", sql);
    JsonArray required = new JsonArray();
    required.add("sql");
    JsonObject schema = AgentToolSchemas.closedObject();
    schema.add("properties", properties);
    schema.add("required", required);
    return schema;
  }

  @Override
  public boolean isAvailableTo(AgentContext context) {
    return config.enabled()
        && context != null
        && context.hasCapability(AgentCapability.DYNAMIC_SQL);
  }

  @Override
  public Set<String> requiredSuccessfulTools() {
    return Set.of("database_schema");
  }

  @Override
  /** Validates and executes the SQL against the current schema, returning a safe error payload. */
  public AgentToolResult execute(AgentContext context, JsonObject arguments) {
    if (!isAvailableTo(context)) {
      return AgentToolResult.error(null, name(), "Tool is unavailable for this caller");
    }
    JsonElement sqlElement = arguments.get("sql");
    if (sqlElement == null
        || !sqlElement.isJsonPrimitive()
        || !sqlElement.getAsJsonPrimitive().isString()) {
      return error(AgentSqlErrorCode.EMPTY_SQL);
    }
    String sql = sqlElement.getAsString();
    if (sql.isBlank()) {
      return error(AgentSqlErrorCode.EMPTY_SQL);
    }
    try {
      var schema = schemaRepository.describe();
      var validated = policy.validate(sql, schema);
      return AgentToolResult.success(name(), sqlRepository.execute(validated, config));
    } catch (AgentSqlPolicyException exception) {
      return error(exception.code());
    } catch (AgentPersistenceException exception) {
      return error(exception.code());
    } catch (RuntimeException exception) {
      return error(AgentSqlErrorCode.EXECUTION_FAILED);
    }
  }

  private AgentToolResult error(AgentSqlErrorCode code) {
    JsonObject payload = new JsonObject();
    payload.addProperty("code", code.name());
    payload.addProperty("message", safeMessage(code));
    return AgentToolResult.error(null, name(), gson.toJson(payload));
  }

  private String safeMessage(AgentSqlErrorCode code) {
    return switch (code) {
      case EMPTY_SQL -> "A non-blank SQL string is required";
      case SQL_TOO_LONG -> "SQL exceeds the configured limit";
      case MALFORMED_SQL -> "SQL could not be parsed";
      case FORBIDDEN_STATEMENT -> "Only one read-only SELECT is allowed";
      case FORBIDDEN_TABLE -> "SQL references a table outside the inspected schema";
      case FORBIDDEN_FUNCTION -> "SQL references a forbidden function";
      case TIMEOUT -> "SQL execution exceeded its deadline";
      case RESULT_TOO_LARGE -> "SQL output exceeded a configured limit";
      case EXECUTION_FAILED -> "SQL execution failed";
    };
  }
}
