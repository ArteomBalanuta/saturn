package org.saturn.app.agent.tool;

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
import org.saturn.app.agent.ToolAccess;
import org.saturn.app.agent.ToolEffect;
import org.saturn.app.agent.ToolResultMode;
import org.saturn.app.agent.persistence.AgentSchemaRepository;

public final class DatabaseSchemaTool implements AgentTool {
  private final AgentSchemaRepository repository;
  private final AgentSqlConfig config;
  private static final AgentPromptCatalog PROMPTS = new AgentPromptCatalog();

  public DatabaseSchemaTool(AgentSchemaRepository repository, AgentSqlConfig config) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.config = Objects.requireNonNull(config, "config");
  }

  @Override
  public String name() {
    return "database_schema";
  }

  @Override
  public String description() {
    return PROMPTS.toolDescription(name());
  }

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

  @Override
  public boolean isAvailableTo(AgentContext context) {
    return config.enabled()
        && context != null
        && context.hasCapability(AgentCapability.DYNAMIC_SQL);
  }

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
