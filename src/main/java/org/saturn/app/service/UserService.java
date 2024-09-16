package org.saturn.app.service;

import java.sql.Connection;
import java.sql.SQLException;

public interface UserService {
    String lastOnline(String tripOrNick);
    int register(String name, String trip, String role);

    boolean isNameRegistered(String name);
    boolean isTripRegistered(String trip);

    void registerNameByTrip(String name, String trip);
    void registerTripByName(String name, String trip);
}
