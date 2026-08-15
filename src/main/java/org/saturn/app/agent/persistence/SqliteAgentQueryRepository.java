package org.saturn.app.agent.persistence;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.saturn.app.agent.AgentContext;

public final class SqliteAgentQueryRepository implements AgentQueryRepository {
  private static final int DEFAULT_ROW_LIMIT = 10;
  private static final int MAX_ROW_LIMIT = 60;
  private final String jdbcUrl;

  public SqliteAgentQueryRepository(String databasePath) {
    this.jdbcUrl = "jdbc:sqlite:" + databasePath;
  }

  @Override
  public JsonObject execute(String queryName, JsonObject arguments, AgentContext context) {
    return switch (queryName) {
      case "message_count" ->
          count("SELECT COUNT(*) FROM messages WHERE visibility = 'PUBLIC'", "count");
      case "registered_user_count" -> count("SELECT COUNT(*) FROM trips", "count");
      case "recent_messages_for_requester" -> recentMessages(context, arguments);
      case "recent_messages_for_user" -> recentMessagesForUser(context, arguments);
      case "recent_messages_for_room" -> recentMessagesForRoom(context, arguments);
      case "known_nicks_for_trip" -> knownNicks(context, arguments);
      default -> throw new IllegalArgumentException("Unknown agent database query: " + queryName);
    };
  }

  private JsonObject count(String sql, String field) {
    try (Connection connection = openReadOnly();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet resultSet = statement.executeQuery()) {
      JsonObject result = new JsonObject();
      result.addProperty(field, resultSet.next() ? resultSet.getLong(1) : 0);
      return result;
    } catch (SQLException exception) {
      throw new AgentPersistenceException("Agent count query failed", exception);
    }
  }

  private JsonObject recentMessages(AgentContext context, JsonObject arguments) {
    if (context.trip() == null || context.trip().isBlank()) {
      return rows(new JsonArray());
    }
    int limit = rowLimit(arguments);
    String sql =
        """
        SELECT name, message, created_on, channel
        FROM messages
        WHERE trip = ? AND visibility = 'PUBLIC'
        ORDER BY created_on DESC, id DESC
        LIMIT ?
        """;
    try (Connection connection = openReadOnly();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, context.trip());
      statement.setInt(2, limit);
      try (ResultSet resultSet = statement.executeQuery()) {
        JsonArray result = new JsonArray();
        while (resultSet.next()) {
          JsonObject row = new JsonObject();
          row.addProperty("name", resultSet.getString("name"));
          row.addProperty("message", resultSet.getString("message"));
          row.addProperty("createdOn", resultSet.getLong("created_on"));
          row.addProperty("channel", resultSet.getString("channel"));
          result.add(row);
        }
        return rows(result);
      }
    } catch (SQLException exception) {
      throw new AgentPersistenceException("Agent recent-messages query failed", exception);
    }
  }

  private JsonObject knownNicks(AgentContext context, JsonObject arguments) {
    String trip = arguments.has("trip") ? arguments.get("trip").getAsString() : context.trip();
    if (trip == null || trip.isBlank()) {
      return rows(new JsonArray());
    }
    String sql =
        """
        SELECT DISTINCT n.name
        FROM trips t
        JOIN trip_names tn ON tn.trip_id = t.id
        JOIN names n ON n.id = tn.name_id
        WHERE t.trip = ?
        ORDER BY n.name
        LIMIT ?
        """;
    try (Connection connection = openReadOnly();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, trip);
      statement.setInt(2, rowLimit(arguments));
      try (ResultSet resultSet = statement.executeQuery()) {
        JsonArray result = new JsonArray();
        while (resultSet.next()) {
          JsonObject row = new JsonObject();
          row.addProperty("name", resultSet.getString(1));
          result.add(row);
        }
        return rows(result);
      }
    } catch (SQLException exception) {
      throw new AgentPersistenceException("Agent known-nicks query failed", exception);
    }
  }

  private JsonObject recentMessagesForUser(AgentContext context, JsonObject arguments) {
    if (!arguments.has("nick")
        || !arguments.get("nick").isJsonPrimitive()
        || !arguments.getAsJsonPrimitive("nick").isString()
        || arguments.get("nick").getAsString().isBlank()) {
      throw new IllegalArgumentException("nick is required");
    }
    boolean scopedToRoom = arguments.has("room");
    String sql =
        scopedToRoom
            ? """
              SELECT name, trip, hash, message, created_on, channel
              FROM messages
              WHERE name = ? COLLATE NOCASE
                AND channel = ? COLLATE NOCASE
                AND visibility = 'PUBLIC'
              ORDER BY created_on DESC, id DESC
              LIMIT ?
              """
            : """
              SELECT name, trip, hash, message, created_on, channel
              FROM messages
              WHERE name = ? COLLATE NOCASE AND visibility = 'PUBLIC'
              ORDER BY created_on DESC, id DESC
              LIMIT ?
              """;
    try (Connection connection = openReadOnly();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, arguments.get("nick").getAsString().trim());
      if (scopedToRoom) {
        statement.setString(2, room(arguments, context));
        statement.setInt(3, rowLimit(arguments));
      } else {
        statement.setInt(2, rowLimit(arguments));
      }
      try (ResultSet resultSet = statement.executeQuery()) {
        JsonArray result = new JsonArray();
        while (resultSet.next()) {
          result.add(messageRow(resultSet));
        }
        return rows(result);
      }
    } catch (SQLException exception) {
      throw new AgentPersistenceException("Agent user-message-history query failed", exception);
    }
  }

  private JsonObject recentMessagesForRoom(AgentContext context, JsonObject arguments) {
    String sql =
        """
        SELECT name, trip, hash, message, created_on, channel
        FROM messages
        WHERE channel = ? COLLATE NOCASE AND visibility = 'PUBLIC'
        ORDER BY created_on DESC, id DESC
        LIMIT ?
        """;
    try (Connection connection = openReadOnly();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, room(arguments, context));
      statement.setInt(2, rowLimit(arguments));
      try (ResultSet resultSet = statement.executeQuery()) {
        JsonArray result = new JsonArray();
        while (resultSet.next()) {
          result.add(messageRow(resultSet));
        }
        return rows(result);
      }
    } catch (SQLException exception) {
      throw new AgentPersistenceException("Agent room-message-history query failed", exception);
    }
  }

  private static String room(JsonObject arguments, AgentContext context) {
    if (!arguments.has("room")) {
      return context.room();
    }
    if (!arguments.get("room").isJsonPrimitive()
        || !arguments.getAsJsonPrimitive("room").isString()
        || arguments.get("room").getAsString().isBlank()) {
      throw new IllegalArgumentException("room must be a non-blank string");
    }
    return arguments.get("room").getAsString().trim();
  }

  private static JsonObject messageRow(ResultSet resultSet) throws SQLException {
    JsonObject row = new JsonObject();
    row.addProperty("name", resultSet.getString("name"));
    row.addProperty("trip", resultSet.getString("trip"));
    row.addProperty("hash", resultSet.getString("hash"));
    row.addProperty("message", resultSet.getString("message"));
    row.addProperty("createdOn", resultSet.getLong("created_on"));
    row.addProperty("channel", resultSet.getString("channel"));
    return row;
  }

  private Connection openReadOnly() throws SQLException {
    Connection connection = DriverManager.getConnection(jdbcUrl);
    try (PreparedStatement timeout = connection.prepareStatement("PRAGMA busy_timeout = 5000");
        PreparedStatement queryOnly = connection.prepareStatement("PRAGMA query_only = ON")) {
      timeout.execute();
      queryOnly.execute();
    }
    return connection;
  }

  private static int rowLimit(JsonObject arguments) {
    int requested = arguments.has("limit") ? arguments.get("limit").getAsInt() : DEFAULT_ROW_LIMIT;
    return Math.max(1, Math.min(requested, MAX_ROW_LIMIT));
  }

  private static JsonObject rows(JsonArray values) {
    JsonObject result = new JsonObject();
    result.add("rows", values);
    return result;
  }
}
