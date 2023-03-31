package org.saturn.app.service;

import org.saturn.app.model.command.UserCommand;
import org.saturn.app.model.dto.ChatMessage;
import org.saturn.app.model.dto.Mail;

import java.util.List;

public interface MailService {
    void executeMail(ChatMessage chatMessage, UserCommand command);
    
    void orderMessageDelivery(String message, String owner, String receiver);
    List<Mail> getMailByNick(String nick);
    void updateMailStatus(String nick);
}
