package org.saturn.app.listener.snapshot;

public interface RoomSnapshotOperation {
  OperationResult apply(RoomSnapshotContext context, OnlineSetSnapshot snapshot) throws Exception;
}
