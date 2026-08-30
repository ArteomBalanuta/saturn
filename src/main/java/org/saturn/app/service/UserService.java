package org.saturn.app.service;

import java.util.List;
import java.util.Optional;
import org.saturn.app.model.dto.Message;
import org.saturn.app.model.dto.User;

public interface UserService {

  record RegisteredIdentity(String name, String trip) {}

  Optional<String> isSeenRecently(User user);

  String lastOnline(String tripOrNick);

  int delete(String name, String trip);

  Optional<RegisteredIdentity> resolveRegisteredIdentity(String nameOrTrip);

  int deleteByNameOrTrip(String nameOrTrip);

  int register(String name, String trip, String role);

  boolean isNameRegistered(String name);

  boolean isTripRegistered(String trip);

  void registerNameByTrip(String name, String trip);

  void registerTripByName(String name, String trip);

  List<Message> lastMessages(String name, String trip, int count);

  List<String> getNicksByTrip(String trip);
}
