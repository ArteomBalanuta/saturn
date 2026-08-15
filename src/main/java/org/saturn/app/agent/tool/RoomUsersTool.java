package org.saturn.app.agent.tool;

import com.google.gson.JsonObject;
import org.saturn.app.agent.AgentContext;
import org.saturn.app.agent.AgentTool;
import org.saturn.app.agent.AgentToolResult;

public final class RoomUsersTool implements AgentTool {
  @Override
  public String name() {
    return "room_users";
  }

  @Override
  public String description() {
    return "Return the current Saturn room name and users currently visible in that room.";
  }

  @Override
  public AgentToolResult execute(AgentContext context, JsonObject arguments) {
    JsonObject result = new JsonObject();
    result.addProperty("room", context.room());
    result.add("users", new com.google.gson.Gson().toJsonTree(context.roomUsers()));
    result.addProperty("count", context.roomUsers().size());
    return AgentToolResult.success(name(), result);
  }
}
