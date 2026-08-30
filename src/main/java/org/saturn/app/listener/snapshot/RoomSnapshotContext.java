package org.saturn.app.listener.snapshot;

import java.util.Objects;
import java.util.function.Consumer;

/** Narrow capabilities granted to one snapshot operation. */
public record RoomSnapshotContext(
    String workflowId,
    String author,
    String sourceChannel,
    String targetChannel,
    String destinationChannel,
    Consumer<String> reply,
    Consumer<String> sendRaw) {
  public RoomSnapshotContext {
    Objects.requireNonNull(workflowId, "workflowId");
    Objects.requireNonNull(author, "author");
    Objects.requireNonNull(sourceChannel, "sourceChannel");
    Objects.requireNonNull(targetChannel, "targetChannel");
    Objects.requireNonNull(reply, "reply");
    Objects.requireNonNull(sendRaw, "sendRaw");
  }
}
