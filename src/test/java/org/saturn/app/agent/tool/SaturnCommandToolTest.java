package org.saturn.app.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.api.AgentCapability;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentToolDescriptor;
import org.saturn.app.agent.api.AgentToolResult;
import org.saturn.app.agent.api.ToolEffect;

class SaturnCommandToolTest {
  @Test
  void publishesASequentialContractAndDispatchesTheCatalogCommand() {
    AtomicReference<String> command = new AtomicReference<>();
    AtomicReference<String> commandArguments = new AtomicReference<>();
    SaturnCommandTool tool =
        new SaturnCommandTool(
            definition("weather"),
            (context, requestedCommand, arguments) -> {
              command.set(requestedCommand);
              commandArguments.set(arguments);
              return true;
            });
    JsonObject arguments = new JsonObject();
    arguments.addProperty("arguments", "Tokyo");

    AgentToolResult result = tool.execute(regularContext(), arguments);

    assertFalse(result.isError());
    assertEquals("saturn_weather", tool.name());
    assertEquals(ToolEffect.ROOM_MESSAGE, tool.descriptor(regularContext()).effect());
    assertFalse(tool.descriptor(regularContext()).isReadOnly());
    assertFalse(tool.descriptor(regularContext()).isIdempotent());
    assertEquals("weather", command.get());
    assertEquals("Tokyo", commandArguments.get());
  }

  @Test
  void rejectsPermanentBanCommandsOutsideThePermanentBanCapability() {
    AtomicReference<String> command = new AtomicReference<>();
    SaturnCommandTool tool =
        new SaturnCommandTool(
            definition("ban"),
            (context, requestedCommand, arguments) -> {
              command.set(requestedCommand);
              return true;
            });
    AgentContext moderator =
        new AgentContext(
            "programming",
            "moderator",
            "moderator-trip",
            "hash",
            false,
            List.of(),
            Set.of(AgentCapability.MODERATION_COMMANDS));

    AgentToolResult result = tool.execute(moderator, new JsonObject());

    assertFalse(tool.isAvailableTo(moderator));
    assertTrue(result.isError());
    assertEquals("TOOL_NOT_AUTHORIZED", result.errorCode());
    assertEquals(null, command.get());
  }

  @Test
  void reportsRejectedGatewayExecutionAndRequiresAContextForAvailability() {
    SaturnCommandTool tool =
        new SaturnCommandTool(
            definition("weather"), (context, requestedCommand, arguments) -> false);

    AgentToolResult result = tool.execute(regularContext(), new JsonObject());

    assertTrue(result.isError());
    assertEquals("COMMAND_REJECTED", result.errorCode());
    assertFalse(tool.isAvailableTo(null));
  }

  @Test
  void exposesModerationCommandsToAuthorizedCallers() {
    SaturnCommandTool tool =
        new SaturnCommandTool(definition("mute"), (context, requestedCommand, arguments) -> true);

    AgentToolDescriptor descriptor = tool.descriptor(regularContext());
    AgentContext moderator =
        new AgentContext(
            "programming",
            "moderator",
            "moderator-trip",
            "hash",
            false,
            List.of(),
            Set.of(AgentCapability.MODERATION_COMMANDS));

    assertEquals(org.saturn.app.agent.api.ToolAccess.AUTHORIZED_CALLER, descriptor.access());
    assertTrue(tool.isAvailableTo(moderator));
  }

  private static SaturnCommandToolCatalog.CommandToolDefinition definition(String alias) {
    return SaturnCommandToolCatalog.entries().stream()
        .filter(entry -> entry.commandAlias().equals(alias))
        .findFirst()
        .orElseThrow();
  }

  private static AgentContext regularContext() {
    return new AgentContext("programming", "alice", "trip-a", "hash", false, List.of());
  }
}
