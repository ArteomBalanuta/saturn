package org.saturn.app.service;

import java.sql.SQLException;

public interface UserService {
    String lastOnline(String tripOrNick);
    int register(String name, String trip, String role);
}
