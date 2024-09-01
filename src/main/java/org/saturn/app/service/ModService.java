package org.saturn.app.service;

import org.saturn.app.model.dto.User;


public interface ModService {
    void kick(String target);

    void overflow(String target);
    void shadowBan(String target);

    void ban(String target);

    void lock();
    void unlock();

    default void shadowBan(String... args){
        for (String arg : args) {
            this.shadowBan(arg);
        }
    }

    void enableCaptcha();

    void disableCaptcha();

    void unban(String target);
    void listBanned(String author);
    void vote(String author);
    void votekick(String nick);

    void unbanAll();

    boolean isBanned(User user);
}
