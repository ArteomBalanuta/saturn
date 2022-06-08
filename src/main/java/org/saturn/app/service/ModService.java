package org.saturn.app.service;

import org.saturn.app.model.impl.User;

public interface ModService {
    void kick(String target);
    
    void ban(String target);
    void unban(String target);
    
    boolean isBanned(User user);
}
