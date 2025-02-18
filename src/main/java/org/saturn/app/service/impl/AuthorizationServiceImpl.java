package org.saturn.app.service.impl;

import static org.saturn.app.util.SqlUtil.SELECT_ROLE_BY_TRIP;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommand;
import org.saturn.app.model.Role;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.AuthorizationService;
import org.saturn.app.util.DateUtil;
import org.saturn.app.util.SqlUtil;

@Slf4j
public class AuthorizationServiceImpl extends OutService implements AuthorizationService {
  private final Connection connection;

  public AuthorizationServiceImpl(
      Connection connection, BlockingQueue<String> outgoingMessageQueue) {
    super(outgoingMessageQueue);
    this.connection = connection;
    log.info("AuthorizationServiceImpl initiated");
  }

  public boolean isUserAuthorized(UserCommand userCommand, ChatMessage chatMessage) {
    if (isAllowedByApplicationConfig(userCommand, chatMessage)
        || isAllowedByDb(userCommand, chatMessage)) {
      return true;
    } else {
      enqueueMessageForSending(
          chatMessage.getNick(), "msg mercury for access.", chatMessage.isWhisper());
      return false;
    }
  }

  @Override
  public void grant(String trip, Role role) {
    if (getRoleByTrip(trip).isPresent()) {
      int updatedRows = update(trip, role);
      if (updatedRows == 1) {
        log.warn("Granted new Role: {}, to trip: {}", role.name(), trip);
      }
    } else {
      insert(trip, role);
      log.warn("Granted Role: {}, to trip: {}", role.name(), trip);
    }
  }

  private boolean isAllowedByApplicationConfig(UserCommand userCommand, ChatMessage chatMessage) {
    List<String> cmdAllowedTrips = userCommand.getAuthorizedTrips();
    boolean isWhitelistedByApp =
        cmdAllowedTrips.contains(chatMessage.getTrip()) || cmdAllowedTrips.contains("x");
    if (!isWhitelistedByApp) {
      log.warn(
          "user: {}, trip: {}, [is not] whitelisted in [./application.properties] config",
          chatMessage.getNick(),
          chatMessage.getTrip());
      return false;
    }
    log.warn(
        "user: {}, trip: {}, [is] whitelisted in [./application.properties] config",
        chatMessage.getNick(),
        chatMessage.getTrip());
    return true;
  }

  private boolean isAllowedByDb(UserCommand userCommand, ChatMessage chatMessage) {
    Role minRequiredRole = userCommand.getAuthorizedRole();
    Optional<Role> userRole = getRoleByTrip(chatMessage.getTrip());

    if (userRole.isEmpty()) {
      userRole = Optional.of(Role.REGULAR);
      log.warn(
          "User: {}, trip: {}, has no role set. Granted REGULAR role permissions.",
          chatMessage.getNick(),
          chatMessage.getTrip());
    }

    boolean isAllowed = userRole.get().getValue() >= minRequiredRole.getValue();
    if (!isAllowed) {
      log.warn(
          "user: {}, trip: {}, [is not] allowed to execute: [{}] command. Required role: {}, provided: {}",
          chatMessage.getNick(),
          chatMessage.getTrip(),
          userCommand.getAliases().get(0),
          minRequiredRole,
          userRole);
      return false;
    }
    log.warn(
        "user: {}, trip: {}, [is] whitelisted, Role: {}, required: {}",
        chatMessage.getNick(),
        chatMessage.getTrip(),
        userRole,
        minRequiredRole);
    return true;
  }

  private Optional<Role> getRoleByTrip(String trip) {
    Optional<Role> role = Optional.empty();
    log.debug("Querying the db for user role, trip: {}", trip);
    try {
      PreparedStatement statement = connection.prepareStatement(SELECT_ROLE_BY_TRIP);
      statement.setString(1, trip);
      statement.execute();

      ResultSet resultSet = statement.getResultSet();
      while (resultSet.next()) {
        role = Optional.of(Role.valueOf(resultSet.getString("type")));
        log.warn("Retrieved Role: {}, for user trip: {}", role.get().name(), trip);
      }
      statement.close();
      resultSet.close();

      return role;
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace", e);
    }
    return role;
  }

  private void insert(String trip, Role role) {
    try {
      PreparedStatement statement =
          connection.prepareStatement(SqlUtil.INSERT_INTO_TRIPS_TYPE_TRIP_CREATED_ON_VALUES);
      statement.setString(1, role.name());
      statement.setString(2, trip);
      statement.setLong(3, DateUtil.getTimestampNow());
      statement.executeUpdate();

      statement.close();
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace", e);
    }
  }

  private int update(String trip, Role newRole) {
    try {
      PreparedStatement insertNote =
          connection.prepareStatement(SqlUtil.UPDATE_TRIPS_SET_TYPE_WHERE_TRIP);
      insertNote.setString(1, newRole.name());
      insertNote.setString(2, trip);

      int i = insertNote.executeUpdate();
      insertNote.close();

      return i;
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace", e);
    }

    return 0;
  }
}
