package org.saturn.app.service.impl;

import org.saturn.app.model.command.UserCommand;
import org.saturn.app.model.dto.ChatMessage;
import org.saturn.app.model.dto.Mail;
import org.saturn.app.service.MailService;
import org.saturn.app.util.Util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

public class MailServiceImpl extends OutService implements MailService {
    private final Connection connection;
    
    public MailServiceImpl(Connection connection, BlockingQueue<String> outQueue) {
        super(outQueue);
        this.connection = connection;
    }
    
    @Override
    public void executeMail(ChatMessage chatMessage, UserCommand command) {
        List<String> arguments = command.getArguments();
        String receiver = arguments.get(0).replace("@", "");
        
        StringBuilder message = new StringBuilder();
        /* skipping fist argument as it is the receiver's nickname */
        for (int i = 1; i < arguments.size(); i++) {
            message.append(arguments.get(i)).append(" ");
        }

        this.orderMessageDelivery(message.toString(), chatMessage.getNick(), receiver, String.valueOf(chatMessage.isWhisper()));
        if (chatMessage.isWhisper()) {
            enqueueMessageForSending("/whisper @" + chatMessage.getNick() + " " + receiver + " will receive your message as soon they chat");
            return;
        }
        enqueueMessageForSending("@" + chatMessage.getNick() + " " + receiver + " will receive your message as soon they chat");

    }

    /**
     * parse :msg @receiver message
     * on user message - if user has messages to read, enqueues the message and
     * removes the pending status
     */
    @Override
    public void orderMessageDelivery(String message, String owner, String receiver, String isWhisper) {
        try {
            PreparedStatement insertMessage = connection.prepareStatement(
                    "INSERT INTO mail ('owner','receiver','message','status','is_whisper', 'created_date') VALUES (?, ?, ?, ?, ?, ?);");
            insertMessage.setString(1, owner);
            insertMessage.setString(2, receiver);
            insertMessage.setString(3, message);
            insertMessage.setString(4, "PENDING");
            insertMessage.setString(5, isWhisper);
            insertMessage.setLong(6, Util.getTimestampNow());
            
            insertMessage.executeUpdate();
            
            insertMessage.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public List<Mail> getMailByNickOrTrip(String nick, String trip) {
        List<Mail> messages = new ArrayList<>();
        try {
            PreparedStatement mail = connection.prepareStatement(
                    "SELECT owner, receiver, message, status, is_whisper, created_date FROM mail WHERE receiver IN (?,?) AND status = " +
                            "'PENDING'; ");
            mail.setString(1, nick);
            mail.setString(2, trip == null ? nick : trip);
            mail.execute();
            
            ResultSet resultSet = mail.getResultSet();
            while (resultSet.next()) {
                Mail message = new Mail(
                        resultSet.getString("owner"),
                        resultSet.getString("receiver"),
                        resultSet.getString("message"),
                        resultSet.getString("status"),
                        resultSet.getString("is_whisper"),
                        resultSet.getLong("created_date"));
                
                messages.add(message);
            }
            mail.close();
            resultSet.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return messages;
    }
    
    @Override
    public void updateMailStatus(String nick) {
        try {
            PreparedStatement insertMessage = connection.prepareStatement(
                    "UPDATE mail SET status='DELIVERED' WHERE receiver = ?");
            insertMessage.setString(1, nick);
            
            insertMessage.executeUpdate();
            
            insertMessage.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
