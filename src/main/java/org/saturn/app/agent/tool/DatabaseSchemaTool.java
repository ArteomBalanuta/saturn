package org.saturn.app.agent.tool;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.saturn.app.agent.api.AgentCapability;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentTool;
import org.saturn.app.agent.api.AgentToolDescriptor;
import org.saturn.app.agent.api.AgentToolResult;
import org.saturn.app.agent.api.ToolAccess;
import org.saturn.app.agent.api.ToolEffect;
import org.saturn.app.agent.api.ToolResultMode;
import org.saturn.app.agent.config.AgentSqlConfig;
import org.saturn.app.agent.persistence.AgentSchemaRepository;
import org.saturn.app.agent.routing.AgentPromptCatalog;

/**
 * Exposes the current application schema to callers authorized for dynamic SQL.
 *
 * <p>A successful invocation is the ordered prerequisite for {@link DatabaseSqlTool} in the same
 * agent request.
 */
public final class DatabaseSchemaTool implements AgentTool {
  private final AgentSchemaRepository repository;
  private final AgentSqlConfig config;
  private static final AgentPromptCatalog PROMPTS = new AgentPromptCatalog();

  /**
   * Implements the {@code DatabaseSchemaTool} operation for this agent component.
   *
   * @param repository input argument used by this operation
   * @param config input argument used by this operation
   */
  public DatabaseSchemaTool(AgentSchemaRepository repository, AgentSqlConfig config) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.config = Objects.requireNonNull(config, "config");
  }

  /**
   * Implements the {@code name} operation for this agent component.
   *
   * @return the operation result
   */
  @Override
  public String name() {
    return "database_schema";
  }

  /**
   * Implements the {@code description} operation for this agent component.
   *
   * @return the operation result
   */
  @Override
  public String description() {
    return PROMPTS.toolDescription(name());
  }

  /**
   * Implements the {@code descriptor} operation for this agent component.
   *
   * @param context input argument used by this operation
   * @return the operation result
   */
  @Override
  public AgentToolDescriptor descriptor(AgentContext context) {
    return new AgentToolDescriptor(
        name(),
        "Inspect database schema",
        description(),
        "database",
        ToolAccess.AUTHORIZED_CALLER,
        ToolEffect.READ_ONLY,
        ToolResultMode.MODEL_DATA,
        parameters(context),
        PROMPTS.toolGuidance(name(), "whenToUse"),
        PROMPTS.toolGuidance(name(), "whenNotToUse"),
        List.of(),
        Set.of(AgentCapability.DYNAMIC_SQL.name()),
        Set.of());
  }

  /**
   * Implements the {@code isAvailableTo} operation for this agent component.
   *
   * @param context input argument used by this operation
   * @return the operation result
   */
  @Override
  public boolean isAvailableTo(AgentContext context) {
    return config.enabled()
        && context != null
        && context.hasCapability(AgentCapability.DYNAMIC_SQL);
  }

  /** Returns a schema snapshot for an authorized caller, without issuing generated SQL. */
  @Override
  public AgentToolResult execute(AgentContext context, JsonObject arguments) {
    if (!isAvailableTo(context)) {
      return AgentToolResult.error(null, name(), "Tool is unavailable for this caller");
    }
    try {
      return AgentToolResult.success(name(), repository.describe());
    } catch (RuntimeException exception) {
      return AgentToolResult.error(null, name(), "Database schema inspection failed");
    }
  }
}
