package org.saturn.app.service.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.saturn.app.command.UserCommand;
import org.saturn.app.model.dto.Mail;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.MailService;
import org.saturn.app.util.DateUtil;
import org.saturn.app.util.SqlUtil;
import org.saturn.app.util.Util;

@Slf4j
public class MailServiceImpl extends OutService implements MailService {
  private final Connection connection;

  public MailServiceImpl(Connection connection, BlockingQueue<String> outQueue) {
    super(outQueue);
    this.connection = connection;
  }

  @Override
  public void executeMail(ChatMessage chatMessage, UserCommand command) {
    List<String> arguments = command.getArguments();
    String author = chatMessage.getNick();
    String receiver = arguments.get(0).replace("@", "");

    /* check against trip_names table. */
    List<String> trips = this.getTripsByNick(receiver);
    if (trips.isEmpty()) {
      String registeredUsers = getRegisteredUsers();
      enqueueMessageForSending(
              author,
              "User you specified is not registered. Please use a name from provided list to send a message to respective trip. \\n" + registeredUsers,
              chatMessage.isWhisper());
      return;
    }

    receiver = Util.listToCommaString(trips);

    StringBuilder message = new StringBuilder();
    /* skipping fist argument as it is the receiver's nickname */
    for (int i = 1; i < arguments.size(); i++) {
      message.append(arguments.get(i)).append(" ");
    }

    this.orderMessageDelivery(
        message.toString(),
        author.concat("#") + chatMessage.getTrip(),
        receiver,
        String.valueOf(chatMessage.isWhisper()));
    enqueueMessageForSending(
        author,
        "trips: " + receiver + " will receive your message as soon they chat",
        chatMessage.isWhisper());
  }

  /**
   * parse :msg @receiver message on user message - if user has messages to read, enqueues the
   * message and removes the pending status
   */
  @Override
  public void orderMessageDelivery(
      String message, String owner, String receiver, String isWhisper) {
    try {
      PreparedStatement insertMessage =
          connection.prepareStatement(
              SqlUtil.INSERT_INTO_MAIL_OWNER_RECEIVER_MESSAGE_STATUS_IS_WHISPER_CREATED_ON_VALUES);
      insertMessage.setString(1, owner);
      insertMessage.setString(2, receiver);
      insertMessage.setString(3, StringEscapeUtils.escapeJson(message));
      insertMessage.setString(4, "PENDING");
      insertMessage.setString(5, isWhisper);
      insertMessage.setLong(6, DateUtil.getTimestampNow());

      insertMessage.executeUpdate();

      insertMessage.close();
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace", e);
    }
  }

  @Override
  public List<String> getTripsByNick(String nick) {
    List<String> trips = new ArrayList<>();
    try {
      PreparedStatement trip = connection.prepareStatement(SqlUtil.GET_TRIP_BY_NICK_REGISTERED);
      trip.setString(1, nick.toLowerCase());
      trip.execute();

      ResultSet resultSet = trip.getResultSet();
      while (resultSet.next()) {
        trips.add(resultSet.getString("trip"));
      }
      trip.close();
      resultSet.close();
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace", e);
    }

    return trips;
  }

  public String getRegisteredUsers() {
    StringBuilder b = new StringBuilder();
    try {
      PreparedStatement stm = connection.prepareStatement(SqlUtil.SELECT_NAME_TRIP_REGISTERED);
      stm.execute();

      ResultSet resultSet = stm.getResultSet();
      while (resultSet.next()) {
         b.append(resultSet.getString("name")).append(" ").append(resultSet.getString("trip")).append("\\n");
      }
      stm.close();
      resultSet.close();
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace", e);
    }
    return b.toString();
  }

  @Override
  public List<Mail> getMailByTrip(String trip) {
    List<Mail> messages = new ArrayList<>();
    try {
      PreparedStatement mail = connection.prepareStatement(SqlUtil.SELECT_MAIL_BY_NICK_OR_TRIP);
      mail.setString(1, "%" + trip + "%");
      mail.execute();

      ResultSet resultSet = mail.getResultSet();
      while (resultSet.next()) {
        Mail message =
            new Mail(
                resultSet.getString("id"),
                resultSet.getString("owner"),
                resultSet.getString("receiver"),
                resultSet.getString("message"),
                resultSet.getString("status"),
                resultSet.getString("is_whisper"),
                resultSet.getLong("created_on"));

        messages.add(message);
      }
      mail.close();
      resultSet.close();
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace", e);
    }

    return messages;
  }

  @Override
  public void updateMailStatus(String id) {
    try {
      PreparedStatement insertMessage =
          connection.prepareStatement(SqlUtil.UPDATE_MAIL_SET_STATUS_DELIVERED_WHERE_RECEIVER);
      insertMessage.setString(1, id);

      insertMessage.executeUpdate();

      insertMessage.close();
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace", e);
    }
  }
}
