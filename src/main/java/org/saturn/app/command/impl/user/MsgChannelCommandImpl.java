package org.saturn.app.command.impl.user;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
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
import org.saturn.app.service.impl.OutService;

import java.util.List;
import java.util.Optional;

import static org.saturn.app.util.Util.getAdminAndUserTrips;

@Slf4j
@CommandAliases(aliases = {"msgchannel", "msgroom"})
public class MsgChannelCommandImpl extends UserCommandBaseImpl {
  private final OutService outService;
  public MsgChannelCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminAndUserTrips(engine));
    super.setAliases(aliases);
    this.outService = super.engine.outService;
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.REGULAR;
  }

  @Override
  public Optional<Status> execute() {
    String author = super.chatMessage.getNick();

    List<String> arguments = this.getArguments();
    if (arguments.isEmpty()) {
      outService.enqueueMessageForSending(
          author, " Example: " + engine.prefix + "msgroom your-room 1984", isWhisper());
      log.info("Executed [msgchannel] command buy user: {}", author);
      return Optional.of(Status.FAILED);
    }

    StringBuilder message = new StringBuilder();
    for (int i = 1; i < arguments.size(); i++) {
      message.append(' ').append(arguments.get(i));
    }

    String room = arguments.getFirst().replace("?", "");
    log.info("Delivering Message: {}, Room: {}", message, room);

    if (room.equals(engine.channel)) {
      /* msg current room */
      outService.enqueueMessageForSending(
          author + " ", formatMessage(message.toString()), isWhisper());
      log.info("Messaging current room: {}", room);
    } else {
      /* JoinChannelListener will make sure to close the connection */
      EngineImpl slaveEngine =
          new EngineImpl(
              null,
              super.engine.getConfig(),
              EngineType.LIST_CMD); // no db connection, nor config for this one is required
      setupListBot(room, slaveEngine);

      JoinChannelListener joinChannelListener =
          new MsgChannelCommandListenerImpl(
              new JoinChannelListenerDto(this.engine, slaveEngine, author, room));

      joinChannelListener.setAction(
          () -> {
            slaveEngine.outService.enqueueMessageForSending(
                "*", formatMessage(message.toString()), false);
            slaveEngine.shareMessages();
            outService.enqueueMessageForSending(author, "sent successfully.", isWhisper());
            log.info(
                "Executed [msgchannel] command by user: {}, room: {}, message: {}",
                author,
                room,
                message);
          });

      slaveEngine.setOnlineSetListener(joinChannelListener);
      slaveEngine.start();
    }

    return Optional.of(Status.SUCCESSFUL);
  }

  private String formatMessage(String message) {
    if (message.contains("![](")) {
      return message + "\\n anonymous mail from: ?" + engine.channel;
    }
    return "anonymous mail from: ?" + engine.channel + " message: " + message;
  }

  private void setupListBot(String channel, EngineImpl listBot) {
    listBot.setChannel(channel);
    int length = 8;
    boolean useLetters = true;
    boolean useNumbers = true;
    String generatedNick = RandomStringUtils.random(length, useLetters, useNumbers);
    listBot.setNick(generatedNick);
    listBot.setPassword(engine.password);
  }
}
