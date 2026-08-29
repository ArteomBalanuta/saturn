package org.saturn.app.command.impl.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;
import org.saturn.app.support.TestSupport;

class ReplicaStatusCommandImplTest {
  @Test
  void executeReportsNoReplicasWhenNoneAreRegistered() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*replicastatus", "admin", "trip");

    var command = new ReplicaStatusCommandImpl(engine, message, List.of("replicastatus", "status"));

    assertEquals(Status.SUCCESSFUL, command.execute().orElseThrow());
    assertEquals(
        "@admin Host room:programming, replicas active: 0 \nServing channels: none",
        engine.outgoingMessageQueue.poll());
  }

  @Test
  void executeReportsActiveReplicaChannels() {
    var engine = TestSupport.engine();
    var loungeReplica = TestSupport.engine();
    loungeReplica.channel = "lounge";
    var musicReplica = TestSupport.engine();
    musicReplica.channel = "music";
    engine.addReplica(loungeReplica);
    engine.addReplica(musicReplica);
    var message = TestSupport.chatMessage("*status", "admin", "trip");

    var command = new ReplicaStatusCommandImpl(engine, message, List.of("replicastatus", "status"));

    assertEquals(Status.SUCCESSFUL, command.execute().orElseThrow());
    String payload = engine.outgoingMessageQueue.poll();
    assertTrue(
        payload.startsWith(
            "@admin Host room:programming, replicas active: 2 \nServing channels: "));
    assertTrue(payload.contains("lounge"));
    assertTrue(payload.contains("music"));
  }
}
