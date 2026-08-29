package org.saturn.app.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentToolResult;

class RunCommandToolTest {
  @Test
  void rejectsArgumentsWithoutACommand() {
    RunCommandTool tool = new RunCommandTool((context, command, arguments) -> true);

    AgentToolResult result = tool.execute(context(), new JsonObject());

    assertTrue(result.isError());
    assertEquals("Missing command", result.content());
  }

  @Test
  void executesAnInformationalCommandWhenArgumentsAreOmitted() {
    AtomicReference<String> command = new AtomicReference<>();
    AtomicReference<String> commandArguments = new AtomicReference<>();
    RunCommandTool tool =
        new RunCommandTool(
            (context, requestedCommand, arguments) -> {
              command.set(requestedCommand);
              commandArguments.set(arguments);
              return true;
            });
    JsonObject arguments = new JsonObject();
    arguments.addProperty("command", "ping");

    AgentToolResult result = tool.execute(context(), arguments);

    assertFalse(result.isError());
    assertEquals("ping", command.get());
    assertEquals("", commandArguments.get());
  }

  private static AgentContext context() {
    return new AgentContext("programming", "alice", "trip-a", "hash-a", false, List.of());
  }
}
