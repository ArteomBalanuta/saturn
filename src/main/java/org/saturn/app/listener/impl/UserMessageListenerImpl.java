package org.saturn.app.listener.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommand;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.Listener;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.List;
import java.util.Optional;

import static org.saturn.app.util.DateUtil.getTimestampNow;
import static org.saturn.app.util.Util.gson;

@Slf4j
public class UserMessageListenerImpl implements Listener {

  private final EngineImpl engine;

  public UserMessageListenerImpl(EngineImpl engine) {
    this.engine = engine;
  }

  @Override
  public String getListenerName() {
    return "messageListener";
  }

  @Override
  public void notify(String jsonText) {
    log.debug("Full message payload: {}", jsonText);
    ChatMessage message = gson.fromJson(jsonText, ChatMessage.class);

    Optional<User> first =
        engine.currentChannelUsers.stream()
            .filter(u -> u.getNick().equals(message.getNick()))
            .findFirst();
    first.ifPresentOrElse(
        user -> message.setHash(user.getHash()),
        () -> {
          log.warn("Hash for user: {}, not present ", message.getNick());
          log.warn(
              "Active users: {}", engine.currentChannelUsers.stream().map(User::getNick).toList());
        });

    engine.logRepository.logMessage(
        message.getTrip(),
        message.getNick(),
        message.getHash(),
        message.getText(),
        this.engine.channel,
        getTimestampNow());

    boolean isBotMessage = engine.nick.equals(message.getNick());
    if (isBotMessage) {
      return;
    }

    log.info(
        "hash: {}, trip: {}, nick: {}, message: {}",
        message.getHash(),
        message.getTrip(),
        message.getNick(),
        message.getText());
    //
    //        if (!isTrustedUser(message.getTrip(), message.getNick(), message.getHash())) {
    //            boolean aboveThreshold = message.getText().length() > 40;
    //            if (aboveThreshold) {
    //                if (message.getTrip() != null) {
    //                    engine.modService.shadowBan(message.getTrip());
    //                }
    //                engine.modService.shadowBan(message.getHash());
    //                engine.modService.ban(message.getNick());
    //                engine.modService.lock();
    //
    //                log.warn("Spam detected by user: {}, banned hash: {}, trip: {}",
    // message.getNick(), message.getHash(), message.getTrip());
    //                engine.outService.enqueueMessageForSending("*", " Banned hash: " +
    // message.getHash() + " trip: " + message.getTrip() + " reason: SPAM",false);
    //            }
    //        }

    /* Mail service check */
    engine.deliverMailIfPresent(message.getNick(), message.getTrip());

    /* Get user dto by message author */
    User user =
        engine.currentChannelUsers.stream()
            .filter(u -> u.getNick().equalsIgnoreCase(message.getNick()))
            .findFirst()
            .get();
    /* Check if user is afk */
    engine.notifyUserNotAfkAnymore(user);

    /* notify mentioned user is afk currently */
    engine.notifyIsAfkIfUserIsMentioned(message.getNick(), message.getText());

    if (message.getText().toLowerCase().contains("has cern ended the universe")) {
      engine.outService.enqueueMessageForSending("no");
    }

    String cmd = message.getText().trim();
    if (!cmd.startsWith(engine.prefix)) {
      return;
    }
    UserCommand userCommand = new UserCommandBaseImpl(message, engine, List.of());
    userCommand.execute();
  }
}
