package org.saturn.app.service;

import org.saturn.app.model.dto.BanDto;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;


public interface ModService {
    void kick(String target);

    void kickTo(String target, String channel);

    void overflow(String target);
    void shadowBan(BanDto banDto);
    void ban(String target);
    void unban(String target);
    void lock();
    void unlock();
    void enableCaptcha();
    void disableCaptcha();
    void unshadowBan(String target);
    void listBanned(ChatMessage chatMessage);
    void unbanAll(String author);
    boolean isBanned(User user);
}
