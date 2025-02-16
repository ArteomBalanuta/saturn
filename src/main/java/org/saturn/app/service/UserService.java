package org.saturn.app.service;

import java.util.List;
import org.saturn.app.model.dto.Message;

public interface UserService {
  String lastOnline(String tripOrNick);

  int register(String name, String trip, String role);

  boolean isNameRegistered(String name);

  boolean isTripRegistered(String trip);

  void registerNameByTrip(String name, String trip);

  void registerTripByName(String name, String trip);

  List<Message> lastMessages(String name, String trip, int count);
}
