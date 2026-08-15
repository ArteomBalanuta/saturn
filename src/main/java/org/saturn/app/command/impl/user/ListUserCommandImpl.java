package org.saturn.app.command.impl.user;

import static org.saturn.app.util.Util.getAdminAndUserTrips;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.JoinChannelListener;
import org.saturn.app.listener.impl.ListCommandListenerImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.JoinChannelListenerDto;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;

@Slf4j
@CommandAliases(aliases = {"list"})
public class ListUserCommandImpl extends UserCommandBaseImpl {
  public ListUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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
    if (arguments.isEmpty()) {
      printUsers(author(), engine.currentChannelUsers, isWhisper());
      replyToAuthor("Example: %slist programming".formatted(engine.prefix));
      log.info("Executed [list] command by user: {} - missing channel, listed current room", author());
      return Optional.of(Status.FAILED);
    }

    String channel = arguments.getFirst().trim();
    if (channel.isBlank() || channel.equals(engine.channel)) {
      printUsers(author(), engine.currentChannelUsers, isWhisper());
      log.info("Executed [list] command by user: {}, channel: {}", author(), engine.channel);
      return successful();
    }

    joinChannel(channel);
    log.info("Executed [list] command by user: {}, channel: {}", author(), channel);
    return successful();
  }

  public void joinChannel(String channel) {
    EngineImpl slaveEngine = new EngineImpl(null, super.engine.getConfig(), EngineType.LIST_CMD);
    setupEngine(channel, slaveEngine);

    JoinChannelListener onlineSetListener =
        new ListCommandListenerImpl(
            new JoinChannelListenerDto(this.engine, slaveEngine, author(), channel));
    onlineSetListener.setChatMessage(chatMessage);

    slaveEngine.setOnlineSetListener(onlineSetListener);
    slaveEngine.start();
  }

  public void printUsers(String author, List<User> users, boolean isWhisper) {
    Set<User> unique = new HashSet<>(users);
    StringBuilder output = new StringBuilder();

    unique.stream()
        .sorted(Comparator.comparing(User::getHash))
        .forEach(
            user ->
                output
                    .append(user.getHash())
                    .append(" - ")
                    .append(
                        user.getTrip() == null || Objects.equals(user.getTrip(), "")
                            ? "------"
                            : user.getTrip())
                    .append(" - ")
                    .append(user.getNick())
                    .append("\\n"));

    engine.outService.enqueueMessageForSending(author, "\\nUsers online: \\n%s\\n".formatted(output), isWhisper);
  }
}
