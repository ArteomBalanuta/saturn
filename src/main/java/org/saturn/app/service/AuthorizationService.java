package org.saturn.app.service;

import org.saturn.app.model.Role;
import org.saturn.app.model.command.UserCommand;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.Optional;

public interface AuthorizationService {
    boolean isUserAuthorized(UserCommand userCommand, ChatMessage chatMessage);

    boolean grant(String trip, Role role);
}
