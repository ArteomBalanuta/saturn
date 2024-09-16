package org.saturn.app.service;

public interface UserService {
    String lastOnline(String tripOrNick);

    String register(String name, String trip, String role);
}
