package org.saturn.app.agent.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.saturn.app.agent.AgentContext;
import org.saturn.app.agent.AgentPromptCatalog;
import org.saturn.app.agent.AgentTool;
import org.saturn.app.agent.AgentToolDescriptor;
import org.saturn.app.agent.AgentToolResult;
import org.saturn.app.agent.ToolAccess;
import org.saturn.app.agent.ToolEffect;
import org.saturn.app.agent.ToolExample;
import org.saturn.app.agent.ToolResultMode;
import org.saturn.app.agent.persistence.AgentQueryRepository;

public final class UserMessageHistoryTool implements AgentTool {
  private final AgentQueryRepository repository;
  private static final AgentPromptCatalog PROMPTS = new AgentPromptCatalog();

  public UserMessageHistoryTool(AgentQueryRepository repository) {
    this.repository = Objects.requireNonNull(repository, "repository");
  }

  @Override
  public String name() {
    return "user_message_history";
  }

  @Override
  public String description() {
    return PROMPTS.toolDescription(name());
  }

  @Override
  public AgentToolDescriptor descriptor(AgentContext context) {
    return new AgentToolDescriptor(
        name(),
        "Search user message history",
        description(),
        "messages",
        ToolAccess.PUBLIC,
        ToolEffect.READ_ONLY,
        ToolResultMode.MODEL_DATA,
        parameters(context),
        PROMPTS.toolGuidance(name(), "whenToUse"),
        PROMPTS.toolGuidance(name(), "whenNotToUse"),
        List.of(new ToolExample(name(), "{\"nick\":\"sun\"}", PROMPTS.toolExample(name()).substring(PROMPTS.toolExample(name()).indexOf(" - ") + 3))),
        Set.of(),
        Set.of());
  }

  @Override
  public JsonObject parameters() {
    JsonObject nick = new JsonObject();
    nick.addProperty("type", "string");
    nick.addProperty("minLength", 1);
    nick.addProperty("maxLength", 100);
    JsonObject limit = new JsonObject();
    limit.addProperty("type", "integer");
    limit.addProperty("minimum", 1);
    limit.addProperty("maximum", 20);
    JsonObject room = new JsonObject();
    room.addProperty("type", "string");
    room.addProperty("minLength", 1);
    room.addProperty("maxLength", 100);
    room.addProperty("description", "Optional room restriction; omit to search all rooms");
    JsonObject properties = new JsonObject();
    properties.add("nick", nick);
    properties.add("limit", limit);
    properties.add("room", room);
    JsonArray required = new JsonArray();
    required.add("nick");
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    schema.add("properties", properties);
    schema.add("required", required);
    schema.addProperty("additionalProperties", false);
    return schema;
  }

  @Override
  public AgentToolResult execute(AgentContext context, JsonObject arguments) {
    JsonElement nick = arguments.get("nick");
    if (nick == null
        || !nick.isJsonPrimitive()
        || !nick.getAsJsonPrimitive().isString()
        || nick.getAsString().isBlank()) {
      return AgentToolResult.error(null, name(), "A non-blank nick is required");
    }
    JsonObject queryArguments = arguments.deepCopy();
    queryArguments.addProperty("nick", nick.getAsString().trim());
    if (queryArguments.has("room")) {
      JsonElement room = queryArguments.get("room");
      if (!room.isJsonPrimitive()
          || !room.getAsJsonPrimitive().isString()
          || room.getAsString().isBlank()) {
        return AgentToolResult.error(null, name(), "A non-blank room is required");
      }
      queryArguments.addProperty("room", room.getAsString().trim());
    }
    try {
      return AgentToolResult.success(
          name(), repository.execute("recent_messages_for_user", queryArguments, context));
    } catch (IllegalArgumentException exception) {
      return AgentToolResult.error(null, name(), "Invalid message-history request");
    } catch (RuntimeException exception) {
      return AgentToolResult.error(null, name(), "Message-history query failed");
    }
  }
}
