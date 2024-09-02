package org.saturn.app.model.command;

import org.saturn.app.model.Role;

import java.util.List;

public interface UserCommand {
    List<String> getAliases();
    List<String> getArguments();

    List<String> getAuthorizedTrips();

    Role getAuthorizedRole();
    boolean isWhisper();
    void execute();
}
