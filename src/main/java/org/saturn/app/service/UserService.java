package org.saturn.app.service;

import org.saturn.app.model.dto.Message;
import org.saturn.app.model.dto.User;

import java.util.List;
import java.util.Optional;

public interface UserService {

  Optional<String> isSeenRecently(User user);
  String lastOnline(String tripOrNick);

  int delete(String name, String trip);

  int register(String name, String trip, String role);

  boolean isNameRegistered(String name);

  boolean isTripRegistered(String trip);

  void registerNameByTrip(String name, String trip);

  void registerTripByName(String name, String trip);

  List<Message> lastMessages(String name, String trip, int count);
}
