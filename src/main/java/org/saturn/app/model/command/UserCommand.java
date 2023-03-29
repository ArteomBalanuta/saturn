package org.saturn.app.model.command;

import org.saturn.app.model.dto.ChatMessage;

import java.util.List;

public interface UserCommand {
    String getCommandName();
    List<String> getArguments();

    List<String> getWhiteTrips();

    void setChatMessage(ChatMessage message);
    void execute();
}
