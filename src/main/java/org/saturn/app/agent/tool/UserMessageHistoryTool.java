package org.saturn.app.agent.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Objects;
import org.saturn.app.agent.AgentContext;
import org.saturn.app.agent.AgentTool;
import org.saturn.app.agent.AgentToolResult;
import org.saturn.app.agent.persistence.AgentQueryRepository;

public final class UserMessageHistoryTool implements AgentTool {
  private final AgentQueryRepository repository;

  public UserMessageHistoryTool(AgentQueryRepository repository) {
    this.repository = Objects.requireNonNull(repository, "repository");
  }

  @Override
  public String name() {
    return "user_message_history";
  }

  @Override
  public String description() {
    return "Fetch a named user's recent messages in the current room for chat-history summaries.";
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
    JsonObject properties = new JsonObject();
    properties.add("nick", nick);
    properties.add("limit", limit);
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
