package org.saturn.app.command.impl.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.WeatherTime;
import org.saturn.app.support.TestSupport;

class TimeUserCommandImplTest {
  @Test
  void executeWithoutArgumentsReturnsFailure() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*time", "testAuthor", "testTrip");

    var cmd = new TimeUserCommandImpl(engine, message, List.of("time", "t"));

    assertEquals(Status.FAILED, cmd.execute().orElseThrow());
    assertEquals("@testAuthor Example: *time Tokyo", engine.outgoingMessageQueue.poll());
  }

  @Test
  void getCurrentTimeAtUsesWeatherTimeZone() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*time", "testAuthor", "testTrip");
    var cmd = new TimeUserCommandImpl(engine, message, List.of("time", "t"));

    String currentTime = cmd.getCurrentTimeAt(new WeatherTime("UTC", "", "", "", "2026-03-27T10:15"));

    assertTrue(currentTime.contains("GMT"));
  }
}
