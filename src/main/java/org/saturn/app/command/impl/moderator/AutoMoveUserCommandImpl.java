package org.saturn.app.command.impl.moderator;

import static org.saturn.app.command.impl.admin.ReplicaCommandImpl.registerReplica;
import static org.saturn.app.util.Util.getAdminTrips;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;

@Slf4j
@CommandAliases(aliases = {"automove"})
public class AutoMoveUserCommandImpl extends UserCommandBaseImpl {
  private static String DESTINATION_CHANNEL = "lounge";
  public static final Set<String> SOURCE_CHANNELS = new HashSet<>();
  private static boolean AUTO_MOVE_STATUS = false;

  public static String getDestinationChannel() {
    return DESTINATION_CHANNEL;
  }

  public static boolean isAutoMoveEnabled() {
    return AUTO_MOVE_STATUS;
  }

  public AutoMoveUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminTrips(engine));
    super.setAliases(aliases);

    /* Default one */
    SOURCE_CHANNELS.add("purgatory");
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.MODERATOR;
  }

  @Override
  public Optional<Status> execute() {
    List<String> arguments = getArguments();
    if (arguments.isEmpty()) {
      return showUsage();
    }

    String firstArgument = arguments.getFirst().trim();
    Optional<Status> status = handleToggle(firstArgument);
    if (status.isPresent()) {
      log.info("Executed [automove] command by user: {}, arguments: {}", author(), firstArgument);
      return status;
    }

    if (arguments.size() == 2) {
      configureChannels(firstArgument, arguments.get(1).trim());
      log.info("Executed [automove] command by user: {}, arguments: {}", author(), arguments);
      return successful();
    }

    return showUsage();
  }

  private Optional<Status> showUsage() {
    replyToAuthor("%sautomove [on|off]".formatted(engine.prefix));
    replyToAuthor(
        "Current status: %s , Source rooms: %s , Destination room: %s"
            .formatted(AUTO_MOVE_STATUS, SOURCE_CHANNELS, DESTINATION_CHANNEL));
    replyToAuthor(
        "To set source: hell, destination: heaven - use: %sautomove hell heaven"
            .formatted(engine.getPrefix()));
    log.info("Executed [automove] command by user: {} - missing required parameters", author());
    return Optional.of(Status.FAILED);
  }

  private Optional<Status> handleToggle(String argument) {
    if ("on".equalsIgnoreCase(argument)) {
      enableAutoMove();
      return successful();
    }

    if ("off".equalsIgnoreCase(argument)) {
      disableAutoMove();
      return successful();
    }

    return Optional.empty();
  }

  private void enableAutoMove() {
    AUTO_MOVE_STATUS = true;
    EngineImpl hostRef = engine.getHostRef();
    if (hostRef != null) {
      ensureReplicasForSourceChannels(hostRef);
    }

    replyToAuthor(" %sautomove is enabled".formatted(engine.prefix));
  }

  private void ensureReplicasForSourceChannels(EngineImpl hostRef) {
    Set<String> replicaChannels = hostRef.replicasMappedByChannel.keySet();
    for (String source : SOURCE_CHANNELS) {
      if (replicaChannels.contains(source)) {
        log.info("Channel: {}, is served by a replica", source);
        continue;
      }

      log.warn("Channel: {}, [IS NOT] server by a replica, launching one automatically.", source);
      replyToAuthor(
          "Channel: %s, [IS NOT] server by a replica, launching one automatically."
              .formatted(source));
      registerReplica(engine, chatMessage, author(), source);
    }
  }

  private void disableAutoMove() {
    AUTO_MOVE_STATUS = false;
    EngineImpl hostRef = engine.getHostRef();
    if (hostRef != null) {
      stopSourceChannelReplicas(hostRef);
    }

    replyToAuthor(" %sautomove is disabled".formatted(engine.prefix));
  }

  private void stopSourceChannelReplicas(EngineImpl hostRef) {
    log.info("Stopping replicas in: {}, channels", SOURCE_CHANNELS);
    for (String channel : SOURCE_CHANNELS) {
      EngineImpl replica = hostRef.replicasMappedByChannel.remove(channel);
      if (replica == null) {
        continue;
      }

      log.info("Stopping replica in channel: {}", channel);
      replica.stop();
    }
  }

  private void configureChannels(String source, String destination) {
    SOURCE_CHANNELS.add(source);
    DESTINATION_CHANNEL = destination;
    replyToAuthor(
        "Set source channel: %s , destination channel: %s. Make sure bot's REPLICA is serving source channels."
            .formatted(SOURCE_CHANNELS, DESTINATION_CHANNEL));
  }
}
