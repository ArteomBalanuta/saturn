package org.saturn.app.command.impl.moderator;

import static org.saturn.app.util.Util.getAdminTrips;

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
  public LastMessagesCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminTrips(engine));
    super.setAliases(aliases);
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.MODERATOR;
  }

  @Override
  public Optional<Status> execute() {
    List<String> arguments = getArguments();
    if (arguments.size() < 2) {
      logMissingArguments();
      return failWithUsage("lastmessages g0KY09 3");
    }

    String trip = arguments.get(0);
    Optional<Integer> requestedCount = parseRequestedCount(arguments.get(1));
    if (requestedCount.isEmpty()) {
      logMissingArguments();
      return failWithUsage("lastmessages g0KY09 3");
    }

    int numberOfMessages = clampMessageCount(requestedCount.get());
    List<Message> messages = engine.userService.lastMessages(null, trip, numberOfMessages);
    String payload = formatLastMessages(messages);
    replyToAuthor(StringEscapeUtils.escapeJava(payload));

    log.info("Executed [lastmessages] command by user: {}, target: {}", author(), trip);
    return successful();
  }

  private String formatLastMessages(List<Message> messages) {
    StringBuilder lastMessages = new StringBuilder();
    messages.forEach(
        message -> {
          String msg;
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

  private void logMissingArguments() {
    log.info(
        "Executed [lastmessages] command by user: {}, trip: {}, no arguments present",
        author(),
        Optional.ofNullable(chatMessage.getTrip()));
  }

  private Optional<Integer> parseRequestedCount(String count) {
    try {
      return Optional.of(Integer.parseInt(count));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  private int clampMessageCount(int requestedCount) {
    if (requestedCount <= 30) {
      return requestedCount;
    }

    replyToAuthor("Retrieving at max 30 messages! ");
    return 30;
  }

  protected static String getFrontCharacters(String message, int length) {
    return message.substring(0, length) + "...";
  }
}
