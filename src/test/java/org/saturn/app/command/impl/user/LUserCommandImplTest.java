package org.saturn.app.command.impl.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.AgentCapability;
import org.saturn.app.agent.AgentInvocation;
import org.saturn.app.agent.AgentInvocationMode;
import org.saturn.app.command.UserCommand;
import org.saturn.app.facade.Base;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.AgentService;
import org.saturn.app.service.AuthorizationService;
import org.saturn.app.support.TestSupport;

class LUserCommandImplTest {
  @Test
  void rejectsMissingPromptWithUsage() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*l", "alice", "trip-a");
    var command = new LUserCommandImpl(engine, message, List.of("l"));

    assertEquals(Status.FAILED, command.execute().orElseThrow());
    assertEquals("@alice Example: *l <prompt/question>", engine.outgoingMessageQueue.poll());
    engine.stop();
  }

  @Test
  void submitsPromptAndRoomContextToAgentService() {
    var engine = TestSupport.engine();
    AtomicReference<AgentInvocation> submitted = installRecordingAgent(engine);
    installRoleResolver(engine, Role.REGULAR);
    engine.currentChannelUsers.add(TestSupport.user("bob", "trip-b", "hash-b"));
    var message = TestSupport.chatMessage("*l how many users?", "alice", "trip-a");
    message.setWhisper(true);
    var command = new LUserCommandImpl(engine, message, List.of("l"));

    assertEquals(Status.SUCCESSFUL, command.execute().orElseThrow());

    AgentInvocation invocation = submitted.get();
    assertEquals("how many users?", invocation.prompt());
    assertEquals(AgentInvocationMode.DIRECT, invocation.mode());
    assertEquals("programming", invocation.context().room());
    assertEquals("trip-a", invocation.context().trip());
    assertEquals(List.of("bob"), invocation.context().roomUsers());
    assertTrue(invocation.context().whisper());
    assertFalse(invocation.context().hasCapability(AgentCapability.DYNAMIC_SQL));
    engine.stop();
  }

  @Test
  void grantsDynamicSqlToConfiguredAdminTrip() {
    var engine = TestSupport.engine();
    engine.adminTrips = "trip-a,trip-b";
    AtomicReference<AgentInvocation> submitted = installRecordingAgent(engine);
    var command =
        new LUserCommandImpl(
            engine,
            TestSupport.chatMessage("*l count all messages", "alice", "trip-a"),
            List.of("l"));

    assertEquals(Status.SUCCESSFUL, command.execute().orElseThrow());
    assertTrue(submitted.get().context().hasCapability(AgentCapability.DYNAMIC_SQL));
    engine.stop();
  }

  @Test
  void grantsDynamicSqlToPersistedDatabaseAdmin() {
    var engine = TestSupport.engine();
    AtomicReference<AgentInvocation> submitted = installRecordingAgent(engine);
    installRoleResolver(engine, Role.ADMIN);
    var command =
        new LUserCommandImpl(
            engine,
            TestSupport.chatMessage("*l count all messages", "alice", "trip-a"),
            List.of("l"));

    assertEquals(Status.SUCCESSFUL, command.execute().orElseThrow());
    assertTrue(submitted.get().context().hasCapability(AgentCapability.DYNAMIC_SQL));
    engine.stop();
  }

  @Test
  void grantsDirectModerationAndPermanentBanOnlyToConfiguredCreatorTrip() {
    var engine = TestSupport.engine();
    AtomicReference<AgentInvocation> submitted = installRecordingAgent(engine);
    var command =
        new LUserCommandImpl(
            engine, TestSupport.chatMessage("*l kick the spammer", "merc", "595754"), List.of("l"));

    assertEquals(Status.SUCCESSFUL, command.execute().orElseThrow());
    assertTrue(submitted.get().context().hasCapability(AgentCapability.MODERATION_COMMANDS));
    assertTrue(submitted.get().context().hasCapability(AgentCapability.PERMANENT_BAN));
    engine.stop();
  }

  private AtomicReference<AgentInvocation> installRecordingAgent(EngineImpl engine) {
    engine.getAgentService().close();
    AtomicReference<AgentInvocation> submitted = new AtomicReference<>();
    AgentService recordingService =
        new AgentService() {
          @Override
          public boolean submit(AgentInvocation invocation) {
            submitted.set(invocation);
            return true;
          }

          @Override
          public void close() {}
        };
    TestSupport.setField(engine, Base.class, "agentService", recordingService);
    return submitted;
  }

  private void installRoleResolver(EngineImpl engine, Role role) {
    AuthorizationService authorizationService =
        new AuthorizationService() {
          @Override
          public boolean isUserAuthorized(UserCommand userCommand, ChatMessage chatMessage) {
            return true;
          }

          @Override
          public void grant(String trip, Role grantedRole) {}

          @Override
          public Role resolveRole(String trip) {
            return role;
          }
        };
    TestSupport.setField(engine, Base.class, "authorizationService", authorizationService);
  }
}
