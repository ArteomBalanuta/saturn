package org.saturn.app.service;

import org.saturn.app.command.UserCommand;
import org.saturn.app.model.dto.Mail;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.List;

public interface MailService {
    void executeMail(ChatMessage chatMessage, UserCommand command);
    void orderMessageDelivery(String message, String owner, String receiver, String isWhisper);

    List<String> getTripsByNick(String nick);
    List<Mail> getMailByTrip(String trip);

    void updateMailStatus(String id);
}
