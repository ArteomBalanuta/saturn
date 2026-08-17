package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moandjiezana.toml.Toml;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.moderation.AgentModerationConfig;
import org.saturn.app.agent.moderation.ModerationAction;
import org.saturn.app.agent.moderation.ModerationDecision;
import org.saturn.app.agent.moderation.RoomModerationMonitor;
import org.saturn.app.command.UserCommand;
import org.saturn.app.facade.Base;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.AgentService;
import org.saturn.app.service.AuthorizationService;
import org.saturn.app.support.TestSupport;

class DefaultAgentRoomAutomationTest {
  @Test
  void routesExactMentionsAndLetsCommandsPassUntouched() {
    EngineImpl engine = TestSupport.engine();
    installRoleResolver(engine, Role.REGULAR);
    List<AgentInvocation> submissions = new ArrayList<>();
    AgentParticipationConfig config = AgentParticipationConfig.from(new Toml());
    DefaultAgentRoomAutomation automation = automation(engine, config, submissions);

    AgentRoomAutomation.Outcome command =
        automation.onMessage(TestSupport.chatMessage("*help", "alice", "trip-a"));
    AgentRoomAutomation.Outcome mention =
        automation.onMessage(
            TestSupport.chatMessage("@SaTuRn, can you explain this?", "alice", "trip-a"));

    assertEquals(AgentRoomAutomation.Outcome.PASS, command);
    assertEquals(AgentRoomAutomation.Outcome.CLAIMED, mention);
    assertEquals(1, submissions.size());
    assertEquals(AgentInvocationMode.MENTION, submissions.getFirst().mode());
    assertEquals("can you explain this?", submissions.getFirst().prompt());
    engine.stop();
  }

  @Test
  void quietRequestSuppressesOnlyThatUsersAmbientTurnsButNotMentions() {
    EngineImpl engine = TestSupport.engine();
    installRoleResolver(engine, Role.REGULAR);
    List<AgentInvocation> submissions = new ArrayList<>();
    AgentParticipationConfig config =
        AgentParticipationConfig.from(
            new Toml().read("[agent]\nambientEnabled = true\nambientEveryMessages = 1"));
    DefaultAgentRoomAutomation automation = automation(engine, config, submissions);

    automation.onMessage(
        TestSupport.chatMessage("Vaelen, please stay out of my conversation", "alice", "trip-a"));
    automation.onMessage(TestSupport.chatMessage("an ambient thought", "alice", "trip-a"));
    automation.onMessage(TestSupport.chatMessage("a useful thought", "bob", "trip-b"));
    automation.onMessage(TestSupport.chatMessage("@saturn answer me directly", "alice", "trip-a"));

    assertEquals(2, submissions.size());
    assertEquals(AgentInvocationMode.AMBIENT, submissions.get(0).mode());
    assertEquals("bob", submissions.get(0).context().nick());
    assertEquals(AgentInvocationMode.MENTION, submissions.get(1).mode());
    assertEquals("alice", submissions.get(1).context().nick());
    engine.stop();
  }

  @Test
  void consumesPoliteSilenceRequestsWithoutAcknowledgingThem() {
    EngineImpl engine = TestSupport.engine();
    installRoleResolver(engine, Role.REGULAR);
    List<AgentInvocation> submissions = new ArrayList<>();
    AgentParticipationConfig config =
        AgentParticipationConfig.from(new Toml().read("[agent]\nambientEveryMessages = 1"));
    DefaultAgentRoomAutomation automation = automation(engine, config, submissions);

    AgentRoomAutomation.Outcome quietRequest =
        automation.onMessage(TestSupport.chatMessage("please be silent", "alice", "trip-a"));
    AgentRoomAutomation.Outcome laterMessage =
        automation.onMessage(TestSupport.chatMessage("I can hear my GPU", "alice", "trip-a"));
    AgentRoomAutomation.Outcome mentionedQuietRequest =
        automation.onMessage(TestSupport.chatMessage("@saturn please be silent", "bob", "trip-b"));
    AgentRoomAutomation.Outcome laterMentionerMessage =
        automation.onMessage(TestSupport.chatMessage("ordinary follow-up", "bob", "trip-b"));

    assertEquals(AgentRoomAutomation.Outcome.PASS, quietRequest);
    assertEquals(AgentRoomAutomation.Outcome.PASS, laterMessage);
    assertEquals(AgentRoomAutomation.Outcome.PASS, mentionedQuietRequest);
    assertEquals(AgentRoomAutomation.Outcome.PASS, laterMentionerMessage);
    assertTrue(submissions.isEmpty());
    engine.stop();
  }

  @Test
  void routesOnlyEveryConfiguredAmbientMessageWhileMentionsRemainImmediate() {
    EngineImpl engine = TestSupport.engine();
    installRoleResolver(engine, Role.REGULAR);
    List<AgentInvocation> submissions = new ArrayList<>();
    AgentParticipationConfig config =
        AgentParticipationConfig.from(
            new Toml().read("[agent]\nambientEnabled = true\nambientEveryMessages = 8"));
    DefaultAgentRoomAutomation automation = automation(engine, config, submissions);

    automation.onMessage(TestSupport.chatMessage("@saturn answer now", "alice", "trip-a"));
    for (int index = 1; index < 8; index++) {
      automation.onMessage(TestSupport.chatMessage("ambient " + index, "alice", "trip-a"));
    }

    assertEquals(1, submissions.size());
    assertEquals(AgentInvocationMode.MENTION, submissions.getFirst().mode());

    automation.onMessage(TestSupport.chatMessage("ambient 8", "alice", "trip-a"));

    assertEquals(2, submissions.size());
    assertEquals(AgentInvocationMode.AMBIENT, submissions.get(1).mode());
    assertEquals("ambient 8", submissions.get(1).prompt());
    engine.stop();
  }

  @Test
  void doesNotSubmitUnaddressedMessagesByDefault() {
    EngineImpl engine = TestSupport.engine();
    List<AgentInvocation> submissions = new ArrayList<>();
    AgentParticipationConfig config = AgentParticipationConfig.from(new Toml());
    DefaultAgentRoomAutomation automation = automation(engine, config, submissions);

    AgentRoomAutomation.Outcome outcome = AgentRoomAutomation.Outcome.PASS;
    for (int index = 1; index <= 16; index++) {
      outcome =
          automation.onMessage(
              TestSupport.chatMessage("ordinary chat " + index, "alice", "trip-a"));
    }

    assertEquals(AgentRoomAutomation.Outcome.PASS, outcome);
    assertTrue(submissions.isEmpty());
    engine.stop();
  }

  @Test
  void ignoresMessagesWithNoTextWithoutDispatching() {
    EngineImpl engine = TestSupport.engine();
    List<AgentInvocation> submissions = new ArrayList<>();
    DefaultAgentRoomAutomation automation =
        automation(engine, AgentParticipationConfig.from(new Toml()), submissions);
    ChatMessage message = new ChatMessage("1", "alice", "trip-a", "hash-a", "now", null);

    assertEquals(AgentRoomAutomation.Outcome.PASS, automation.onMessage(message));
    assertTrue(submissions.isEmpty());
    engine.stop();
  }

  @Test
  void ignoresMessagesAuthoredByBotsToPreventReplyLoops() {
    EngineImpl engine = TestSupport.engine();
    installRoleResolver(engine, Role.REGULAR);
    User otherBot = TestSupport.user("otherBot", "bot-trip", "bot-hash");
    otherBot.setBot(true);
    engine.currentChannelUsers.add(otherBot);
    List<AgentInvocation> submissions = new ArrayList<>();
    AgentParticipationConfig config = AgentParticipationConfig.from(new Toml());
    DefaultAgentRoomAutomation automation = automation(engine, config, submissions);

    AgentRoomAutomation.Outcome outcome =
        automation.onMessage(
            TestSupport.chatMessage("@saturn answer this", "otherBot", "bot-trip"));

    assertEquals(AgentRoomAutomation.Outcome.PASS, outcome);
    assertTrue(submissions.isEmpty());
    engine.stop();
  }

  @Test
  void ignoresConventionalBotNicksWhenTheServerDoesNotFlagThem() {
    EngineImpl engine = TestSupport.engine();
    installRoleResolver(engine, Role.REGULAR);
    engine.currentChannelUsers.add(TestSupport.user("QiuLingJinBot_88", "bot-trip", "bot-hash"));
    List<AgentInvocation> submissions = new ArrayList<>();
    AgentParticipationConfig config = AgentParticipationConfig.from(new Toml());
    DefaultAgentRoomAutomation automation = automation(engine, config, submissions);

    AgentRoomAutomation.Outcome outcome =
        automation.onMessage(
            TestSupport.chatMessage("a scheduled quote", "QiuLingJinBot_88", "bot-trip"));

    assertEquals(AgentRoomAutomation.Outcome.PASS, outcome);
    assertTrue(submissions.isEmpty());
    engine.stop();
  }

  @Test
  void monitorsCommandsAndJoinEventsBeforeParticipationFiltering() {
    EngineImpl engine = TestSupport.engine();
    List<AgentInvocation> submissions = new ArrayList<>();
    List<ModerationDecision> decisions = new ArrayList<>();
    AgentParticipationConfig participationConfig = AgentParticipationConfig.from(new Toml());
    AgentModerationConfig moderationConfig =
        AgentModerationConfig.from(
            new Toml()
                .read(
                    """
                    [agent]
                    moderationMessageBurstCount = 1
                    moderationJoinBurstCount = 1
                    """));
    DefaultAgentRoomAutomation automation =
        new DefaultAgentRoomAutomation(
            engine,
            participationConfig,
            recordingService(submissions),
            new AgentInvocationFactory(participationConfig),
            new AgentMentionParser(),
            new AgentQuietRegistry(participationConfig.quietDuration(), Clock.systemUTC()),
            new RoomModerationMonitor(moderationConfig, Clock.systemUTC()),
            decision -> {
              decisions.add(decision);
              return true;
            });

    automation.onMessage(TestSupport.chatMessage("*help", "spammer", "trip-s"));
    automation.onJoin(TestSupport.user("raider", "trip-r", "hash-r"));

    assertEquals(
        List.of(ModerationAction.WARN, ModerationAction.CAPTCHA_ON),
        decisions.stream().map(ModerationDecision::action).toList());
    assertTrue(submissions.isEmpty());
    engine.stop();
  }

  @Test
  void doesNotSpendSemanticModerationOnOrdinaryMessagesOrMentions() {
    EngineImpl engine = TestSupport.engine();
    installRoleResolver(engine, Role.REGULAR);
    List<AgentInvocation> submissions = new ArrayList<>();
    AgentParticipationConfig participationConfig = AgentParticipationConfig.from(new Toml());
    AgentContext botContext =
        new AgentContext(
            engine.channel,
            engine.nick,
            "creator-trip",
            null,
            false,
            List.of("saturn", "alice"),
            Set.of(AgentCapability.MODERATION_COMMANDS));
    DefaultAgentRoomAutomation automation =
        new DefaultAgentRoomAutomation(
            engine,
            participationConfig,
            recordingService(submissions),
            new AgentInvocationFactory(participationConfig),
            new AgentMentionParser(),
            new AgentQuietRegistry(participationConfig.quietDuration(), Clock.systemUTC()),
            RoomModerationMonitor.disabled(),
            decision -> true,
            botContext,
            message -> true);

    automation.onMessage(TestSupport.chatMessage("nice", "alice", "trip-a"));
    automation.onMessage(
        TestSupport.chatMessage("@saturn i'm holding in a fart", "alice", "trip-a"));

    assertEquals(1, submissions.size());
    assertEquals(AgentInvocationMode.MENTION, submissions.getFirst().mode());
    assertEquals("i'm holding in a fart", submissions.getFirst().prompt());
    engine.stop();
  }

  @Test
  void dispatchesSevereSemanticModerationSignalsToTheModerationMode() {
    EngineImpl engine = TestSupport.engine();
    installRoleResolver(engine, Role.REGULAR);
    List<AgentInvocation> submissions = new ArrayList<>();
    AgentContext botContext =
        new AgentContext(
            engine.channel,
            engine.nick,
            "creator-trip",
            null,
            false,
            List.of("saturn", "alice"),
            Set.of(AgentCapability.MODERATION_COMMANDS));
    DefaultAgentRoomAutomation automation =
        new DefaultAgentRoomAutomation(
            engine,
            AgentParticipationConfig.from(new Toml()),
            recordingService(submissions),
            new AgentInvocationFactory(AgentParticipationConfig.from(new Toml())),
            new AgentMentionParser(),
            new AgentQuietRegistry(java.time.Duration.ofMinutes(5), Clock.systemUTC()),
            RoomModerationMonitor.disabled(),
            decision -> true,
            botContext,
            message -> true);

    automation.onMessage(TestSupport.chatMessage("I will doxx you", "alice", "trip-a"));

    assertEquals(1, submissions.size());
    assertEquals(AgentInvocationMode.MODERATION, submissions.getFirst().mode());
    assertTrue(submissions.getFirst().prompt().contains("doxx"));
    engine.stop();
  }

  @Test
  void toleratesModerationActionRejectionWithoutChangingMessageRouting() {
    EngineImpl engine = TestSupport.engine();
    List<AgentInvocation> submissions = new ArrayList<>();
    AgentModerationConfig moderationConfig =
        AgentModerationConfig.from(new Toml().read("[agent]\nmoderationMessageBurstCount = 1"));
    DefaultAgentRoomAutomation automation =
        new DefaultAgentRoomAutomation(
            engine,
            AgentParticipationConfig.from(new Toml()),
            recordingService(submissions),
            new AgentInvocationFactory(AgentParticipationConfig.from(new Toml())),
            new AgentMentionParser(),
            new AgentQuietRegistry(java.time.Duration.ofMinutes(5), Clock.systemUTC()),
            new RoomModerationMonitor(moderationConfig, Clock.systemUTC()),
            decision -> false);

    assertEquals(
        AgentRoomAutomation.Outcome.PASS,
        automation.onMessage(TestSupport.chatMessage("ordinary chat", "alice", "trip-a")));
    assertTrue(submissions.isEmpty());
    engine.stop();
  }

  private DefaultAgentRoomAutomation automation(
      EngineImpl engine, AgentParticipationConfig config, List<AgentInvocation> submissions) {
    return new DefaultAgentRoomAutomation(
        engine,
        config,
        recordingService(submissions),
        new AgentInvocationFactory(config),
        new AgentMentionParser(),
        new AgentQuietRegistry(config.quietDuration(), Clock.systemUTC()));
  }

  private AgentService recordingService(List<AgentInvocation> submissions) {
    return new AgentService() {
      @Override
      public boolean submit(AgentInvocation invocation) {
        submissions.add(invocation);
        return true;
      }

      @Override
      public void close() {}
    };
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
