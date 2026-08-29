package org.saturn.app.agent.moderation;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.tool.SaturnCommandGateway;
import org.saturn.app.service.impl.OutService;

@Slf4j
/** Executes moderation actions through the application engine. */
public final class EngineModerationActionExecutor implements ModerationActionExecutor {
  private final SaturnCommandGateway gateway;
  private final OutService outService;
  private final AgentContext botContext;

  /**
   * Implements the {@code EngineModerationActionExecutor} operation for this agent component.
   *
   * @param gateway input argument used by this operation
   * @param outService input argument used by this operation
   * @param botContext input argument used by this operation
   */
  public EngineModerationActionExecutor(
      SaturnCommandGateway gateway, OutService outService, AgentContext botContext) {
    this.gateway = Objects.requireNonNull(gateway, "gateway");
    this.outService = Objects.requireNonNull(outService, "outService");
    this.botContext = Objects.requireNonNull(botContext, "botContext");
  }

  /**
   * Implements the {@code execute} operation for this agent component.
   *
   * @param decision input argument used by this operation
   * @return the operation result
   */
  @Override
  public boolean execute(ModerationDecision decision) {
    if (decision == null) {
      log.warn("Autonomous moderation action rejected: decision is null");
      return false;
    }
    try {
      return switch (decision.action()) {
        case WARN -> warn(decision.target().orElseThrow());
        case CAPTCHA_ON -> gateway.execute(botContext, "captcha", "on");
        case MUTE -> gateway.execute(botContext, "mute", decision.target().orElseThrow());
        case KICK -> gateway.execute(botContext, "kick", decision.target().orElseThrow());
        case SHADOWBAN -> gateway.execute(botContext, "shadowban", decision.target().orElseThrow());
      };
    } catch (RuntimeException exception) {
      log.warn(
          "Autonomous moderation action failed, action={}, target={}, reason={}",
          decision.action(),
          decision.target().orElse("room"),
          exception.getMessage());
      log.debug("Autonomous moderation action failure", exception);
      return false;
    }
  }

  /**
   * Implements the {@code warn} operation for this agent component.
   *
   * @param target input argument used by this operation
   * @return the operation result
   */
  private boolean warn(String target) {
    outService.enqueueMessageForSending(target, "Please stop flooding.", false);
    return true;
  }
}
