package org.saturn.app.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.model.Role;
import org.saturn.app.model.command.UserCommand;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.AuthorizationService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.BlockingQueue;

@Slf4j
public class AuthorizationServiceImpl extends OutService implements AuthorizationService {
    private final Connection connection;

    public AuthorizationServiceImpl(Connection connection, BlockingQueue<String> outgoingMessageQueue) {
        super(outgoingMessageQueue);
        this.connection = connection;
        log.info("AuthorizationServiceImpl initiated");
    }

    public boolean isUserAuthorized(UserCommand userCommand, ChatMessage chatMessage) {
        if (isAllowedByApplicationConfig(userCommand, chatMessage)) {
            return true;
        }

        if (isAllowedByDb(userCommand, chatMessage)) {
            return true;
        }

        enqueueMessageForSending(chatMessage.getNick(), " msg mercury for access.", chatMessage.isWhisper());
        return false;
    }

    private boolean isAllowedByApplicationConfig(UserCommand userCommand, ChatMessage chatMessage) {
        List<String> cmdAllowedTrips = userCommand.getAuthorizedTrips();
        boolean isWhitelistedByApp =  cmdAllowedTrips.contains(chatMessage.getTrip()) ||  cmdAllowedTrips.contains("x");
        if (!isWhitelistedByApp) {
            log.warn("user: {}, trip: {}, [is not] whitelisted in [./application.properties] config", chatMessage.getNick(), chatMessage.getTrip());
            return false;
        }
        log.warn("user: {}, trip: {}, [is] whitelisted in [./application.properties] config", chatMessage.getNick(), chatMessage.getTrip());
        return true;
    }

    private boolean isAllowedByDb(UserCommand userCommand, ChatMessage chatMessage) {
        Role minRequiredRole = userCommand.getAuthorizedRole();
        Role userRole = getRoleByTrip(chatMessage.getTrip());

        boolean isAllowed = userRole.getValue() >= minRequiredRole.getValue();
        if (!isAllowed) {
            log.warn("user: {}, trip: {}, [is not] allowed to execute: [{}] command. Required role: {}, provided: {}",
                    chatMessage.getNick(), chatMessage.getTrip(), userCommand.getAliases().get(0), minRequiredRole, userRole);
            return false;
        }
        log.warn("user: {}, trip: {}, [is] whitelisted, Role: {}, required: {}", chatMessage.getNick(), chatMessage.getTrip(), userRole, minRequiredRole);
        return true;
    }

    private Role getRoleByTrip(String trip) {
        Role role = Role.REGULAR;
        log.debug("Querying the db for user role, trip: {}", trip);
        try {
            PreparedStatement statement = connection.prepareStatement(
                    "SELECT type FROM trips WHERE trip == ?; ");
            statement.setString(1, trip);
            statement.execute();

            ResultSet resultSet = statement.getResultSet();
            while (resultSet.next()) {
                role = Role.valueOf(resultSet.getString("type"));
                log.info("Retrieved Role: {}, for user trip: {}", role, trip);
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
}
