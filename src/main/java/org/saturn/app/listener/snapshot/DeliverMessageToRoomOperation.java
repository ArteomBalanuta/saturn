package org.saturn.app.listener.snapshot;

import org.saturn.app.model.dto.User;
import org.saturn.app.util.JsonPayloads;

/** Delivers one already-formatted message to a remote room. */
public final class DeliverMessageToRoomOperation implements RoomSnapshotOperation {
  private final String message;

  public DeliverMessageToRoomOperation(String message) {
    if (message == null) throw new IllegalArgumentException("message cannot be null");
    this.message = message;
  }

  @Override
  public OperationResult apply(RoomSnapshotContext context, OnlineSetSnapshot snapshot) {
    boolean onlyMe = snapshot.users().stream().allMatch(User::isIsMe);
    if (onlyMe) return OperationResult.empty(" " + context.targetChannel() + " is empty");
    context.sendRaw().accept(JsonPayloads.command("chat", "nick", "*", "text", message));
    return OperationResult.success("sent successfully.");
  }
}
