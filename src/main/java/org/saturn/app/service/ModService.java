package org.saturn.app.service;

import org.saturn.app.model.dto.BanDto;
import org.saturn.app.model.dto.User;


public interface ModService {
    void kick(String target);

    void overflow(String target);
    void shadowBan(BanDto banDto);

    void ban(String target);

    void unban(String target);

    void lock();
    void unlock();

    default void shadowBan(BanDto... args){
        for (BanDto arg : args) {
            this.shadowBan(arg);
        }
    }

    void enableCaptcha();

    void disableCaptcha();

    void unshadowban(String target);
    void listBanned(String author);

    void unbanAll(String author);

    boolean isBanned(User user);
}
