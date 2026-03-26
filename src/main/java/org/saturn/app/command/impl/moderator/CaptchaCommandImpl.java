package org.saturn.app.command.impl.moderator;

import static org.saturn.app.util.Util.getAdminTrips;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;

@Slf4j
@CommandAliases(aliases = {"captcha"})
public class CaptchaCommandImpl extends UserCommandBaseImpl {
  public CaptchaCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminTrips(engine));
    super.setAliases(aliases);
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.MODERATOR;
  }

  @Override
  public Optional<Status> execute() {
    List<String> arguments = getArguments();
    if (arguments.isEmpty()) {
      engine.modService.enableCaptcha();
      replyToAuthor(" Captcha enabled!");
      log.info("Executed [captcha] command by user: {} - captcha: enabled", author());
      return successful();
    }

    String argument = arguments.getFirst();
    if ("on".equals(argument)) {
      engine.modService.enableCaptcha();
      replyToAuthor(" Captcha enabled!");
      log.info("Executed [captcha] command by user: {}, captcha: enabled", author());
      return successful();
    }

    if ("off".equals(argument)) {
      engine.modService.disableCaptcha();
      replyToAuthor(" Captcha disabled!");
      log.info("Executed [captcha] command by user: {}, captcha: disabled", author());
      return successful();
    }

    replyToAuthor("%scaptcha [on|off]".formatted(engine.prefix));
    return Optional.of(Status.FAILED);
  }
}
