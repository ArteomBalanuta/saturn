package org.saturn.app.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.model.dto.LastSeenDto;
import org.saturn.app.service.UserService;
import org.saturn.app.util.DateUtil;
import org.saturn.app.util.SqlUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.apache.commons.text.StringEscapeUtils.escapeJson;
import static org.saturn.app.util.DateUtil.formatRfc1123;
import static org.saturn.app.util.DateUtil.getDifference;

@Slf4j
public class UserServiceImpl extends OutService implements UserService {
    private final Connection connection;

    public UserServiceImpl(Connection connection, BlockingQueue<String> queue) {
        super(queue);
        this.connection = connection;
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
            dto.setLastSeenRfc1123(formatRfc1123(Long.parseLong(timestamp), TimeUnit.MILLISECONDS, "UTC"));
        }

        if (lastMessage != null) {
            dto.setLastMessage(escapeJson(lastMessage));
        }

        if (timestamp != null) {
            dto.setTimeSinceSeen(getDifference(ZonedDateTime.now(ZoneId.of("UTC")), ZonedDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(timestamp)), ZoneId.of("UTC"))));
            setSessionDurationAndJoinedDateTime(dto);
        }

        log.info("Trip,Nick: {}, \\n Joined: {}, \\n Last seen: {}, \\n Seen active: {} ago, \\n Session duration: {}, \\n Last message: {}", tripOrNick, dto.getJoinedAtRfc1123(), dto.getLastSeenRfc1123(), dto.getTimeSinceSeen(), dto.getSessionDuration(), dto.getLastMessage());
        return "\\n Nick|Trip: " + tripOrNick + "\\n Joined: " + dto.getJoinedAtRfc1123() + "\\n Last seen: " + dto.getLastSeenRfc1123() + "\\n Seen active: " + dto.getTimeSinceSeen() + " ago." + "\\n Session duration: " + dto.getSessionDuration() + " \\n Last message: " + dto.getLastMessage() + "\\n";
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
            ZonedDateTime joined = ZonedDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(joinedAt)), utc);

            dto.setSessionDuration(getDifference(now, joined));
            dto.setJoinedAtRfc1123(DateUtil.formatRfc1123(Long.parseLong(joinedAt), TimeUnit.MILLISECONDS, utc.toString()));
        }
    }
}
