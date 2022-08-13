package org.saturn.app.service;

import org.saturn.app.model.Command;
import org.saturn.app.model.impl.Mail;

import java.util.List;

public interface MailService {
    void executeMail(String owner, Command cmd);
    
    void orderMessageDelivery(String message, String owner, String receiver);
    List<Mail> getMailByNick(String nick);
    void updateMailStatus(String nick);
}
