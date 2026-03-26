package org.saturn.app.service;

import java.util.List;
import org.saturn.app.command.UserCommand;
import org.saturn.app.model.dto.Mail;
import org.saturn.app.model.dto.payload.ChatMessage;

public interface MailService {
  void executeMail(ChatMessage chatMessage, UserCommand command);

  void orderMessageDelivery(String message, String owner, String receiver, String isWhisper);

  List<String> getTripsByNickOrTrip(String nick);

  List<Mail> getMailByTrip(String trip);

  void updateMailStatus(String id);
}
