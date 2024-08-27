package org.saturn.app.service.impl;

import org.saturn.app.service.UserService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.apache.commons.text.StringEscapeUtils.escapeJson;
import static org.saturn.app.util.DateUtil.formatRfc1123;

public class UserServiceImpl extends OutService implements UserService {
    private final Connection connection;

    public UserServiceImpl(Connection connection, BlockingQueue<String> queue) {
        super(queue);
        this.connection = connection;
    }

    @Override
    public String lastOnline(String tripOrNick) {
        String lastMessage = null;
        String sendAt = null;
        try {
            PreparedStatement statement = connection.prepareStatement("SELECT message,created_on FROM messages WHERE nick = ? or trip = ?");
            statement.setString(1, tripOrNick);
            statement.setString(2, tripOrNick);
            statement.execute();

            ResultSet resultSet = statement.getResultSet();
            while (resultSet.next()) {
                sendAt = resultSet.getString("created_on");
                lastMessage = resultSet.getString("message");
            }

            statement.close();
            resultSet.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (sendAt == null) {
            sendAt = "never";
        } else {
            sendAt = formatRfc1123(Long.parseLong(sendAt), TimeUnit.SECONDS, "UTC");
        }

        if (lastMessage == null) {
            lastMessage = " - ";
        } else {
            lastMessage = escapeJson(lastMessage);
        }

        return " nick or trip: " + tripOrNick + "\\n last seen: " + sendAt + " \\n " + "last message: " + lastMessage + "\\n";
    }
}
