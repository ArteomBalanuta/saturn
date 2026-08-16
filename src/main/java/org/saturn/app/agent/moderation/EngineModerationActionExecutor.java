package org.saturn.app.agent.moderation;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.agent.AgentContext;
import org.saturn.app.agent.tool.SaturnCommandGateway;
import org.saturn.app.service.impl.OutService;

@Slf4j
public final class EngineModerationActionExecutor implements ModerationActionExecutor {
  private final SaturnCommandGateway gateway;
  private final OutService outService;
  private final AgentContext botContext;

  public EngineModerationActionExecutor(
      SaturnCommandGateway gateway, OutService outService, AgentContext botContext) {
    this.gateway = Objects.requireNonNull(gateway, "gateway");
    this.outService = Objects.requireNonNull(outService, "outService");
    this.botContext = Objects.requireNonNull(botContext, "botContext");
  }

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

  private boolean warn(String target) {
    outService.enqueueMessageForSending(target, "Please stop flooding.", false);
    return true;
  }
}
