package org.saturn.app.listener.snapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.saturn.app.model.dto.User;
import org.saturn.app.util.IdentityUtil;

/** Records miner credentials from one typed snapshot. */
public final class MineTripOperation implements RoomSnapshotOperation {
  private final String generatedNick;
  private final String password;
  private final Path output;

  public MineTripOperation(String generatedNick, String password, Path output) {
    this.generatedNick = generatedNick;
    this.password = password;
    this.output = output;
  }

  @Override
  public OperationResult apply(RoomSnapshotContext context, OnlineSetSnapshot snapshot) {
    User joined =
        snapshot.users().stream()
            .filter(user -> IdentityUtil.sameNick(user.getNick(), generatedNick))
            .findFirst()
            .orElse(null);
    if (joined == null) return OperationResult.failed("Didn't join");
    try {
      Files.writeString(
          output,
          "Password: " + password + " trip: " + joined.getTrip() + " \r\n",
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
      return OperationResult.success();
    } catch (IOException e) {
      return OperationResult.failed("Failed to save mined trip");
    }
  }
}
