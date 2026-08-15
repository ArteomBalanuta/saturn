package org.saturn.app.agent.tool;

import com.google.gson.JsonObject;
import java.util.Objects;
import org.saturn.app.agent.AgentContext;
import org.saturn.app.agent.AgentTool;
import org.saturn.app.agent.AgentToolResult;

public final class RoomUsersTool implements AgentTool {
  private final AgentRoomDirectory roomDirectory;

  public RoomUsersTool(AgentRoomDirectory roomDirectory) {
    this.roomDirectory = Objects.requireNonNull(roomDirectory, "roomDirectory");
  }

  @Override
  public String name() {
    return "room_users";
  }

  @Override
  public String description() {
    return "Return live users in a Saturn-managed room. Pass room whenever the user names a"
        + " channel; omit it only for the caller's current room.";
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
