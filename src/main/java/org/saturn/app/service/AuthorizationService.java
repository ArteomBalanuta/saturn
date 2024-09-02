package org.saturn.app.service;

import org.saturn.app.model.command.UserCommand;
import org.saturn.app.model.dto.payload.ChatMessage;

public interface AuthorizationService {
    boolean isUserAuthorized(UserCommand userCommand, ChatMessage chatMessage);
}
