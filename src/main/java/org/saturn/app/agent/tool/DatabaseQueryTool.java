package org.saturn.app.agent.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.saturn.app.agent.AgentContext;
import org.saturn.app.agent.AgentTool;
import org.saturn.app.agent.AgentToolResult;
import org.saturn.app.agent.persistence.AgentQueryRepository;

public final class DatabaseQueryTool implements AgentTool {
  private final AgentQueryRepository repository;

  public DatabaseQueryTool(AgentQueryRepository repository) {
    this.repository = repository;
  }

  @Override
  public String name() {
    return "database_query";
  }

  @Override
  public String description() {
    return "Execute one approved read-only Saturn database query. Arbitrary SQL is not accepted.";
  }

  @Override
  public JsonObject parameters() {
    JsonArray queryNames = new JsonArray();
    queryNames.add("message_count");
    queryNames.add("registered_user_count");
    queryNames.add("recent_messages_for_requester");
    queryNames.add("known_nicks_for_trip");
    JsonObject query = new JsonObject();
    query.addProperty("type", "string");
    query.add("enum", queryNames);
    JsonObject limit = new JsonObject();
    limit.addProperty("type", "integer");
    limit.addProperty("minimum", 1);
    limit.addProperty("maximum", 20);
    JsonObject trip = new JsonObject();
    trip.addProperty("type", "string");
    JsonObject properties = new JsonObject();
    properties.add("query", query);
    properties.add("limit", limit);
    properties.add("trip", trip);
    JsonArray required = new JsonArray();
    required.add("query");
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    schema.add("properties", properties);
    schema.add("required", required);
    schema.addProperty("additionalProperties", false);
    return schema;
  }

  @Override
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
