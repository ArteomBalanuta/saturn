package org.saturn.app.agent.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.Set;
import org.saturn.app.agent.AgentContext;
import org.saturn.app.agent.AgentTool;
import org.saturn.app.agent.AgentToolResult;

public final class RunCommandTool implements AgentTool {
  private static final Set<String> ALLOWED_COMMANDS =
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
    return "Execute an approved non-destructive Saturn command as the requesting user.";
  }

  @Override
  public JsonObject parameters() {
    JsonObject command = new JsonObject();
    command.addProperty("type", "string");
    JsonArray allowed = new JsonArray();
    ALLOWED_COMMANDS.stream().sorted().forEach(allowed::add);
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
    if (!ALLOWED_COMMANDS.contains(command)) {
      return AgentToolResult.error(null, name(), "Command is not approved for agent execution");
    }
    String commandArguments =
        arguments.has("arguments") ? arguments.get("arguments").getAsString().trim() : "";
    boolean executed = gateway.execute(context, command, commandArguments);
    return executed
        ? AgentToolResult.success(name(), "Command executed; its output was sent to the room.")
        : AgentToolResult.error(null, name(), "Command was not authorized or could not run");
  }
}
