package org.saturn.app.agent.tool;

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

public final class RoomUsersTool implements AgentTool {
  private final AgentRoomDirectory roomDirectory;
  private static final AgentPromptCatalog PROMPTS = new AgentPromptCatalog();

  public RoomUsersTool(AgentRoomDirectory roomDirectory) {
    this.roomDirectory = Objects.requireNonNull(roomDirectory, "roomDirectory");
  }

  @Override
  public String name() {
    return "room_users";
  }

  @Override
  public String description() {
    return PROMPTS.toolDescription(name());
  }

  @Override
  public AgentToolDescriptor descriptor(AgentContext context) {
    return new AgentToolDescriptor(
        name(),
        "List room users",
        description(),
        "room",
        ToolAccess.PUBLIC,
        ToolEffect.READ_ONLY,
        ToolResultMode.MODEL_DATA,
        parameters(context),
        PROMPTS.toolGuidance(name(), "whenToUse"),
        PROMPTS.toolGuidance(name(), "whenNotToUse"),
        List.of(new ToolExample(name(), "{\"room\":\"lounge\"}", PROMPTS.toolExample(name()).substring(PROMPTS.toolExample(name()).indexOf(" - ") + 3))),
        Set.of(),
        Set.of());
  }

  @Override
  public JsonObject parameters() {
    JsonObject room = new JsonObject();
    room.addProperty("type", "string");
    room.addProperty("minLength", 1);
    room.addProperty("maxLength", 100);
    room.addProperty("description", "Room/channel to inspect, such as lounge");
    JsonObject properties = new JsonObject();
    properties.add("room", room);
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    schema.add("properties", properties);
    schema.addProperty("additionalProperties", false);
    return schema;
  }

  @Override
  public AgentToolResult execute(AgentContext context, JsonObject arguments) {
    String requestedRoom = context.room();
    if (arguments.has("room")) {
      if (!arguments.get("room").isJsonPrimitive()
          || !arguments.getAsJsonPrimitive("room").isString()
          || arguments.get("room").getAsString().isBlank()) {
        return AgentToolResult.error(null, name(), "A non-blank room is required");
      }
      requestedRoom = arguments.get("room").getAsString().trim();
    }
    AgentRoomDirectory.RoomSnapshot snapshot = roomDirectory.find(requestedRoom).orElse(null);
    if (snapshot == null) {
      return AgentToolResult.error(
          null, name(), "Saturn does not currently manage room: " + requestedRoom);
    }
    JsonObject result = new JsonObject();
    result.addProperty("room", snapshot.room());
    result.add("users", new com.google.gson.Gson().toJsonTree(snapshot.users()));
    result.addProperty("count", snapshot.users().size());
    return AgentToolResult.success(name(), result);
  }
}
