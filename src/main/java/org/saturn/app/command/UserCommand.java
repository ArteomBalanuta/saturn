package org.saturn.app.command;

import org.saturn.app.model.Role;
import org.saturn.app.model.Status;

import java.util.List;
import java.util.Optional;

public interface UserCommand {
    List<String> getAliases();
    List<String> getArguments();

    List<String> getAuthorizedTrips();

    Role getAuthorizedRole();
    boolean isWhisper();
    Optional<Status> execute();
}
