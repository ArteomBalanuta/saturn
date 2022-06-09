package org.saturn.app.service;

import org.saturn.app.model.impl.User;

public interface ModService {
    void kick(String target);
    
    void ban(String target);
    void unban(String target);
    void listBanned();
    void vote(String author);
    void votekick(String nick);
    boolean isBanned(User user);
}
