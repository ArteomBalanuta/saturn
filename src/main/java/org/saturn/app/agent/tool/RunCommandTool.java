package org.saturn.app.agent.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.saturn.app.agent.AgentCapability;
import org.saturn.app.agent.AgentContext;
import org.saturn.app.agent.AgentPromptCatalog;
import org.saturn.app.agent.AgentTool;
import org.saturn.app.agent.AgentToolDescriptor;
import org.saturn.app.agent.AgentToolResult;
import org.saturn.app.agent.ToolAccess;
import org.saturn.app.agent.ToolEffect;
import org.saturn.app.agent.ToolExample;
import org.saturn.app.agent.ToolResultMode;

public final class RunCommandTool implements AgentTool {
  private static final Set<String> INFORMATIONAL_COMMANDS =
      Set.of(
          "help",
          "h",
          "list",
          "users",
          "info",
          "whois",
          "lastseen",
          "ping",
          "p",
          "weather",
          "w",
          "time",
          "t",
          "version",
          "v");
  private static final Set<String> MODERATION_COMMANDS =
      Set.of("captcha", "mute", "kick", "shadowban");
  private static final Set<String> PERMANENT_BAN_COMMAND = Set.of("ban");
  private static final AgentPromptCatalog PROMPTS = new AgentPromptCatalog();
  private final SaturnCommandGateway gateway;

  public RunCommandTool(SaturnCommandGateway gateway) {
    this.gateway = gateway;
  }

  @Override
  public String name() {
    return "run_command";
  }

  @Override
  public String description() {
    return PROMPTS.toolDescription(name());
  }

  @Override
  public JsonObject parameters() {
    return parametersFor(INFORMATIONAL_COMMANDS);
  }

  @Override
  public JsonObject parameters(AgentContext context) {
    return parametersFor(allowedCommands(context));
  }

  @Override
  public AgentToolDescriptor descriptor(AgentContext context) {
    boolean creator = context != null && context.hasCapability(AgentCapability.PERMANENT_BAN);
    boolean moderator = context != null && context.hasCapability(AgentCapability.MODERATION_COMMANDS);
    return new AgentToolDescriptor(
        name(),
        "Run Saturn command",
        description(),
        "commands",
        creator ? ToolAccess.CREATOR_ONLY : ToolAccess.AUTHORIZED_CALLER,
        moderator || creator ? ToolEffect.MODERATION : ToolEffect.ROOM_MESSAGE,
        ToolResultMode.ROOM_DELIVERY_AND_MODEL_DATA,
        parameters(context),
        PROMPTS.toolGuidance(name(), "whenToUse"),
        PROMPTS.toolGuidance(name(), "whenNotToUse"),
        List.of(new ToolExample(name(), "{\"command\":\"weather\",\"arguments\":\"Tokyo\"}", PROMPTS.toolExample(name()).substring(PROMPTS.toolExample(name()).indexOf(" - ") + 3))),
        Set.of(),
        Set.of());
  }

  private JsonObject parametersFor(Set<String> commands) {
    JsonObject command = new JsonObject();
    command.addProperty("type", "string");
    JsonArray allowed = new JsonArray();
    commands.stream().sorted().forEach(allowed::add);
    command.add("enum", allowed);
    JsonObject arguments = new JsonObject();
    arguments.addProperty("type", "string");
    JsonObject properties = new JsonObject();
    properties.add("command", command);
    properties.add("arguments", arguments);
    JsonArray required = new JsonArray();
    required.add("command");
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    schema.add("properties", properties);
    schema.add("required", required);
    schema.addProperty("additionalProperties", false);
    return schema;
  }

  @Override
  public AgentToolResult execute(AgentContext context, JsonObject arguments) {
    if (!arguments.has("command")) {
      return AgentToolResult.error(null, name(), "Missing command");
    }
    String command = arguments.get("command").getAsString().toLowerCase(Locale.ROOT);
    if (!allowedCommands(context).contains(command)) {
      return AgentToolResult.error(null, name(), "Command is not approved for agent execution");
    }
    String commandArguments =
        arguments.has("arguments") ? arguments.get("arguments").getAsString().trim() : "";
    boolean executed = gateway.execute(context, command, commandArguments);
    return executed
        ? AgentToolResult.success(
            name(),
            PROMPTS.formatted("command-executed-result.txt", command))
        : AgentToolResult.error(null, name(), "Command was not authorized or could not run");
  }

  private static Set<String> allowedCommands(AgentContext context) {
    Set<String> commands = new HashSet<>(INFORMATIONAL_COMMANDS);
    if (context.hasCapability(AgentCapability.MODERATION_COMMANDS)) {
      commands.addAll(MODERATION_COMMANDS);
    }
    if (context.hasCapability(AgentCapability.PERMANENT_BAN)) {
      commands.addAll(PERMANENT_BAN_COMMAND);
    }
    return Set.copyOf(commands);
  }
}
