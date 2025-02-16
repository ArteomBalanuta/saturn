package org.saturn.app.command.impl.moderator;

import static org.saturn.app.util.Util.getAdminTrips;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.Message;
import org.saturn.app.model.dto.payload.ChatMessage;

@Slf4j
@CommandAliases(aliases = {"messages", "lastmessages"})
public class LastMessagesCommandImpl extends UserCommandBaseImpl {
  private final List<String> aliases = new ArrayList<>();

  public LastMessagesCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminTrips(engine));
    super.setAliases(this.getAliases());
    this.aliases.addAll(aliases);
  }

  @Override
  public List<String> getAliases() {
    return this.aliases;
  }

  @Override
  public List<String> getArguments() {
    return super.getArguments();
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.MODERATOR;
  }

  public Optional<Status> execute() {
    String author = chatMessage.getNick();
    Optional<String> authorTrip = Optional.ofNullable(chatMessage.getTrip());

    List<String> arguments = getArguments();
    if (arguments.size() < 2) {
      log.info(
          "Executed [lastmessages] command by user: {}, trip: {}, no arguments present",
          author,
          authorTrip);
      engine.outService.enqueueMessageForSending(
          author, "Example: " + engine.prefix + "lastmessages g0KY09 3", isWhisper());
      return Optional.of(Status.FAILED);
    }

    String trip = arguments.get(0);
    String count = arguments.get(1);

    int numberOfMessages;
    try {
      numberOfMessages = Integer.parseInt(count);
    } catch (NumberFormatException e) {
      log.info(
          "Executed [lastmessages] command by user: {}, trip: {}, no arguments present",
          author,
          authorTrip);
      engine.outService.enqueueMessageForSending(
          author, "Example: " + engine.prefix + "lastmessages g0KY09 3", isWhisper());
      return Optional.of(Status.FAILED);
    }

    if (numberOfMessages > 30) {
      engine.outService.enqueueMessageForSending(
          author, "Retrieving at max 30 messages! ", isWhisper());
      numberOfMessages = 30;
    }

    List<Message> messages = engine.userService.lastMessages(null, trip, numberOfMessages);
    String payload = formatLastMessages(messages);
    engine.outService.enqueueMessageForSending(
        author, StringEscapeUtils.escapeJava(payload), isWhisper());

    log.info("Executed [lastmessages] command by user: {}, target: {}", author, trip);
    return Optional.of(Status.SUCCESSFUL);
  }

  private String formatLastMessages(List<Message> messages) {
    StringBuilder lastMessages = new StringBuilder();
    messages.forEach(
        message -> {
          String msg = null;
          /* We print first N characters of the message */
          if (message.message().length() > 200) {
            msg = getFrontCharacters(message.message(), 200);
          } else {
            msg = message.message();
          }
          String body = message.author() + "#" + message.trip() + ": " + msg;
          lastMessages.append("\n").append(body).append("\n");
        });

    return lastMessages.toString();
  }

  protected static String getFrontCharacters(String message, int length) {
    StringBuilder cut = new StringBuilder();
    for (int i = 0; i < length; i++) {
      cut.append(message.charAt(i));
    }

    cut.append("...");
    return cut.toString();
  }
}
