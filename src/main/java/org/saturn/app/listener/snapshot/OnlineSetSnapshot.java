package org.saturn.app.listener.snapshot;

import java.util.List;
import java.util.Objects;
import org.saturn.app.model.dto.User;

public record OnlineSetSnapshot(List<User> users, boolean agentShape) {
  public OnlineSetSnapshot {
    Objects.requireNonNull(users, "users");
    if (users.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("users cannot contain null values");
    }
    if (users.stream().anyMatch(user -> user.getNick() == null || user.getNick().isBlank())) {
      throw new IllegalArgumentException("users must have a nonblank nick");
    }
    users = List.copyOf(users);
  }
}
