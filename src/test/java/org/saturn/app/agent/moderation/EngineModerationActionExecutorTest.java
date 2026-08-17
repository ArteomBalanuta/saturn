package org.saturn.app.agent.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.AgentCapability;
import org.saturn.app.agent.AgentContext;
import org.saturn.app.agent.tool.SaturnCommandGateway;
import org.saturn.app.service.impl.OutService;

class EngineModerationActionExecutorTest {
  @Test
  void mapsEveryApprovedDecisionWithoutPermanentBanFallback() {
    List<String> commands = new ArrayList<>();
    SaturnCommandGateway gateway =
        (context, command, arguments) -> {
          commands.add(command + " " + arguments);
          return true;
        };
    ArrayBlockingQueue<String> messages = new ArrayBlockingQueue<>(10);
    EngineModerationActionExecutor executor =
        new EngineModerationActionExecutor(gateway, new OutService(messages), botContext());

    assertTrue(
        executor.execute(
            ModerationDecision.targeted(ModerationAction.WARN, "spammer", "flooding")));
    assertTrue(
        executor.execute(ModerationDecision.room(ModerationAction.CAPTCHA_ON, "join burst")));
    assertTrue(
        executor.execute(ModerationDecision.targeted(ModerationAction.MUTE, "spammer", "repeat")));
    assertTrue(
        executor.execute(ModerationDecision.targeted(ModerationAction.KICK, "spammer", "repeat")));
    assertTrue(
        executor.execute(
            ModerationDecision.targeted(ModerationAction.SHADOWBAN, "spammer", "reoffence")));

    assertEquals(
        List.of("captcha on", "mute spammer", "kick spammer", "shadowban spammer"), commands);
    assertEquals("@spammer Please stop flooding.", messages.poll());
    assertTrue(commands.stream().noneMatch(command -> command.startsWith("ban ")));
  }

  @Test
  void reportsCommandFailureWithoutEscalatingToAnotherAction() {
    List<String> commands = new ArrayList<>();
    EngineModerationActionExecutor executor =
        new EngineModerationActionExecutor(
            (context, command, arguments) -> {
              commands.add(command);
              return false;
            },
            new OutService(new ArrayBlockingQueue<>(2)),
            botContext());

    boolean executed =
        executor.execute(
            ModerationDecision.targeted(ModerationAction.KICK, "spammer", "second breach"));

    assertFalse(executed);
    assertEquals(List.of("kick"), commands);
  }

  @Test
  void rejectsNullDecisionsWithoutLeakingAnException() {
    EngineModerationActionExecutor executor =
        new EngineModerationActionExecutor(
            (context, command, arguments) -> true,
            new OutService(new ArrayBlockingQueue<>(2)),
            botContext());

    assertFalse(executor.execute(null));
  }

  @Test
  void isolatesGatewayExceptionsAndDoesNotAttemptAnotherAction() {
    List<String> commands = new ArrayList<>();
    EngineModerationActionExecutor executor =
        new EngineModerationActionExecutor(
            (context, command, arguments) -> {
              commands.add(command);
              throw new IllegalStateException("gateway unavailable");
            },
            new OutService(new ArrayBlockingQueue<>(2)),
            botContext());

    assertFalse(
        executor.execute(
            ModerationDecision.targeted(ModerationAction.SHADOWBAN, "spammer", "repeat")));
    assertEquals(List.of("shadowban"), commands);
  }

  @Test
  void isolatesWarningDeliveryExceptions() {
    ArrayBlockingQueue<String> messages = new ArrayBlockingQueue<>(1);
    messages.add("already queued");
    EngineModerationActionExecutor executor =
        new EngineModerationActionExecutor(
            (context, command, arguments) -> true, new OutService(messages), botContext());

    assertFalse(
        executor.execute(
            ModerationDecision.targeted(ModerationAction.WARN, "spammer", "flooding")));
    assertEquals("already queued", messages.poll());
  }

  private AgentContext botContext() {
    return new AgentContext(
        "programming",
        "saturn",
        "595754",
        "bot-hash",
        false,
        List.of("saturn"),
        java.util.Set.of(AgentCapability.MODERATION_COMMANDS));
  }
}
