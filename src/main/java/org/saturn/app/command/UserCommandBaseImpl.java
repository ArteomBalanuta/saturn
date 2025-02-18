package org.saturn.app.command;

import static org.saturn.app.util.Util.toLower;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.JoinChannelListener;
import org.saturn.app.listener.impl.KickCommandListenerImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.JoinChannelListenerDto;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.util.DateUtil;

@Slf4j
public class UserCommandBaseImpl implements UserCommand {
  protected final EngineImpl engine;
  protected ChatMessage chatMessage;
  public static String lastKicked;
  public static String kickedTo;
  protected final List<String> authorizedTrips = new ArrayList<>();
  private List<String> aliases;
  private final List<String> arguments = new ArrayList<>();

  public boolean resurrectLastKicked(String thisEngineChannel) {
    return lastKicked != null && kickedTo != null && !kickedTo.equals(thisEngineChannel);
  }

  public UserCommandBaseImpl(
      ChatMessage chatMessage, EngineImpl engine, List<String> authorizedTrips) {
    this.engine = engine;
    this.chatMessage = chatMessage;

    this.authorizedTrips.addAll(authorizedTrips);

    String message = chatMessage.getText().substring(engine.getPrefix().length());
    if (message.contains(" ")) {
      setAliases(List.of(message.substring(0, message.indexOf(" ")).trim().toUpperCase()));
      parseArguments(message);
      return;
    }

    setAliases(List.of(message.trim().toUpperCase()));
  }

  private void parseArguments(String message) {
    String arguments = message.substring(message.indexOf(" ") + 1);
    if (arguments.contains(" ")) {
      this.arguments.addAll(Arrays.asList(arguments.split(" ")));
    } else {
      this.arguments.add(arguments);
    }
  }

  protected void setAliases(List<String> aliases) {
    this.aliases = aliases;
  }

  @Override
  public List<String> getAliases() {
    return this.aliases;
  }

  @Override
  public Optional<Status> execute() {
    List<String> aliases = toLower(this.getAliases());
    Optional<UserCommand> cmd = engine.commandFactory.getCommand(this.chatMessage, aliases.get(0));

    if (cmd.isEmpty()
        || !engine.authorizationService.isUserAuthorized(cmd.get(), this.chatMessage)) {
      return Optional.empty();
    } else {
      setupArguments(cmd.get());

      Optional<Status> executionStatus = cmd.get().execute();
      engine.logRepository.logCommand(
          chatMessage.getTrip(),
          cmd.get().getAliases().toString(),
          cmd.get().getArguments().toString(),
          executionStatus.get().name(),
          this.engine.channel,
          DateUtil.getTimestampNow());
      return executionStatus;
    }
  }

  private void setupArguments(UserCommand cmd) {
    cmd.getArguments().clear();
    cmd.getArguments().addAll(this.arguments);
  }

  @Override
  public List<String> getArguments() {
    String[] array = arguments.toArray(new String[0]);
    if (array.length > 0 && array[0].contains("\\n")) {
      /* split first argument into array */
      String[] fixedReceiver = StringUtils.splitByWholeSeparator(array[0], "\\n");

      /* nullify broken receiver in initial argument array */
      String[] freshArguments = ArrayUtils.remove(array, 0);

      /* reset arguments with fixed arguments */
      return new ArrayList<>(List.of(ArrayUtils.insert(0, freshArguments, fixedReceiver)));
    }

    return arguments;
  }

  @Override
  public List<String> getAuthorizedTrips() {
    return authorizedTrips;
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.ADMIN; /* highest privileged role required by default to run the commands*/
  }

  @Override
  public boolean isWhisper() {
    return chatMessage.isWhisper();
  }

  public void resurrect(String channel, String nick, String targetChannel, EngineImpl slaveEngine) {
    setupEngine(channel, slaveEngine);

    JoinChannelListenerDto dto =
        new JoinChannelListenerDto(this.engine, slaveEngine, slaveEngine.nick, channel);
    dto.target = nick;
    dto.destinationChannel = targetChannel;

    JoinChannelListener onlineSetListener = new KickCommandListenerImpl(dto);
    onlineSetListener.setChatMessage(chatMessage);

    onlineSetListener.setAction(
        () -> {
          slaveEngine.outService.enqueueRawMessageForSending(
              String.format(
                  "{ \"cmd\": \"kick\", \"nick\": \"%s\", \"to\":\"%s\"}", nick, targetChannel));
          slaveEngine.shareMessages();
          log.info("user: {}, has been moved to: {}", nick, targetChannel);
        });

    slaveEngine.setOnlineSetListener(onlineSetListener);
    slaveEngine.start();
  }

  public void setupEngine(String channel, EngineImpl listBot) {
    listBot.setChannel(channel);
    int length = 8;
    boolean useLetters = true;
    boolean useNumbers = true;
    String generatedNick = RandomStringUtils.random(length, useLetters, useNumbers);
    listBot.setNick(generatedNick);
    listBot.setPassword(engine.password);
  }
}
