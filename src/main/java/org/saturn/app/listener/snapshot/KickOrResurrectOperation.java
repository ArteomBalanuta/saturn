package org.saturn.app.listener.snapshot;

import org.saturn.app.util.IdentityUtil;
import org.saturn.app.util.JsonPayloads;

/** Performs a kick/move after a typed room snapshot. */
public final class KickOrResurrectOperation implements RoomSnapshotOperation {
  private final String target;

  public KickOrResurrectOperation(String target) {
    this.target = IdentityUtil.normalizeNickTarget(target);
  }

  @Override
  public OperationResult apply(RoomSnapshotContext context, OnlineSetSnapshot snapshot) {
    boolean present =
        snapshot.users().stream().anyMatch(u -> IdentityUtil.sameNick(u.getNick(), target));
    if (!present) return OperationResult.absent(" " + target + " isn't in the room");
    context
        .sendRaw()
        .accept(JsonPayloads.command("kick", "nick", target, "to", context.destinationChannel()));
    return OperationResult.success();
  }
}
