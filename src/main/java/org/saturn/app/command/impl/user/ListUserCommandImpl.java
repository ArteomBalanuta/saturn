package org.saturn.app.command.impl.user;

import static org.saturn.app.util.Util.getAdminAndUserTrips;

import com.moandjiezana.toml.Toml;
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
import org.saturn.app.service.impl.OutService;

@Slf4j
@CommandAliases(aliases = {"list", "l"})
public class ListUserCommandImpl extends UserCommandBaseImpl {
  private final OutService outService;

  public ListUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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
      printUsers(author, engine.currentChannelUsers, engine.outService, chatMessage.isWhisper());
      outService.enqueueMessageForSending(
          author, "Example: " + engine.prefix + "list programming", isWhisper());
      return Optional.of(Status.FAILED);
    }

    String channel = arguments.get(0).trim();
    if (channel.isBlank() || channel.equals(engine.channel)) {
      /* parse nicks from current channel */
      printUsers(author, engine.currentChannelUsers, engine.outService, chatMessage.isWhisper());
    } else {
      /* ListCommandListenerImpl will make sure to close the connection */
      joinChannel(author, channel);
    }

    log.info("Executed [list] command by user: {}, channel: {}", author, channel);
    return Optional.of(Status.SUCCESSFUL);
  }

  public void joinChannel(String author, String channel) {
    Toml main = super.engine.getConfig();
    EngineImpl slaveEngine =
        new EngineImpl(
            null, main, EngineType.LIST_CMD); // no db connection, nor config for this one is needed
    setupEngine(channel, slaveEngine);

    JoinChannelListener onlineSetListener =
        new ListCommandListenerImpl(
            new JoinChannelListenerDto(this.engine, slaveEngine, author, channel));
    onlineSetListener.setChatMessage(chatMessage);

    slaveEngine.setOnlineSetListener(onlineSetListener);

    slaveEngine.start();
  }

  public void printUsers(
      String author, List<User> users, OutService outService, boolean isWhisper) {
    Set<User> unique = new HashSet<>(users);
    StringBuilder output = new StringBuilder();
    unique.forEach(
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

    outService.enqueueMessageForSending(author, "\\nUsers online: \\n" + output + "\\n", isWhisper);
  }
}
