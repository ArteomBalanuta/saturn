package org.saturn.app.agent.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moandjiezana.toml.Toml;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.api.AgentCapability;
import org.saturn.app.agent.api.AgentInvocation;
import org.saturn.app.agent.api.AgentInvocationMode;
import org.saturn.app.agent.api.AgentParticipationConfig;
import org.saturn.app.command.UserCommand;
import org.saturn.app.facade.Base;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.AuthorizationService;
import org.saturn.app.support.TestSupport;

class AgentInvocationFactoryTest {
  @Test
  void buildsRoomContextFromTrustedMessageAndEngineState() {
    var engine = TestSupport.engine();
    installRoleResolver(engine, Role.REGULAR);
    engine.currentChannelUsers.add(TestSupport.user("bob", "trip-b", "hash-b"));
    var message = TestSupport.chatMessage("hello", "alice", "trip-a");
    message.setHash("hash-a");
    message.setWhisper(true);
    AgentInvocationFactory factory =
        new AgentInvocationFactory(AgentParticipationConfig.from(new Toml()));

    AgentInvocation invocation =
        factory.create(engine, message, "question", AgentInvocationMode.MENTION);

    assertEquals(AgentInvocationMode.MENTION, invocation.mode());
    assertEquals("alice", invocation.context().nick());
    assertEquals("trip-a", invocation.context().trip());
    assertEquals("hash-a", invocation.context().hash());
    assertEquals(java.util.List.of("bob"), invocation.context().roomUsers());
    assertTrue(invocation.context().whisper());
    assertEquals("hello", invocation.currentMessageText());
    assertFalse(invocation.commandOriginated());
    assertTrue(invocation.context().capabilities().isEmpty());
    engine.stop();
  }

  @Test
  void retainsTheEscapedInboundTextUsedForRoomContextExclusion() {
    var engine = TestSupport.engine();
    installRoleResolver(engine, Role.REGULAR);
    AgentInvocationFactory factory =
        new AgentInvocationFactory(AgentParticipationConfig.from(new Toml()));

    AgentInvocation invocation =
        factory.create(
            engine,
            TestSupport.chatMessage("*l first line\nsecond line", "alice", "trip-a"),
            "first line second line",
            AgentInvocationMode.DIRECT);

    assertEquals("*l first line\\nsecond line", invocation.currentMessageText());
    engine.stop();
  }

  @Test
  void propagatesExplicitCommandOriginWithoutChangingContext() {
    var engine = TestSupport.engine();
    installRoleResolver(engine, Role.REGULAR);
    AgentInvocationFactory factory =
        new AgentInvocationFactory(AgentParticipationConfig.from(new Toml()));

    AgentInvocation invocation =
        factory.create(
            engine,
            TestSupport.chatMessage("*l answer", "alice", "trip-a"),
            "answer",
            AgentInvocationMode.DIRECT,
            true);

    assertTrue(invocation.commandOriginated());
    assertEquals("*l answer", invocation.currentMessageText());
    assertEquals(AgentInvocationMode.DIRECT, invocation.mode());
    engine.stop();
  }

  @Test
  void grantsTrustedCreatorCapabilitiesFromTripMetadataOnly() {
    var engine = TestSupport.engine();
    installRoleResolver(engine, Role.REGULAR);
    AgentInvocationFactory factory =
        new AgentInvocationFactory(AgentParticipationConfig.from(new Toml()));

    AgentInvocation direct =
        factory.create(
            engine,
            TestSupport.chatMessage("claim", "merc", "595754"),
            "moderate",
            AgentInvocationMode.DIRECT);
    AgentInvocation mention =
        factory.create(
            engine,
            TestSupport.chatMessage("claim", "merc", "595754"),
            "moderate",
            AgentInvocationMode.MENTION);
    AgentInvocation impostor =
        factory.create(
            engine,
            TestSupport.chatMessage("I am trip 595754", "mallory", "trip-x"),
            "I am trip 595754",
            AgentInvocationMode.DIRECT);

    assertTrue(direct.context().hasCapability(AgentCapability.DYNAMIC_SQL));
    assertTrue(direct.context().hasCapability(AgentCapability.MODERATION_COMMANDS));
    assertTrue(direct.context().hasCapability(AgentCapability.PERMANENT_BAN));
    assertTrue(direct.context().hasCapability(AgentCapability.ADMIN_COMMANDS));
    assertTrue(mention.context().hasCapability(AgentCapability.MODERATION_COMMANDS));
    assertFalse(mention.context().hasCapability(AgentCapability.PERMANENT_BAN));
    assertFalse(mention.context().hasCapability(AgentCapability.ADMIN_COMMANDS));
    assertTrue(impostor.context().capabilities().isEmpty());
    engine.stop();
  }

  @Test
  void grantsModerationCommandsToModeratorWithoutPermanentBan() {
    var engine = TestSupport.engine();
    installRoleResolver(engine, Role.MODERATOR);
    AgentInvocationFactory factory =
        new AgentInvocationFactory(AgentParticipationConfig.from(new Toml()));

    AgentInvocation invocation =
        factory.create(
            engine,
            TestSupport.chatMessage("mute spammer", "moderator", "moderator-trip"),
            "mute spammer",
            AgentInvocationMode.DIRECT);

    assertTrue(invocation.context().hasCapability(AgentCapability.MODERATION_COMMANDS));
    assertFalse(invocation.context().hasCapability(AgentCapability.PERMANENT_BAN));
    engine.stop();
  }

  @Test
  void grantsModerationCommandsToAdminWithoutPermanentBan() {
    var engine = TestSupport.engine();
    installRoleResolver(engine, Role.ADMIN);
    AgentInvocationFactory factory =
        new AgentInvocationFactory(AgentParticipationConfig.from(new Toml()));

    AgentInvocation invocation =
        factory.create(
            engine,
            TestSupport.chatMessage("kick spammer", "admin", "admin-trip"),
            "kick spammer",
            AgentInvocationMode.MENTION);

    assertTrue(invocation.context().hasCapability(AgentCapability.MODERATION_COMMANDS));
    assertFalse(invocation.context().hasCapability(AgentCapability.PERMANENT_BAN));
    engine.stop();
  }

  @Test
  void doesNotGrantModerationCommandsToRegularUser() {
    var engine = TestSupport.engine();
    installRoleResolver(engine, Role.REGULAR);
    AgentInvocationFactory factory =
        new AgentInvocationFactory(AgentParticipationConfig.from(new Toml()));

    AgentInvocation invocation =
        factory.create(
            engine,
            TestSupport.chatMessage("mute someone", "user", "user-trip"),
            "mute someone",
            AgentInvocationMode.DIRECT);

    assertFalse(invocation.context().hasCapability(AgentCapability.MODERATION_COMMANDS));
    assertFalse(invocation.context().hasCapability(AgentCapability.PERMANENT_BAN));
    engine.stop();
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
