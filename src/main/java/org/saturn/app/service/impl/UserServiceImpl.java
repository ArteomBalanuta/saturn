package org.saturn.app.service.impl;

import static org.apache.commons.text.StringEscapeUtils.escapeJson;
import static org.saturn.app.util.DateUtil.formatRfc1123;
import static org.saturn.app.util.DateUtil.getDifference;
import static org.saturn.app.util.SqlUtil.DELETE_NAME;
import static org.saturn.app.util.SqlUtil.DELETE_TRIP;
import static org.saturn.app.util.SqlUtil.DELETE_TRIP_NAMES;
import static org.saturn.app.util.SqlUtil.INSERT_NAMES;
import static org.saturn.app.util.SqlUtil.INSERT_TRIPS;
import static org.saturn.app.util.SqlUtil.INSERT_TRIP_NAME;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.model.dto.LastSeenDto;
import org.saturn.app.model.dto.Message;
import org.saturn.app.model.dto.User;
import org.saturn.app.service.UserService;
import org.saturn.app.util.DateUtil;
import org.saturn.app.util.SqlUtil;

@Slf4j
public class UserServiceImpl extends OutService implements UserService {
  private final Connection connection;

  public UserServiceImpl(Connection connection, BlockingQueue<String> queue) {
    super(queue);
    this.connection = connection;
  }

  @Override
  public Optional<String> isSeenRecently(User user) {
    List<String> names = new ArrayList<>();
    try {
      PreparedStatement statement = connection.prepareStatement(SqlUtil.SELECT_SEEN_RECENTLY_AS);
      statement.setString(1, user.getHash());
      statement.setString(2, user.getTrip());
      statement.execute();

      ResultSet resultSet = statement.getResultSet();
      while (resultSet.next()) {
        names.add(resultSet.getString("name"));
      }

      statement.close();
      resultSet.close();
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace: ", e);
    }

    if (names.isEmpty()
        || (names.size() == 1 && names.getFirst().equalsIgnoreCase(user.getNick()))) {
      return Optional.empty();
    } else {
      String aliases =
          names.stream()
              .filter(n -> !n.equalsIgnoreCase(user.getNick()))
              .toList()
              .toString()
              .replace("[", "")
              .replace("]", "");
      return Optional.of(
          "\\n @%s, has been seen as: _%s_ recently. \\n".formatted(user.getNick(), aliases));
    }
  }

  @Override
  public String lastOnline(String tripOrNick) {
    LastSeenDto dto = new LastSeenDto();
    dto.setTripOrNick(tripOrNick);

    String lastMessage = null;
    String timestamp = null;
    try {
      PreparedStatement statement = connection.prepareStatement(SqlUtil.SELECT_LAST_SEEN);
      statement.setString(1, tripOrNick);
      statement.setString(2, tripOrNick);
      statement.execute();

      ResultSet resultSet = statement.getResultSet();
      while (resultSet.next()) {
        timestamp = resultSet.getString("created_on");
        lastMessage = resultSet.getString("message");
      }

      statement.close();
      resultSet.close();
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace: ", e);
    }

    if (timestamp != null) {
      dto.setLastSeenRfc1123(
          formatRfc1123(Long.parseLong(timestamp), TimeUnit.MILLISECONDS, "UTC"));
    }

    if (lastMessage != null) {
      dto.setLastMessage(escapeJson(lastMessage));
    }

    if (timestamp != null) {
      dto.setTimeSinceSeen(
          getDifference(
              ZonedDateTime.now(ZoneId.of("UTC")),
              ZonedDateTime.ofInstant(
                  Instant.ofEpochMilli(Long.parseLong(timestamp)), ZoneId.of("UTC"))));
      setSessionDurationAndJoinedDateTime(dto);
    }

    log.info(
        "Trip,Nick: {}, \\n Joined: {}, \\n Last seen: {}, \\n Seen active: {} ago, \\n Session duration: {}, \\n Last message: {}",
        tripOrNick,
        dto.getJoinedAtRfc1123(),
        dto.getLastSeenRfc1123(),
        dto.getTimeSinceSeen(),
        dto.getSessionDuration(),
        dto.getLastMessage());
    return """
        \\n Nick|Trip: %s
        \\n Joined: %s
        \\n Last seen: %s
        \\n Seen active: %s ago.
        \\n Session duration: %s \\n Last message: %s
        \\n
        """
        .formatted(
            tripOrNick,
            dto.getJoinedAtRfc1123(),
            dto.getLastSeenRfc1123(),
            dto.getTimeSinceSeen(),
            dto.getSessionDuration(),
            dto.getLastMessage());
  }

  @Override
  public Optional<RegisteredIdentity> resolveRegisteredIdentity(String nameOrTrip) {
    if (nameOrTrip == null || nameOrTrip.trim().isBlank()) return Optional.empty();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT DISTINCT n.name, t.trip FROM trip_names tn "
                + "JOIN names n ON n.id = tn.name_id JOIN trips t ON t.id = tn.trip_id "
                + "WHERE LOWER(n.name) = LOWER(?) OR LOWER(t.trip) = LOWER(?)")) {
      String value = nameOrTrip.trim();
      statement.setString(1, value);
      statement.setString(2, value);
      try (ResultSet result = statement.executeQuery()) {
        RegisteredIdentity identity = null;
        if (result.next())
          identity = new RegisteredIdentity(result.getString(1), result.getString(2));
        if (identity == null || result.next()) return Optional.empty();
        return Optional.of(identity);
      }
    } catch (SQLException e) {
      log.error("Unable to resolve registered identity", e);
      return Optional.empty();
    }
  }

  @Override
  public int deleteByNameOrTrip(String nameOrTrip) {
    Optional<RegisteredIdentity> identity = resolveRegisteredIdentity(nameOrTrip);
    return identity.map(value -> delete(value.name(), value.trip())).orElse(1);
  }

  @Override
  public int delete(String name, String trip) {
    try {
      runInTransaction(
          () -> {
            try (PreparedStatement pstmtNames = connection.prepareStatement(DELETE_TRIP_NAMES)) {
              pstmtNames.setString(1, trip);
              pstmtNames.setString(2, name);
              pstmtNames.executeUpdate();
            }

            try (PreparedStatement pstmtTrips = connection.prepareStatement(DELETE_TRIP)) {
              pstmtTrips.setString(1, trip);
              pstmtTrips.executeUpdate();
            }

            try (PreparedStatement pstmtTripNames = connection.prepareStatement(DELETE_NAME)) {
              pstmtTripNames.setString(1, name);
              pstmtTripNames.executeUpdate();
            }
          });
      return 0;
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace: ", e);
      return 1;
    }
  }

  @Override
  public int register(String name, String trip, String role) {
    try {
      runInTransaction(
          () -> {
            int nameId;
            try (PreparedStatement pstmtNames =
                    connection.prepareStatement(INSERT_NAMES, Statement.RETURN_GENERATED_KEYS);
                ResultSet rsNames =
                    executeInsertReturningKeys(
                        pstmtNames, statement -> statement.setString(1, name))) {
              rsNames.next();
              nameId = rsNames.getInt(1);
            }

            int tripId;
            try (PreparedStatement pstmtTrips =
                    connection.prepareStatement(INSERT_TRIPS, Statement.RETURN_GENERATED_KEYS);
                ResultSet rsTrips =
                    executeInsertReturningKeys(
                        pstmtTrips,
                        statement -> {
                          statement.setString(1, role);
                          statement.setString(2, trip);
                        })) {
              rsTrips.next();
              tripId = rsTrips.getInt(1);
            }

            try (PreparedStatement pstmtTripNames = connection.prepareStatement(INSERT_TRIP_NAME)) {
              pstmtTripNames.setInt(1, tripId);
              pstmtTripNames.setInt(2, nameId);
              pstmtTripNames.executeUpdate();
            }
          });
      return 0;
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace: ", e);
      return 1;
    }
  }

  @Override
  public boolean isNameRegistered(String name) {
    boolean exists = false;
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT id FROM names WHERE LOWER(name) = ?")) {
      statement.setString(1, name.toLowerCase());
      statement.execute();

      ResultSet resultSet = statement.getResultSet();
      while (resultSet.next()) {
        exists = true;
      }

      statement.close();
      resultSet.close();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
    return exists;
  }

  @Override
  public boolean isTripRegistered(String trip) {
    boolean exists = false;
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT id FROM trips WHERE LOWER(trip) = ?")) {
      statement.setString(1, trip.toLowerCase());
      statement.execute();

      ResultSet resultSet = statement.getResultSet();
      while (resultSet.next()) {
        exists = true;
      }

      statement.close();
      resultSet.close();
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace: ", e);
    }
    return exists;
  }

  @Override
  public void registerTripByName(String name, String trip) {
    String insertTripSql = "INSERT INTO trips (type, trip, created_on) VALUES ('REGULAR', ?, ?)";
    String insertTripNamesSql =
        "INSERT INTO trip_names (trip_id, name_id) SELECT ?, id FROM names WHERE name = ?";
    try {
      runInTransaction(
          () -> {
            int tripId;
            try (PreparedStatement pstmtInsertTrip =
                    connection.prepareStatement(insertTripSql, Statement.RETURN_GENERATED_KEYS);
                ResultSet rs =
                    executeInsertReturningKeys(
                        pstmtInsertTrip,
                        statement -> {
                          statement.setString(1, trip);
                          statement.setLong(2, DateUtil.getTimestampNow());
                        })) {
              if (rs.next()) {
                tripId = rs.getInt(1);
              } else {
                throw new SQLException("Failed to retrieve generated ID");
              }
            }

            try (PreparedStatement pstmtInsertTripNames =
                connection.prepareStatement(insertTripNamesSql)) {
              pstmtInsertTripNames.setInt(1, tripId);
              pstmtInsertTripNames.setString(2, name);
              pstmtInsertTripNames.executeUpdate();
            }
          });
      log.info("Registered new trip: {}, for name: {}", trip, name);
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace: ", e);
    }
  }

  @Override
  public List<Message> lastMessages(String name, String trip, int count) {
    if (count <= 0) {
      /* default */
      count = 5;
    }

    List<Message> messages = new ArrayList<>();
    try {
      PreparedStatement statement = connection.prepareStatement(SqlUtil.SELECT_LAST_N_MESSAGES);
      if (name == null) {
        statement.setNull(1, Types.VARCHAR);
      } else {
        statement.setString(1, name);
      }
      statement.setString(2, trip);
      statement.setInt(3, count);
      statement.execute();

      ResultSet resultSet = statement.getResultSet();
      while (resultSet.next()) {
        String timestamp = resultSet.getString("created_on");
        String text = resultSet.getString("message");
        String mName = resultSet.getString("name");

        Message message = new Message(mName, trip, text, timestamp);
        messages.add(message);
      }

      statement.close();
      resultSet.close();
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace: ", e);
    }

    return messages;
  }

  @Override
  public void registerNameByTrip(String name, String trip) {
    String insertNameSql = "INSERT INTO names (name, created_on) VALUES (?, ?)";
    String insertTripNamesSql =
        "INSERT INTO trip_names (trip_id, name_id) SELECT id, ? FROM trips WHERE trip = ?";

    try {
      runInTransaction(
          () -> {
            int nameId;
            try (PreparedStatement pstmtInsertName =
                    connection.prepareStatement(insertNameSql, Statement.RETURN_GENERATED_KEYS);
                ResultSet rs =
                    executeInsertReturningKeys(
                        pstmtInsertName,
                        statement -> {
                          statement.setString(1, name);
                          statement.setLong(2, DateUtil.getTimestampNow());
                        })) {
              if (rs.next()) {
                nameId = rs.getInt(1);
              } else {
                log.error("Failed to retrieve generated ID");
                throw new SQLException("Failed to retrieve generated ID");
              }
            }

            try (PreparedStatement pstmtInsertTripNames =
                connection.prepareStatement(insertTripNamesSql)) {
              pstmtInsertTripNames.setInt(1, nameId);
              pstmtInsertTripNames.setString(2, trip);
              pstmtInsertTripNames.executeUpdate();
            }
          });
      log.info("Registered new name: {}, for trip: {}", name, trip);
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace: ", e);
    }
  }

  @Override
  public List<String> getNicksByTrip(String trip) {
    List<String> trips = new ArrayList<>();
    try {
      PreparedStatement nicks = connection.prepareStatement(SqlUtil.GET_NICKS_BY_TRIP);
      nicks.setString(1, trip.toLowerCase());
      nicks.execute();

      ResultSet resultSet = nicks.getResultSet();
      while (resultSet.next()) {
        trips.add(resultSet.getString("name"));
      }
      nicks.close();
      resultSet.close();
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace", e);
    }

    return trips;
  }

  public void setSessionDurationAndJoinedDateTime(LastSeenDto dto) {
    String joinedAt = null;
    try {
      PreparedStatement statement = connection.prepareStatement(SqlUtil.SELECT_SESSION_JOINED);
      statement.setString(1, dto.getTripOrNick());
      statement.setString(2, dto.getTripOrNick());
      statement.execute();

      ResultSet resultSet = statement.getResultSet();
      while (resultSet.next()) {
        joinedAt = resultSet.getString("created_on");
      }

      statement.close();
      resultSet.close();
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace: ", e);
    }

    if (joinedAt != null) {
      // move to utils.
      ZoneId utc = ZoneId.of("UTC");
      ZonedDateTime now = ZonedDateTime.now(utc);
      ZonedDateTime joined =
          ZonedDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(joinedAt)), utc);

      dto.setSessionDuration(getDifference(now, joined));
      dto.setJoinedAtRfc1123(
          DateUtil.formatRfc1123(Long.parseLong(joinedAt), TimeUnit.MILLISECONDS, utc.toString()));
    }
  }

  private void runInTransaction(SqlWork work) throws SQLException {
    boolean initialAutoCommit = connection.getAutoCommit();
    connection.setAutoCommit(false);
    try {
      work.run();
      connection.commit();
    } catch (SQLException e) {
      connection.rollback();
      throw e;
    } finally {
      connection.setAutoCommit(initialAutoCommit);
    }
  }

  private ResultSet executeInsertReturningKeys(
      PreparedStatement statement, SqlStatementBinder binder) throws SQLException {
    binder.bind(statement);
    statement.executeUpdate();
    return statement.getGeneratedKeys();
  }

  @FunctionalInterface
  private interface SqlWork {
    void run() throws SQLException;
  }

  @FunctionalInterface
  private interface SqlStatementBinder {
    void bind(PreparedStatement statement) throws SQLException;
  }
}
