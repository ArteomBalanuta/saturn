package org.saturn.app.command.impl.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.facade.Base;
import org.saturn.app.model.Status;
import org.saturn.app.service.UserService;
import org.saturn.app.support.TestSupport;

class ListNicksCommandImplTest {
  @Test
  void executeListsKnownNicksForTrip() {
    var engine = TestSupport.engine();
    UserService userService =
        (UserService)
            Proxy.newProxyInstance(
                UserService.class.getClassLoader(),
                new Class<?>[] {UserService.class},
                (proxy, method, args) -> {
                  if ("getNicksByTrip".equals(method.getName())) {
                    return List.of("merc", "saturn");
                  }
                  return null;
                });
    TestSupport.setField(engine, Base.class, "userService", userService);
    var message = TestSupport.chatMessage("*t2n trip-a", "testAuthor", "testTrip");

    var cmd = new ListNicksCommandImpl(engine, message, List.of("nicks", "t2n"));

    assertEquals(Status.SUCCESSFUL, cmd.execute().orElseThrow());
    assertEquals("@testAuthor merc,saturn", engine.outgoingMessageQueue.poll());
  }
}
