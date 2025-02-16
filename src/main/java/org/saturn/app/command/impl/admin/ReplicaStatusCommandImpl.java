package org.saturn.app.command.impl.admin;

import static org.saturn.app.util.Util.getWhiteListedTrips;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.impl.OutService;

@Slf4j
@CommandAliases(aliases = {"replicastatus", "status"})
public class ReplicaStatusCommandImpl extends UserCommandBaseImpl {
  private final OutService outService;
  private final List<String> aliases = new ArrayList<>();

  public ReplicaStatusCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getWhiteListedTrips(engine));
    super.setAliases(this.getAliases());
    this.outService = super.engine.outService;
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
    return Role.ADMIN;
  }

  @Override
  public Optional<Status> execute() {
    String author = super.chatMessage.getNick();

    String header =
        "Host room:"
            + engine.channel
            + ", replicas active: "
            + engine.replicasMappedByChannel.size()
            + " \\n";
    StringBuilder b = new StringBuilder();

    engine.replicasMappedByChannel.forEach((k, v) -> b.append(k).append(", "));

    String servingChannels = "Serving channels: " + StringUtils.removeEnd(b.toString(), ", ");
    outService.enqueueMessageForSending(author, header + servingChannels, isWhisper());

    log.info("Executed [replicastatus] command by user: {}", author);
    return Optional.of(Status.SUCCESSFUL);
  }
}
