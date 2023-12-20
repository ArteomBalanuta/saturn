package org.saturn.app.service;

import org.saturn.app.model.dto.User;

public interface ModService {
    void kick(String target);

    void overflow(String target);
    void ban(String target);

    void lock();
    void unlock();

    default void ban(String... args){
        for (String arg : args) {
            this.ban(arg);
        }
    }

    void enableCaptcha();

    void disableCaptcha();

    void unban(String target);
    void listBanned();
    void vote(String author);
    void votekick(String nick);

    void unbanAll();

    boolean isBanned(User user);
}
