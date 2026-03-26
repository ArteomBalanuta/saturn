package org.saturn.app.command.impl.admin;

import static org.saturn.app.util.Util.getAdminAndUserTrips;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;

@Slf4j
@CommandAliases(aliases = {"replicastatus", "status"})
public class ReplicaStatusCommandImpl extends UserCommandBaseImpl {
  public ReplicaStatusCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminAndUserTrips(engine));
    super.setAliases(aliases);
  }

  @Override
  public Optional<Status> execute() {
    String servingChannels =
        StringUtils.defaultIfBlank(
            String.join(", ", engine.replicasMappedByChannel.keySet()), "none");
    replyToAuthor(
        "Host room:%s, replicas active: %s \\nServing channels: %s"
            .formatted(engine.channel, engine.replicasMappedByChannel.size(), servingChannels));
    log.info("Executed [replicastatus] command by user: {}", author());
    return successful();
  }
}
