package org.saturn.app.command.impl.moderator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;
import org.saturn.app.support.TestSupport;

class CaptchaCommandImplTest {
  @Test
  void executeWithoutArgumentsEnablesCaptcha() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*captcha", "mod", "trip");

    var command = new CaptchaCommandImpl(engine, message, List.of("captcha"));

    assertEquals(Status.SUCCESSFUL, command.execute().orElseThrow());
    assertEquals("{ \"cmd\": \"enablecaptcha\"}", engine.outgoingRawMessageQueue.poll());
    assertEquals("@mod  Captcha enabled!", engine.outgoingMessageQueue.poll());
  }

  @Test
  void executeWithOffDisablesCaptcha() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*captcha off", "mod", "trip");

    var command = new CaptchaCommandImpl(engine, message, List.of("captcha"));

    assertEquals(Status.SUCCESSFUL, command.execute().orElseThrow());
    assertEquals("{ \"cmd\": \"disablecaptcha\"}", engine.outgoingRawMessageQueue.poll());
    assertEquals("@mod  Captcha disabled!", engine.outgoingMessageQueue.poll());
  }

  @Test
  void executeWithInvalidFlagReturnsFailure() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*captcha maybe", "mod", "trip");

    var command = new CaptchaCommandImpl(engine, message, List.of("captcha"));

    assertEquals(Status.FAILED, command.execute().orElseThrow());
    assertEquals("@mod *captcha [on|off]", engine.outgoingMessageQueue.poll());
  }
}
