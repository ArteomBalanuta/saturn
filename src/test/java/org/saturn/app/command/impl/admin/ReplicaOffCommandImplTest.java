package org.saturn.app.command.impl.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;
import org.saturn.app.support.TestSupport;

class ReplicaOffCommandImplTest {
  @Test
  void executeWithoutChannelReturnsFailure() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*replicaoff", "admin", "trip");

    var command = new ReplicaOffCommandImpl(engine, message, List.of("replicaoff"));

    assertEquals(Status.FAILED, command.execute().orElseThrow());
    assertEquals("@admin Example: *replicaoff lounge", engine.outgoingMessageQueue.poll());
  }

  @Test
  void executeRejectsHostChannel() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*replicaoff programming", "admin", "trip");

    var command = new ReplicaOffCommandImpl(engine, message, List.of("replicaoff"));

    assertEquals(Status.FAILED, command.execute().orElseThrow());
    assertEquals(
        "@admin I'm the host bot serving current channel, not a replica.",
        engine.outgoingMessageQueue.poll());
  }

  @Test
  void executeFailsWhenReplicaIsMissing() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*replicaoff lounge", "admin", "trip");

    var command = new ReplicaOffCommandImpl(engine, message, List.of("replicaoff"));

    assertEquals(Status.FAILED, command.execute().orElseThrow());
    assertEquals("@admin No replica in channel: lounge", engine.outgoingMessageQueue.poll());
  }

  @Test
  void executeStopsAndRemovesReplica() {
    var engine = TestSupport.engine();
    var replica = TestSupport.engine();
    replica.channel = "lounge";
    engine.addReplica(replica);
    var message = TestSupport.chatMessage("*replicaoff lounge", "admin", "trip");

    var command = new ReplicaOffCommandImpl(engine, message, List.of("replicaoff"));

    assertEquals(Status.SUCCESSFUL, command.execute().orElseThrow());
    assertEquals(0, engine.replicasMappedByChannel.size());
    assertEquals(
        "@admin Successfully shut down replica in channel: lounge",
        engine.outgoingMessageQueue.poll());
  }
}
