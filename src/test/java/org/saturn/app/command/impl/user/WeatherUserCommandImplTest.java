package org.saturn.app.command.impl.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.facade.Base;
import org.saturn.app.model.Status;
import org.saturn.app.service.WeatherService;
import org.saturn.app.support.TestSupport;

class WeatherUserCommandImplTest {
  @Test
  void executeQueuesFormattedWeatherPayload() {
    var engine = TestSupport.engine();
    WeatherService weatherService =
        (WeatherService)
            Proxy.newProxyInstance(
                WeatherService.class.getClassLoader(),
                new Class<?>[] {WeatherService.class},
                (proxy, method, args) -> "Temperature: 21 C\\nWind speed: 7 km/h\\n");
    TestSupport.setField(engine, Base.class, "weatherService", weatherService);
    var message = TestSupport.chatMessage("*weather newyork", "testAuthor", "testTrip");

    var cmd = new WeatherUserCommandImpl(engine, message, List.of("weather", "w"));

    assertEquals(Status.SUCCESSFUL, cmd.execute().orElseThrow());
    assertEquals(
        "@testAuthor Temperature: 21 C\n Wind speed: 7 km/h\n",
        engine.outgoingMessageQueue.poll());
  }
}
