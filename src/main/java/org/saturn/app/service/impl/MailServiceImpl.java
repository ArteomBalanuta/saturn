package org.saturn.app.service.impl;

import org.saturn.app.model.Command;
import org.saturn.app.model.impl.Mail;
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
    private Connection connection;
    
    public MailServiceImpl(Connection connection, BlockingQueue<String> outQueue) {
        super(outQueue);
        this.connection = connection;
    }
    
    @Override
    public void executeMail(String owner, Command cmd) {
        String[] args = cmd.getArguments().toArray(new String[0]);
        String receiver = args[0];
        
        StringBuilder message = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            message.append(args[i]).append(" ");
        }
        
        this.orderMessageDelivery(message.toString(), owner, receiver);
        enqueueMessageForSending("@" + owner + ",  " + receiver + " will receive your message as soon they chats");
    }
    
    @Override
    public void orderMessageDelivery(String message, String owner, String receiver) {
        try {
            PreparedStatement insertMessage = connection.prepareStatement(
                    "INSERT INTO mail ('owner','receiver','message','status','created_date') VALUES (?, ?, ?, ?, ?);");
            insertMessage.setString(1, owner);
            insertMessage.setString(2, receiver);
            insertMessage.setString(3, message);
            insertMessage.setString(4, "PENDING");
            insertMessage.setLong(5, Util.getTimestampNow());
            
            insertMessage.executeUpdate();
            
            insertMessage.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        /*
         * parse :msg @receiver messagebodyhere
         *
         * on user message - if user has messages to receive bot enques the message and
         * removes the pendind status
         */
    }
    
    @Override
    public List<Mail> getMailByNick(String nick) {
        List<Mail> messages = new ArrayList<>();
        try {
            PreparedStatement mail = connection.prepareStatement(
                    "SELECT owner, receiver, message, status, created_date FROM mail WHERE receiver = ? AND status = " +
                            "'PENDING'; ");
            mail.setString(1, nick);
            mail.execute();
            
            ResultSet resultSet = mail.getResultSet();
            while (resultSet.next()) {
                Mail message = new Mail(resultSet.getString("owner"), resultSet.getString("receiver"),
                        resultSet.getString("message"), resultSet.getString("status"),
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
