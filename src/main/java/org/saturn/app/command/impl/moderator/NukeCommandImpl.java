package org.saturn.app.command.impl.moderator;

import static org.saturn.app.util.Util.getAdminTrips;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.snapshot.DefaultRoomSnapshotCoordinator;
import org.saturn.app.listener.snapshot.EngineSnapshotSession;
import org.saturn.app.listener.snapshot.GsonOnlineSetPayloadParser;
import org.saturn.app.listener.snapshot.NukeRoomOperation;
import org.saturn.app.listener.snapshot.RoomSnapshotRequest;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;

@Slf4j
@CommandAliases(
    aliases = {
      "nuke",
    })
public class NukeCommandImpl extends UserCommandBaseImpl {
  public NukeCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminTrips(engine));
    super.setAliases(aliases);
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.MODERATOR;
  }

  @Override
  public Optional<Status> execute() {
    final List<String> arguments =
        getArguments().stream().map(arg -> arg.replace("@", "")).toList();

    final String author = chatMessage.getNick();
    if (arguments.isEmpty()) {
      log.info("Executed [nuke] command by user: {}", author);
      engine.outService.enqueueMessageForSending(
          author, "\\n Example: " + engine.prefix + "nuke hotlinks", isWhisper());
      return Optional.of(Status.FAILED);
    }

    String room = arguments.getFirst();

    String workflowId = UUID.randomUUID().toString();
    DefaultRoomSnapshotCoordinator coordinator =
        new DefaultRoomSnapshotCoordinator(
            (request, sink) ->
                EngineSnapshotSession.create(
                    workflowId,
                    engine.getConfig(),
                    room,
                    "nuke-" + workflowId.substring(0, 8),
                    engine.password,
                    sink),
            (request, reply) ->
                engine.outService.enqueueMessageForSending(author, reply, isWhisper()),
            new GsonOnlineSetPayloadParser(EngineType.REPLICA, workflowId, room));
    coordinator.submit(
        new RoomSnapshotRequest(
            workflowId, author, engine.channel, room, null, chatMessage, new NukeRoomOperation()));
    log.info("Executed [nuke] command by user: {}, room: {}", author, room);
    return Optional.of(Status.SUCCESSFUL);
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
