package org.saturn.app.listener.snapshot;

import java.util.Objects;
import org.saturn.app.model.dto.payload.ChatMessage;

public record RoomSnapshotRequest(
    String workflowId,
    String author,
    String sourceChannel,
    String targetChannel,
    String destinationChannel,
    ChatMessage replyMessage,
    RoomSnapshotOperation operation) {
  public RoomSnapshotRequest {
    requireText(workflowId, "workflowId");
    requireText(author, "author");
    requireText(sourceChannel, "sourceChannel");
    requireText(targetChannel, "targetChannel");
    Objects.requireNonNull(operation, "operation");
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(name + " cannot be blank");
  }
}
