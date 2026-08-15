package org.saturn.app.command.impl.user;

import static org.saturn.app.util.Util.getAdminAndUserTrips;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;

@Slf4j
@CommandAliases(aliases = {"wsa", "wsayanon", "anonsay"})
public class WhiskeyAnonUserCommandImpl extends UserCommandBaseImpl {
  public WhiskeyAnonUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminAndUserTrips(engine));
    super.setAliases(aliases);
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.REGULAR;
  }

  @Override
  public Optional<Status> execute() {
    String author = author();
    Optional<String> trip = Optional.ofNullable(chatMessage.getTrip());
    boolean isAdmin =
        trip.isPresent() && List.of(engine.adminTrips.split(",")).contains(trip.get());
    String message = renderArguments(isAdmin);

    EngineImpl support = engine.replicasMappedByChannel.get("support");
    support.outService.enqueueMessageForSending(
        "anon_from_hc: " + StringEscapeUtils.escapeJava(message));
    support.shareMessages();

    log.info("Executed [whiskeysayanon] command by user: {}, argument: {}", author, message);

    return successful();
  }
}
