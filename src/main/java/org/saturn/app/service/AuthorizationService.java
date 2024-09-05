package org.saturn.app.service;

import org.saturn.app.model.Role;
import org.saturn.app.command.UserCommand;
import org.saturn.app.model.dto.payload.ChatMessage;

public interface AuthorizationService {
    boolean isUserAuthorized(UserCommand userCommand, ChatMessage chatMessage);
    boolean grant(String trip, Role role);
}
