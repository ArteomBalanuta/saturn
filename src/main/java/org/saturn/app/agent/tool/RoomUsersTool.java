package org.saturn.app.agent.tool;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentTool;
import org.saturn.app.agent.api.AgentToolDescriptor;
import org.saturn.app.agent.api.AgentToolResult;
import org.saturn.app.agent.api.ToolAccess;
import org.saturn.app.agent.api.ToolEffect;
import org.saturn.app.agent.api.ToolExample;
import org.saturn.app.agent.api.ToolResultMode;
import org.saturn.app.agent.routing.AgentPromptCatalog;
import org.saturn.app.agent.tool.contract.AgentToolSchemas;

/**
 * Returns a live snapshot of users in one Saturn-managed room.
 *
 * <p>The tool is read-only and idempotent, so independent calls are eligible for executor fan-out.
 */
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
        List.of(
            new ToolExample(
                name(),
                "{\"room\":\"lounge\"}",
                PROMPTS
                    .toolExample(name())
                    .substring(PROMPTS.toolExample(name()).indexOf(" - ") + 3))),
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
    JsonObject schema = AgentToolSchemas.closedObject();
    schema.add("properties", properties);
    return schema;
  }

  @Override
  /** Returns the selected room's name, user list, and count, or a coded lookup error. */
  public AgentToolResult execute(AgentContext context, JsonObject arguments) {
    String requestedRoom = context.room();
    if (arguments.has("room")) {
      Optional<String> room = AgentToolArgumentReader.nonBlankString(arguments, "room");
      if (room.isEmpty()) {
        return AgentToolResult.error(null, name(), "A non-blank room is required");
      }
      requestedRoom = room.orElseThrow();
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
