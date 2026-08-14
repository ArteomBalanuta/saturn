package org.saturn.app.command.impl.admin;

import static org.saturn.app.util.Util.getAdminAndUserTrips;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;

@Slf4j
@CommandAliases(aliases = {"prefix"})
public class PrefixCommandImpl extends UserCommandBaseImpl {
  public PrefixCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminAndUserTrips(engine));
    super.setAliases(aliases);
  }

  @Override
  public Optional<Status> execute() {
    Optional<String> nextPrefix = firstArgument();
    if (nextPrefix.isEmpty() || nextPrefix.get().isBlank()) {
      return failWithUsage("prefix $");
    }

    String previousPrefix = engine.prefix;
    String newPrefix = nextPrefix.get().trim();
    applyPrefix(engine, newPrefix);
    engine.replicasMappedByChannel.values().forEach(replica -> applyPrefix(replica, newPrefix));

    replyToAuthor("prefix changed from %s to %s".formatted(previousPrefix, newPrefix));
    log.info("Executed [prefix] command by user: {}, prefix: {} -> {}", author(), previousPrefix, newPrefix);
    return successful();
  }

  private void applyPrefix(EngineImpl engine, String prefix) {
    engine.prefix = prefix;
  }
}
