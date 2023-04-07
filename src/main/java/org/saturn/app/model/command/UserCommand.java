package org.saturn.app.model.command;

import java.util.List;

public interface UserCommand {
    List<String> getAliases();
    List<String> getArguments();

    List<String> getWhiteTrips();

    void execute();
}
