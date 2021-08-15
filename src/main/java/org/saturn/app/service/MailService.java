package org.saturn.app.service;

import java.util.List;

import org.saturn.app.model.impl.Mail;

public interface MailService {

    void orderMessageDelivery(String message, String owner, String receiver);
    List<Mail> getMailByNick(String nick);
    void updateMailStatus(String nick);
}
