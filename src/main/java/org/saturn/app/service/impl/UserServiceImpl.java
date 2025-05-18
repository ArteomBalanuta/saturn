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

    if (names.isEmpty() || (names.size() == 1 && names.getFirst().equalsIgnoreCase(user.getNick()))) {
      return Optional.empty();
    } else {
      return Optional.of(
          "\\n @"
              + user.getNick()
              + ", has been seen online as: "
              + names.toString().replace("[","").replace("]","")
              + " in last 15 minutes. "
              + "\\n");
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
    return "\\n Nick|Trip: "
        + tripOrNick
        + "\\n Joined: "
        + dto.getJoinedAtRfc1123()
        + "\\n Last seen: "
        + dto.getLastSeenRfc1123()
        + "\\n Seen active: "
        + dto.getTimeSinceSeen()
        + " ago."
        + "\\n Session duration: "
        + dto.getSessionDuration()
        + " \\n Last message: "
        + dto.getLastMessage()
        + "\\n";
  }

  @Override
  public int delete(String name, String trip) {
    try {
      connection.setAutoCommit(false); // Begin transaction

      // Delete from name_trips
      try (PreparedStatement pstmtNames = connection.prepareStatement(DELETE_TRIP_NAMES)) {
        pstmtNames.setString(1, trip);
        pstmtNames.setString(2, name);
        pstmtNames.executeUpdate();

        // Delete from trips
        try (PreparedStatement pstmtTrips = connection.prepareStatement(DELETE_TRIP)) {
          pstmtTrips.setString(1, trip);
          pstmtTrips.executeUpdate();

          // Delete from names
          try (PreparedStatement pstmtTripNames = connection.prepareStatement(DELETE_NAME)) {
            pstmtTripNames.setString(1, name);
            pstmtTripNames.executeUpdate();
          }
        }
      }
      connection.commit(); // Commit transaction
      connection.setAutoCommit(true);
    } catch (SQLException e) {
      try {
        connection.rollback();
      } catch (SQLException ex) {
        log.info("Error: {}", e.getMessage());
        log.error("Stack trace: ", e);
        throw new RuntimeException(ex);
      }
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace: ", e);

      return 1;
    }

    return 0;
  }

  @Override
  public int register(String name, String trip, String role) {
    try {
      connection.setAutoCommit(false); // Begin transaction

      // Insert into names table
      try (PreparedStatement pstmtNames =
          connection.prepareStatement(INSERT_NAMES, Statement.RETURN_GENERATED_KEYS)) {
        pstmtNames.setString(1, name);
        pstmtNames.executeUpdate();

        ResultSet rsNames = pstmtNames.getGeneratedKeys();
        rsNames.next();
        int nameId = rsNames.getInt(1);

        // Insert into trips table
        try (PreparedStatement pstmtTrips =
            connection.prepareStatement(INSERT_TRIPS, Statement.RETURN_GENERATED_KEYS)) {
          pstmtTrips.setString(1, role);
          pstmtTrips.setString(2, trip);
          pstmtTrips.executeUpdate();

          ResultSet rsTrips = pstmtTrips.getGeneratedKeys();
          rsTrips.next();
          int tripId = rsTrips.getInt(1);

          // Insert into trip_names table
          try (PreparedStatement pstmtTripNames = connection.prepareStatement(INSERT_TRIP_NAME)) {
            pstmtTripNames.setInt(1, tripId);
            pstmtTripNames.setInt(2, nameId);
            pstmtTripNames.executeUpdate();
          }
        }
      }
      connection.commit(); // Commit transaction
      connection.setAutoCommit(true);
    } catch (SQLException e) {
      try {
        connection.rollback();
      } catch (SQLException ex) {
        log.info("Error: {}", e.getMessage());
        log.error("Stack trace: ", e);
        throw new RuntimeException(ex);
      }
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace: ", e);

      return 1;
    }

    return 0;
  }

  @Override
  public boolean isNameRegistered(String name) {
    boolean exists = false;
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT id from names where LOWER(name)==?")) {
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
        connection.prepareStatement("SELECT id from trips where LOWER(trip)==?")) {
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
    String insertTripSql =
        "INSERT INTO trips (type, trip, created_on) VALUES ('MODERATOR', ?, strftime('%s', 'now'))";
    String insertTripNamesSql =
        "INSERT INTO trip_names (trip_id, name_id) SELECT ?, id FROM names WHERE name = ?";
    try {
      connection.setAutoCommit(false); // Begin transaction
      // First statement: Insert into trips and get the generated ID
      int tripId;
      try (PreparedStatement pstmtInsertTrip =
          connection.prepareStatement(insertTripSql, Statement.RETURN_GENERATED_KEYS)) {
        pstmtInsertTrip.setString(1, trip); // Set the trip parameter
        pstmtInsertTrip.executeUpdate();

        ResultSet rs = pstmtInsertTrip.getGeneratedKeys();
        if (rs.next()) {
          tripId = rs.getInt(1); // Retrieve the generated ID
        } else {
          throw new SQLException("Failed to retrieve generated ID");
        }
      }

      // Second statement: Insert into trip_names using the ID from the first insert
      try (PreparedStatement pstmtInsertTripNames =
          connection.prepareStatement(insertTripNamesSql)) {
        pstmtInsertTripNames.setInt(1, tripId); // Use the retrieved trip ID
        pstmtInsertTripNames.setString(2, name); // Set the name parameter
        pstmtInsertTripNames.executeUpdate();
      }

      connection.commit(); // Commit transaction
      log.info("Registered new trip: {}, for name: {}", trip, name);
    } catch (SQLException e) {
      try {
        connection.rollback(); // Rollback in case of error
      } catch (SQLException ex) {
        log.info("Error: {}", e.getMessage());
        log.error("Stack trace: ", e);
        throw new RuntimeException(ex);
      }
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
      statement.setString(3, String.valueOf(count));
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
    String insertNameSql = "INSERT INTO names (name, created_on) VALUES (?, strftime('%s', 'now'))";
    String insertTripNamesSql =
        "INSERT INTO trip_names (trip_id, name_id) SELECT id, ? FROM trips WHERE trip = ?";

    try {
      connection.setAutoCommit(false); // Begin transaction
      // First statement: Insert into names and get the generated ID
      int nameId;
      try (PreparedStatement pstmtInsertName =
          connection.prepareStatement(insertNameSql, Statement.RETURN_GENERATED_KEYS)) {
        pstmtInsertName.setString(1, name); // Set the name parameter
        pstmtInsertName.executeUpdate();

        ResultSet rs = pstmtInsertName.getGeneratedKeys();
        if (rs.next()) {
          nameId = rs.getInt(1); // Retrieve the generated ID
        } else {
          log.error("Failed to retrieve generated ID");
          throw new SQLException("Failed to retrieve generated ID");
        }
      }

      // Second statement: Insert into trip_names using the ID from the first insert
      try (PreparedStatement pstmtInsertTripNames =
          connection.prepareStatement(insertTripNamesSql)) {
        pstmtInsertTripNames.setInt(1, nameId); // Use the retrieved ID
        pstmtInsertTripNames.setString(2, trip); // Set the trip parameter
        pstmtInsertTripNames.executeUpdate();
      }

      connection.commit(); // Commit transaction

      log.info("Registered new name: {}, for trip: {}", name, trip);
    } catch (SQLException e) {
      try {
        connection.rollback(); // Rollback in case of error
      } catch (SQLException ex) {
        log.info("Error: {}", e.getMessage());
        log.error("Stack trace: ", e);
        throw new RuntimeException(ex);
      }
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace: ", e);
    }
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
}
