package org.saturn.app.agent.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Set;
import org.saturn.app.agent.AgentContext;
import org.saturn.app.agent.AgentPromptCatalog;
import org.saturn.app.agent.AgentTool;
import org.saturn.app.agent.AgentToolDescriptor;
import org.saturn.app.agent.AgentToolResult;
import org.saturn.app.agent.AgentToolSchemas;
import org.saturn.app.agent.ToolAccess;
import org.saturn.app.agent.ToolEffect;
import org.saturn.app.agent.ToolExample;
import org.saturn.app.agent.ToolResultMode;
import org.saturn.app.agent.persistence.AgentQueryRepository;

/**
 * Runs one of Saturn's named read-only database queries.
 *
 * <p>It does not accept generated SQL; use {@link DatabaseSqlTool} only where the separate
 * admin-only dynamic-SQL contract is available.
 */
public final class DatabaseQueryTool implements AgentTool {
  private final AgentQueryRepository repository;
  private static final AgentPromptCatalog PROMPTS = new AgentPromptCatalog();

  public DatabaseQueryTool(AgentQueryRepository repository) {
    this.repository = repository;
  }

  @Override
  public String name() {
    return "database_query";
  }

  @Override
  public String description() {
    return PROMPTS.toolDescription(name());
  }

  @Override
  public AgentToolDescriptor descriptor(AgentContext context) {
    return new AgentToolDescriptor(
        name(),
        "Approved database query",
        description(),
        "database",
        ToolAccess.PUBLIC,
        ToolEffect.READ_ONLY,
        ToolResultMode.MODEL_DATA,
        parameters(context),
        PROMPTS.toolGuidance(name(), "whenToUse"),
        PROMPTS.toolGuidance(name(), "whenNotToUse"),
        List.of(
            new ToolExample(
                name(),
                "{\"query\":\"recent_messages_for_room\"}",
                PROMPTS
                    .toolExample(name())
                    .substring(PROMPTS.toolExample(name()).indexOf(" - ") + 3))),
        Set.of(),
        Set.of());
  }

  @Override
  public JsonObject parameters() {
    JsonArray queryNames = new JsonArray();
    queryNames.add("message_count");
    queryNames.add("registered_user_count");
    queryNames.add("recent_messages_for_requester");
    queryNames.add("recent_messages_for_room");
    queryNames.add("known_nicks_for_trip");
    JsonObject query = new JsonObject();
    query.addProperty("type", "string");
    query.add("enum", queryNames);
    JsonObject limit = new JsonObject();
    limit.addProperty("type", "integer");
    limit.addProperty("minimum", 1);
    limit.addProperty("maximum", 60);
    JsonObject trip = new JsonObject();
    trip.addProperty("type", "string");
    JsonObject room = new JsonObject();
    room.addProperty("type", "string");
    room.addProperty("minLength", 1);
    room.addProperty("maxLength", 100);
    JsonObject properties = new JsonObject();
    properties.add("query", query);
    properties.add("limit", limit);
    properties.add("trip", trip);
    properties.add("room", room);
    JsonArray required = new JsonArray();
    required.add("query");
    JsonObject schema = AgentToolSchemas.closedObject();
    schema.add("properties", properties);
    schema.add("required", required);
    return schema;
  }

  @Override
  /** Executes an allow-listed query and converts repository failures into tool errors. */
  public AgentToolResult execute(AgentContext context, JsonObject arguments) {
    if (!arguments.has("query")) {
      return AgentToolResult.error(null, name(), "Missing required query name");
    }
    String queryName = arguments.get("query").getAsString();
    JsonObject queryArguments = arguments.deepCopy();
    queryArguments.remove("query");
    try {
      return AgentToolResult.success(
          name(), repository.execute(queryName, queryArguments, context));
    } catch (IllegalArgumentException exception) {
      return AgentToolResult.error(null, name(), "Query is not approved");
    } catch (RuntimeException exception) {
      return AgentToolResult.error(null, name(), "Database query failed");
    }
  }
}
