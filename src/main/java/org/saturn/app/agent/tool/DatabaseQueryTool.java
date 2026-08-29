package org.saturn.app.agent.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Set;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentTool;
import org.saturn.app.agent.api.AgentToolDescriptor;
import org.saturn.app.agent.api.AgentToolResult;
import org.saturn.app.agent.api.ToolAccess;
import org.saturn.app.agent.api.ToolEffect;
import org.saturn.app.agent.api.ToolExample;
import org.saturn.app.agent.api.ToolResultMode;
import org.saturn.app.agent.persistence.AgentQueryRepository;
import org.saturn.app.agent.routing.AgentPromptCatalog;
import org.saturn.app.agent.tool.contract.AgentToolSchemas;

/**
 * Runs one of Saturn's named read-only database queries.
 *
 * <p>It does not accept generated SQL; use {@link DatabaseSqlTool} only where the separate
 * admin-only dynamic-SQL contract is available.
 */
public final class DatabaseQueryTool implements AgentTool {
  private final AgentQueryRepository repository;
  private static final AgentPromptCatalog PROMPTS = new AgentPromptCatalog();

  /**
   * Implements the {@code DatabaseQueryTool} operation for this agent component.
   *
   * @param repository input argument used by this operation
   */
  public DatabaseQueryTool(AgentQueryRepository repository) {
    this.repository = repository;
  }

  /**
   * Implements the {@code name} operation for this agent component.
   *
   * @return the operation result
   */
  @Override
  public String name() {
    return "database_query";
  }

  /**
   * Implements the {@code description} operation for this agent component.
   *
   * @return the operation result
   */
  @Override
  public String description() {
    return PROMPTS.toolDescription(name());
  }

  /**
   * Implements the {@code descriptor} operation for this agent component.
   *
   * @param context input argument used by this operation
   * @return the operation result
   */
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

  /**
   * Implements the {@code parameters} operation for this agent component.
   *
   * @return the operation result
   */
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

  /** Executes an allow-listed query and converts repository failures into tool errors. */
  @Override
  public AgentToolResult execute(AgentContext context, JsonObject arguments) {
    if (arguments == null || !arguments.has("query")) {
      return AgentToolResult.error(null, name(), "Missing required query name");
    }
    if (!arguments.get("query").isJsonPrimitive()
        || !arguments.getAsJsonPrimitive("query").isString()) {
      return AgentToolResult.error(null, name(), "Query is not approved");
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
