package org.saturn.app.service;

import org.saturn.app.model.dto.User;

public interface ModService {
    void kick(String target);
    
    void ban(String target);
    default void ban(String... args){
        for (String arg : args) {
            this.ban(arg);
        }
    }
    
    void unban(String target);
    void listBanned();
    void vote(String author);
    void votekick(String nick);
    boolean isBanned(User user);
}
