package org.saturn.app.agent.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
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
  private static final int MAX_HISTORY_MESSAGES = 500;
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
    limit.addProperty("maximum", MAX_HISTORY_MESSAGES);
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
      JsonObject result =
          repository.execute("recent_messages_for_user", queryArguments, context);
      return AgentToolResult.success(
          name(), withEvidenceMetadata(result).toString());
    } catch (IllegalArgumentException exception) {
      return AgentToolResult.error(null, name(), "Invalid message-history request");
    } catch (RuntimeException exception) {
      return AgentToolResult.error(null, name(), "Message-history query failed");
    }
  }

  private static JsonObject withEvidenceMetadata(JsonObject result) {
    JsonObject enriched = result.deepCopy();
    JsonArray rows =
        result.has("rows") && result.get("rows").isJsonArray()
            ? result.getAsJsonArray("rows")
            : new JsonArray();
    Long oldest = null;
    Long newest = null;
    for (JsonElement element : rows) {
      if (!element.isJsonObject()) {
        continue;
      }
      JsonElement createdOn = element.getAsJsonObject().get("createdOn");
      if (createdOn == null
          || !createdOn.isJsonPrimitive()
          || !createdOn.getAsJsonPrimitive().isNumber()) {
        continue;
      }
      long timestamp = createdOn.getAsLong();
      oldest = oldest == null ? timestamp : Math.min(oldest, timestamp);
      newest = newest == null ? timestamp : Math.max(newest, timestamp);
    }

    enriched.addProperty("returnedCount", rows.size());
    enriched.add(
        "oldestCreatedOn", oldest == null ? JsonNull.INSTANCE : new JsonPrimitive(oldest));
    enriched.add(
        "newestCreatedOn", newest == null ? JsonNull.INSTANCE : new JsonPrimitive(newest));
    return enriched;
  }
}
