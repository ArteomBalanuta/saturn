package org.saturn.app.agent.tool;

import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.Set;
import org.saturn.app.agent.api.AgentCapability;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentTool;
import org.saturn.app.agent.api.AgentToolDescriptor;
import org.saturn.app.agent.api.AgentToolResult;
import org.saturn.app.agent.api.ToolAccess;
import org.saturn.app.agent.api.ToolResultMode;

/**
 * Validated contextual adapter for one {@link SaturnCommandToolCatalog.CommandToolDefinition}.
 *
 * <p>The adapter never reimplements command authorization or delivery; it renders validated
 * structured input and delegates to Saturn's command gateway.
 */
public final class SaturnCommandTool implements AgentTool {
  private final SaturnCommandToolCatalog.CommandToolDefinition definition;
  private final SaturnCommandGateway gateway;

  public SaturnCommandTool(
      SaturnCommandToolCatalog.CommandToolDefinition definition, SaturnCommandGateway gateway) {
    this.definition = Objects.requireNonNull(definition, "definition");
    this.gateway = Objects.requireNonNull(gateway, "gateway");
  }

  @Override
  public String name() {
    return definition.toolName();
  }

  @Override
  public String description() {
    return definition.description();
  }

  @Override
  public JsonObject parameters(AgentContext context) {
    return definition.parameters().deepCopy();
  }

  @Override
  public boolean isAvailableTo(AgentContext context) {
    return context != null && context.capabilities().containsAll(definition.requiredCapabilities());
  }

  @Override
  public AgentToolDescriptor descriptor(AgentContext context) {
    return new AgentToolDescriptor(
        name(),
        "Run Saturn %s command".formatted(definition.commandAlias()),
        description(),
        "commands",
        access(),
        definition.effect(),
        ToolResultMode.ROOM_DELIVERY_AND_MODEL_DATA,
        parameters(context),
        definition.whenToUse(),
        definition.whenNotToUse(),
        definition.examples(),
        definition.requiredCapabilities().stream()
            .map(Enum::name)
            .collect(java.util.stream.Collectors.toSet()),
        Set.of(),
        definition.isIdempotent(),
        definition.timeout(),
        anyResultSchema());
  }

  @Override
  public AgentToolResult execute(AgentContext context, JsonObject arguments) {
    if (!isAvailableTo(context)) {
      return AgentToolResult.error(
          null,
          name(),
          "TOOL_NOT_AUTHORIZED",
          "Caller is not allowed to execute this Saturn command");
    }
    SaturnCommandGateway.CommandExecution execution =
        gateway.executeWithResult(
            context, definition.commandAlias(), definition.renderArguments(arguments));
    return execution.executed()
        ? AgentToolResult.success(name(), execution.modelData())
        : AgentToolResult.error(
            null, name(), "COMMAND_REJECTED", "Saturn rejected the command invocation");
  }

  private ToolAccess access() {
    if (definition.requiredCapabilities().contains(AgentCapability.ADMIN_COMMANDS)) {
      return ToolAccess.CREATOR_ONLY;
    }
    return definition.requiredCapabilities().isEmpty()
        ? ToolAccess.PUBLIC
        : ToolAccess.AUTHORIZED_CALLER;
  }

  private JsonObject anyResultSchema() {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "any");
    return schema;
  }
}
