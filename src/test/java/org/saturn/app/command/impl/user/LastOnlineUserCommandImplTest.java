package org.saturn.app.command.impl.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.facade.Base;
import org.saturn.app.model.Status;
import org.saturn.app.service.UserService;
import org.saturn.app.support.TestSupport;

class LastOnlineUserCommandImplTest {
  @Test
  void executeWithArgumentsQueuesLastSeenMessage() {
    var engine = TestSupport.engine();
    UserService userService =
        (UserService)
            Proxy.newProxyInstance(
                UserService.class.getClassLoader(),
                new Class<?>[] {UserService.class},
                (proxy, method, args) -> "merc was online 1 minute ago");
    TestSupport.setField(engine, Base.class, "userService", userService);
    var message = TestSupport.chatMessage("*lastseen merc", "testAuthor", "testTrip");

    var cmd = new LastOnlineUserCommandImpl(engine, message, List.of("lastseen", "seen"));

    assertEquals(Status.SUCCESSFUL, cmd.execute().orElseThrow());
    assertEquals("@testAuthor merc was online 1 minute ago", engine.outgoingMessageQueue.poll());
  }

  @Test
  void executeWithoutArgumentsReturnsFailure() {
    var engine = TestSupport.engine();
    var message = TestSupport.chatMessage("*lastseen", "testAuthor", "testTrip");

    var cmd = new LastOnlineUserCommandImpl(engine, message, List.of("lastseen", "seen"));

    assertEquals(Status.FAILED, cmd.execute().orElseThrow());
    assertEquals("@testAuthor \n Example: *lastseen merc", engine.outgoingMessageQueue.poll());
  }
}
