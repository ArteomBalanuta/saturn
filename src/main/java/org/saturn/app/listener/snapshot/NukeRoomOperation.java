package org.saturn.app.listener.snapshot;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.saturn.app.model.dto.User;
import org.saturn.app.util.IdentityUtil;
import org.saturn.app.util.JsonPayloads;

/** Applies nuke moderation actions to the immutable snapshot through the session capability. */
public final class NukeRoomOperation implements RoomSnapshotOperation {
  private final long delayMillis;

  public NukeRoomOperation() {
    this(200);
  }

  public NukeRoomOperation(long delayMillis) {
    this.delayMillis = delayMillis;
  }

  @Override
  public OperationResult apply(RoomSnapshotContext context, OnlineSetSnapshot snapshot) {
    List<String> nicks = snapshot.users().stream().map(User::getNick).toList();
    boolean failed = false;
    for (String nick : nicks) {
      try {
        context
            .sendRaw()
            .accept(JsonPayloads.command("ban", "nick", IdentityUtil.normalizeNickTarget(nick)));
      } catch (RuntimeException e) {
        failed = true;
      }
      if (delayMillis > 0) {
        try {
          TimeUnit.MILLISECONDS.sleep(delayMillis);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          failed = true;
        }
      }
    }
    try {
      context.sendRaw().accept(JsonPayloads.command("lockroom"));
    } catch (RuntimeException e) {
      failed = true;
    }
    return failed
        ? OperationResult.failed("Failed to nuke " + context.targetChannel())
        : OperationResult.success();
  }
}
