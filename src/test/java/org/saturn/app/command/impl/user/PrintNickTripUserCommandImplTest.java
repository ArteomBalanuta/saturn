package org.saturn.app.command.impl.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.facade.Base;
import org.saturn.app.model.Status;
import org.saturn.app.service.SQLService;
import org.saturn.app.support.TestSupport;

class PrintNickTripUserCommandImplTest {
  @Test
  void executeQueuesRegisteredUsersPayload() {
    var engine = TestSupport.engine();
    SQLService sqlService =
        (SQLService)
            Proxy.newProxyInstance(
                SQLService.class.getClassLoader(),
                new Class<?>[] {SQLService.class},
                (proxy, method, args) -> "trip-a | merc");
    TestSupport.setField(engine, Base.class, "sqlService", sqlService);
    var message = TestSupport.chatMessage("*users", "testAuthor", "testTrip");

    var cmd = new PrintNickTripUserCommandImpl(engine, message, List.of("users"));

    assertEquals(Status.SUCCESSFUL, cmd.execute().orElseThrow());
    assertEquals("@testAuthor Users: \ntrip-a | merc", engine.outgoingMessageQueue.poll());
  }
}
