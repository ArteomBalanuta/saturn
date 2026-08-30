package org.saturn.app.listener.snapshot;

import java.util.Objects;
import java.util.stream.Collectors;
import org.saturn.app.model.dto.User;

public final class ListRoomSnapshotOperation implements RoomSnapshotOperation {
  @Override
  public OperationResult apply(RoomSnapshotContext context, OnlineSetSnapshot snapshot) {
    if (snapshot.users().stream().allMatch(User::isIsMe)) {
      return OperationResult.empty(" " + context.targetChannel() + " is empty");
    }
    String users =
        snapshot.users().stream()
            .map(
                user ->
                    user.getHash()
                        + " - "
                        + (user.getTrip() == null || Objects.equals(user.getTrip(), "")
                            ? "------"
                            : user.getTrip())
                        + " - "
                        + user.getNick()
                        + "\\n")
            .collect(Collectors.joining());
    return OperationResult.success("\\nUsers online: \\n" + users + "\\n");
  }
}
