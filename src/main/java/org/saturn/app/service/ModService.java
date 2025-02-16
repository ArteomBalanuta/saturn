package org.saturn.app.service;

import org.saturn.app.model.dto.BanRecord;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.Optional;


public interface ModService {
    void kick(String target);

    void kickTo(String target, String channel);

    void overflow(String target);
    void shadowBan(BanRecord banDto);
    void ban(String target);
    void unban(String target);
    void lock();
    void unlock();
    void enableCaptcha();

    void auth(String trip);

    void deauth(String trip);

    void disableCaptcha();
    void unshadowBan(String target);
    void listBanned(ChatMessage chatMessage);
    void unbanAll(String author);
    Optional<BanRecord> isShadowBanned(User user);
}
