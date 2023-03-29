package org.saturn.app.service;

import org.saturn.app.model.command.UserCommand;
import org.saturn.app.model.dto.Mail;

import java.util.List;

public interface MailService {
    void executeMail(String owner, UserCommand cmd);
    
    void orderMessageDelivery(String message, String owner, String receiver);
    List<Mail> getMailByNick(String nick);
    void updateMailStatus(String nick);
}
