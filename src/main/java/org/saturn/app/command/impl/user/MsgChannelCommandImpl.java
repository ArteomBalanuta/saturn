package org.saturn.app.command.impl.user;

import static org.saturn.app.util.Util.getAdminAndUserTrips;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.JoinChannelListener;
import org.saturn.app.listener.impl.MsgChannelCommandListenerImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.JoinChannelListenerDto;
import org.saturn.app.model.dto.payload.ChatMessage;

@Slf4j
@CommandAliases(aliases = {"msgchannel", "msgroom"})
public class MsgChannelCommandImpl extends UserCommandBaseImpl {
  public MsgChannelCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminAndUserTrips(engine));
    super.setAliases(aliases);
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.REGULAR;
  }

  @Override
  public Optional<Status> execute() {
    List<String> arguments = getArguments();
    if (arguments.size() < 2) {
      replyToAuthor(" Example: %smsgroom your-room your message".formatted(engine.prefix));
      log.info("Executed [msgchannel] command by user: {} - missing room or message", author());
      return Optional.of(Status.FAILED);
    }

    String room = normalizeRoom(arguments.getFirst());
    if (room.isBlank()) {
      replyToAuthor("Room name cannot be blank.");
      log.info("Executed [msgchannel] command by user: {} - blank room", author());
      return Optional.of(Status.FAILED);
    }

    String message = renderMessage(arguments);
    log.info("Delivering message: {}, room: {}", message, room);

    if (room.equals(engine.channel)) {
      deliverToCurrentRoom(message);
      return successful();
    }

    deliverToRemoteRoom(room, message);
    return successful();
  }

  private String formatMessage(String message) {
    if (message.contains("![](")) {
      return message + "\\n anonymous mail from: ?" + engine.channel;
    }
    return "anonymous mail from: ?" + engine.channel + " message: " + message;
  }

  private String normalizeRoom(String room) {
    return room.replace("?", "").trim();
  }

  private String renderMessage(List<String> arguments) {
    return String.join(" ", arguments.subList(1, arguments.size())).trim();
  }

  private void deliverToCurrentRoom(String message) {
    engine.outService.enqueueMessageForSending(author() + " ", formatMessage(message), isWhisper());
    log.info("Messaging current room: {}", engine.channel);
  }

  private void deliverToRemoteRoom(String room, String message) {
    EngineImpl slaveEngine = new EngineImpl(null, super.engine.getConfig(), EngineType.LIST_CMD);
    setupEngine(room, slaveEngine);

    JoinChannelListener joinChannelListener =
        new MsgChannelCommandListenerImpl(
            new JoinChannelListenerDto(this.engine, slaveEngine, author(), room));

    joinChannelListener.setAction(
        () -> {
          slaveEngine.outService.enqueueMessageForSending("*", formatMessage(message), false);
          slaveEngine.shareMessages();
          replyToAuthor("sent successfully.");
          log.info(
              "Executed [msgchannel] command by user: {}, room: {}, message: {}",
              author(),
              room,
              message);
        });

    slaveEngine.setOnlineSetListener(joinChannelListener);
    slaveEngine.start();
  }
}
