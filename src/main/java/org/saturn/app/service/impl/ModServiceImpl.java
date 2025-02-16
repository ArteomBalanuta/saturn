package org.saturn.app.service.impl;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.model.dto.BanRecord;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.ModService;
import org.saturn.app.util.DateUtil;
import org.saturn.app.util.SqlUtil;

@Slf4j
public class ModServiceImpl extends OutService implements ModService {
  private final Connection connection;
  private final Base64.Encoder encoder;
  private final Base64.Decoder decoder;

  public ModServiceImpl(
      Connection connection, BlockingQueue<String> queue, BlockingQueue<String> rawMessageQueue) {
    super(queue, rawMessageQueue);
    this.connection = connection;

    encoder = Base64.getEncoder();
    decoder = Base64.getDecoder();
  }

  @Override
  public void shadowBan(BanRecord banDto) {
    try {
      PreparedStatement statement =
          connection.prepareStatement(
              SqlUtil.INSERT_INTO_BANNED_USERS_TRIP_NAME_HASH_REASON_CREATED_ON_VALUES);

      if (banDto.trip() != null) {
        statement.setString(1, banDto.trip());
      } else {
        statement.setNull(1, Types.VARCHAR);
      }

      if (banDto.name() != null) {
        statement.setString(2, banDto.name());
      } else {
        statement.setNull(2, Types.VARCHAR);
      }

      if (banDto.hash() != null) {
        String hashedHash = encoder.encodeToString(banDto.hash().getBytes(StandardCharsets.UTF_8));
        statement.setString(3, hashedHash);
      } else {
        statement.setNull(3, Types.VARCHAR);
      }

      if (banDto.reason() != null) {
        statement.setString(4, banDto.reason());
      } else {
        statement.setNull(4, Types.VARCHAR);
      }

      statement.setLong(5, DateUtil.getTimestampNow());
      statement.executeUpdate();

      statement.close();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void ban(String target) {
    enqueueRawMessageForSending(String.format("{ \"cmd\": \"ban\", \"nick\": \"%s\"}", target));
  }

  @Override
  public void unban(String target) {
    enqueueRawMessageForSending(String.format("{ \"cmd\": \"unban\", \"hash\": \"%s\"}", target));
  }

  @Override
  public void lock() {
    enqueueRawMessageForSending("{ \"cmd\": \"lockroom\"}");
  }

  @Override
  public void unlock() {
    enqueueRawMessageForSending("{ \"cmd\": \"unlockroom\"}");
  }

  @Override
  public void enableCaptcha() {
    enqueueRawMessageForSending("{ \"cmd\": \"enablecaptcha\"}");
  }

  @Override
  public void auth(String trip) {
    enqueueRawMessageForSending(String.format("{ \"cmd\": \"authtrip\", \"trip\": \"%s\"}", trip));
  }

  @Override
  public void deauth(String trip) {
    enqueueRawMessageForSending(
        String.format("{ \"cmd\": \"deauthtrip\", \"trip\": \"%s\"}", trip));
  }

  @Override
  public void disableCaptcha() {
    enqueueRawMessageForSending("{ \"cmd\": \"disablecaptcha\"}");
  }

  @Override
  public void unshadowBan(String target) {
    try {
      PreparedStatement statement =
          connection.prepareStatement(SqlUtil.DELETE_FROM_BANNED_USERS_WHERE_NAME_OR_TRIP_OR_HASH);
      statement.setString(1, target);
      statement.setString(2, target);

      String hashedHash = encoder.encodeToString(target.getBytes(StandardCharsets.UTF_8));
      statement.setString(3, hashedHash);
      statement.executeUpdate();

      statement.close();
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace", e);
    }
  }

  public List<BanRecord> getBannedUsers() {
    List<BanRecord> banned = new ArrayList<>();
    try {
      PreparedStatement statement = connection.prepareStatement(SqlUtil.SELECT_BANNED_USERS);
      statement.execute();

      ResultSet resultSet = statement.getResultSet();
      while (resultSet.next()) {
        String hash = resultSet.getString("hash");
        if (hash != null) {
          byte[] decodedBytes = decoder.decode(hash.getBytes(StandardCharsets.UTF_8));
          hash = new String(decodedBytes, StandardCharsets.UTF_8);
        }

        BanRecord banDto =
            new BanRecord(
                resultSet.getString("trip"),
                resultSet.getString("name"),
                hash,
                resultSet.getString("reason"));

        banned.add(banDto);
      }
      statement.close();
      resultSet.close();
      return banned;
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace", e);
    }

    return Collections.emptyList();
  }

  @Override
  public void listBanned(ChatMessage chatMessage) {
    String author = chatMessage.getNick();
    List<BanRecord> bannedIds = this.getBannedUsers();

    StringBuilder output = new StringBuilder();
    bannedIds.forEach(
        user ->
            output
                .append(user.hash())
                .append(" - ")
                .append(
                    user.trip() == null || Objects.equals(user.trip(), "") ? "------" : user.trip())
                .append(" - ")
                .append(user.name())
                .append("\\n"));

    if (bannedIds.isEmpty()) {
      enqueueMessageForSending(author, "No users has been banned.", chatMessage.isWhisper());
    } else {
      enqueueMessageForSending(
          author, "Banned hashes, trips, names: \\n" + output, chatMessage.isWhisper());
    }
  }

  @Override
  public void unbanAll(String author) {
    List<BanRecord> bannedIds = this.getBannedUsers();
    if (bannedIds.isEmpty()) {
      enqueueMessageForSending(author, "No users has been banned.", false);
    } else {
      StringBuilder output = new StringBuilder();
      bannedIds.forEach(
          user ->
              output
                  .append(user.hash())
                  .append(" - ")
                  .append(
                      user.trip() == null || Objects.equals(user.trip(), "")
                          ? "------"
                          : user.trip())
                  .append(" - ")
                  .append(user.name())
                  .append("\\n"));

      try {
        PreparedStatement notesByTrip =
            connection.prepareStatement(SqlUtil.DELETE_FROM_BANNED_USERS);
        notesByTrip.executeUpdate();

        notesByTrip.close();
      } catch (SQLException e) {
        log.info("Error: {}", e.getMessage());
        log.error("Stack trace", e);
      }
      enqueueMessageForSending(author, "Unbanned hashes, trips, nicks: \\n" + output, false);
    }
  }

  @Override
  public void kick(String target) {
    enqueueRawMessageForSending(String.format("{ \"cmd\": \"kick\", \"nick\": \"%s\"}", target));
  }

  @Override
  public void kickTo(String target, String channel) {
    enqueueRawMessageForSending(
        String.format("{ \"cmd\": \"kick\", \"nick\": \"%s\", \"to\":\"%s\"}", target, channel));
  }

  @Override
  public void overflow(String target) {
    enqueueRawMessageForSending(
        String.format("{ \"cmd\": \"overflow\", \"nick\": \"%s\"}", target));
  }

  @Override
  public Optional<BanRecord> isShadowBanned(User target) {
    Optional<BanRecord> bannedUser = Optional.empty();
    if (target == null) {
      return bannedUser;
    }
    List<BanRecord> bannedIds = getBannedUsers();
    for (BanRecord banned : bannedIds) {
      if (target.getTrip() != null
          && banned.trip() != null
          && target.getTrip().equals(banned.trip())) {
        bannedUser = Optional.of(banned);
        log.warn("User's trip is banned: {}", banned.trip());
      }
      if (target.getNick() != null
          && banned.name() != null
          && target.getNick().equals(banned.name())) {
        bannedUser = Optional.of(banned);
        log.warn("User's nick is banned: {}", banned.name());
      }

      if (target.getHash() != null
          && banned.hash() != null
          && target.getHash().equals(banned.hash())) {
        bannedUser = Optional.of(banned);
        log.warn("User's hash is banned: {}", banned.hash());
      }
    }

    return bannedUser;
  }
}
