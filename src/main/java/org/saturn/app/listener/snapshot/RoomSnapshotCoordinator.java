package org.saturn.app.listener.snapshot;

public interface RoomSnapshotCoordinator {
  void submit(RoomSnapshotRequest request);

  void onSnapshot(String sessionId, String jsonText);

  void onTransportError(String sessionId, Throwable error);

  void onClosed(String sessionId, int code, String reason);

  void cancel(String workflowId, String reason);
}
