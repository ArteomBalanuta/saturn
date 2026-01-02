package org.saturn.app.command;

import java.util.List;
import java.util.Optional;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;

public interface UserCommand {
  List<String> getAliases();

  List<String> getArguments();

  List<String> getAuthorizedTrips();

  Role getAuthorizedRole();

  boolean isWhisper();

  Optional<Status> execute();
}
