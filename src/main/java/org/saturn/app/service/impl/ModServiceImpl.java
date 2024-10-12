package org.saturn.app.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.model.dto.BanDto;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.ModService;
import org.saturn.app.util.DateUtil;
import org.saturn.app.util.SqlUtil;

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
import java.util.concurrent.BlockingQueue;

@Slf4j
public class ModServiceImpl extends OutService implements ModService {
    private final Connection connection;
    private final Base64.Encoder encoder;
    private final Base64.Decoder decoder;

    public ModServiceImpl(Connection connection, BlockingQueue<String> queue, BlockingQueue<String> rawMessageQueue) {
        super(queue, rawMessageQueue);
        this.connection = connection;

        encoder = Base64.getEncoder();
        decoder = Base64.getDecoder();
    }

    @Override
    public void shadowBan(BanDto banDto) {
        try {
            PreparedStatement statement = connection
                    .prepareStatement(SqlUtil.INSERT_INTO_BANNED_USERS_TRIP_NAME_HASH_REASON_CREATED_ON_VALUES);

            if (banDto.getTrip() != null) {
                statement.setString(1, banDto.getTrip());
            } else {
                statement.setNull(1, Types.VARCHAR);
            }

            if (banDto.getName() != null) {
                statement.setString(2, banDto.getName());
            } else {
                statement.setNull(2, Types.VARCHAR);
            }

            if (banDto.getHash() != null) {
                String hashedHash = encoder.encodeToString(banDto.getHash().getBytes(StandardCharsets.UTF_8));
                statement.setString(3, hashedHash);
            } else {
                statement.setNull(3, Types.VARCHAR);
            }

            if (banDto.getReason() != null) {
                statement.setString(4, banDto.getReason());
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
    public void disableCaptcha() {
        enqueueRawMessageForSending("{ \"cmd\": \"disablecaptcha\"}");
    }

    @Override
    public void unshadowBan(String target) {
        try {
            PreparedStatement statement = connection
                    .prepareStatement(SqlUtil.DELETE_FROM_BANNED_USERS_WHERE_NAME_OR_TRIP_OR_HASH);
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

    public List<BanDto> getBannedUsers() {
        List<BanDto> banned = new ArrayList<>();
        try {
            PreparedStatement statement = connection.prepareStatement(
                    SqlUtil.SELECT_BANNED_USERS);
            statement.execute();

            ResultSet resultSet = statement.getResultSet();
            while (resultSet.next()) {
                String hash = resultSet.getString("hash");
                if (hash != null) {
                    byte[] decodedBytes = decoder.decode(hash.getBytes(StandardCharsets.UTF_8));
                    hash = new String(decodedBytes, StandardCharsets.UTF_8);
                }

                BanDto banDto = new BanDto(
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
        List<BanDto> bannedIds = this.getBannedUsers();

        StringBuilder output = new StringBuilder();
        bannedIds.forEach(user -> {
            output.append(user.getHash()).append(" - ").append(user.getTrip() == null || Objects.equals(user.getTrip(), "") ? "------" : user.getTrip()).append(" - ").append(user.getName()).append("\\n");
        });

        if (bannedIds.isEmpty()) {
            enqueueMessageForSending(author,"No users has been banned.", chatMessage.isWhisper());
        } else {
            enqueueMessageForSending(author,"Banned hashes, trips, names: \\n" + output, chatMessage.isWhisper());
        }
    }

    @Override
    public void unbanAll(String author) {
        List<BanDto> bannedIds = this.getBannedUsers();
        if (bannedIds.isEmpty()) {
            enqueueMessageForSending("", "No users has been banned.", false);
        } else {
            StringBuilder output = new StringBuilder();
            bannedIds.forEach(user -> output.append(user.getHash()).append(" - ").append(user.getTrip() == null || Objects.equals(user.getTrip(), "") ? "------" : user.getTrip()).append(" - ").append(user.getName()).append("\\n"));

            try {
                PreparedStatement notesByTrip = connection.prepareStatement(SqlUtil.DELETE_FROM_BANNED_USERS);
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
        enqueueRawMessageForSending(String.format("{ \"cmd\": \"kick\", \"nick\": \"%s\", \"to\":\"%s\"}", target, channel));
    }

    @Override
    public void overflow(String target) {
        enqueueRawMessageForSending(String.format("{ \"cmd\": \"overflow\", \"nick\": \"%s\"}", target));
    }

    @Override
    public boolean isBanned(User target) {
        if (target == null) {
            return false;
        }

        List<BanDto> bannedIds = getBannedUsers();

        for (BanDto banned : bannedIds) {
            boolean isTripPresent = target.getTrip() != null && banned.getTrip() != null;
            if (isTripPresent && target.getTrip().equals(banned.getTrip())) {
                return true;
            }
            if (target.getNick().equals(banned.getName()) || target.getHash().equals(banned.getHash())) {
                return true;
            }
        }

        return false;
    }
}
