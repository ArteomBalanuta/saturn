package org.saturn.app.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.AgentContext;
import org.saturn.app.agent.AgentToolResult;
import org.saturn.app.agent.persistence.AgentQueryRepository;

class UserMessageHistoryToolTest {
  @Test
  void rejectsBlankRoomWithoutQueryingRepository() {
    AtomicBoolean queried = new AtomicBoolean();
    AgentQueryRepository repository =
        (queryName, arguments, context) -> {
          queried.set(true);
          return new JsonObject();
        };
    UserMessageHistoryTool tool = new UserMessageHistoryTool(repository);
    JsonObject arguments = new JsonObject();
    arguments.addProperty("nick", "alice");
    arguments.addProperty("room", " ");

    AgentToolResult result = tool.execute(context(), arguments);

    assertTrue(result.isError());
    assertEquals("A non-blank room is required", result.content());
    assertFalse(queried.get());
  }

  @Test
  void ignoresMalformedEvidenceRowsWhenBuildingMetadata() {
    AtomicReference<String> queryName = new AtomicReference<>();
    AtomicReference<JsonObject> queryArguments = new AtomicReference<>();
    AgentQueryRepository repository =
        (queryNameValue, argumentsValue, context) -> {
          queryName.set(queryNameValue);
          queryArguments.set(argumentsValue);
          return JsonParser.parseString(
                  """
                    {
                      "rows": [
                        {"createdOn": 30},
                        "not-an-object",
                        {"message": "missing timestamp"},
                        {"createdOn": "not-a-number"}
                      ]
                    }
                    """)
              .getAsJsonObject();
        };
    UserMessageHistoryTool tool = new UserMessageHistoryTool(repository);
    JsonObject arguments = new JsonObject();
    arguments.addProperty("nick", "@alice");

    AgentToolResult result = tool.execute(context(), arguments);
    JsonObject content = JsonParser.parseString(result.content()).getAsJsonObject();

    assertFalse(result.isError());
    assertEquals("recent_messages_for_user", queryName.get());
    assertEquals("alice", queryArguments.get().get("nick").getAsString());
    assertEquals(4, content.get("returnedCount").getAsInt());
    assertEquals(30, content.get("oldestCreatedOn").getAsLong());
    assertEquals(30, content.get("newestCreatedOn").getAsLong());
  }

  @Test
  void mapsInvalidRepositoryRequestsToStableErrors() {
    AgentQueryRepository repository =
        (queryName, arguments, context) -> {
          throw new IllegalArgumentException("invalid query");
        };
    UserMessageHistoryTool tool = new UserMessageHistoryTool(repository);
    JsonObject arguments = new JsonObject();
    arguments.addProperty("nick", "alice");

    AgentToolResult result = tool.execute(context(), arguments);

    assertTrue(result.isError());
    assertEquals("Invalid message-history request", result.content());
  }

  private static AgentContext context() {
    return new AgentContext("programming", "alice", "trip", "hash", false, List.of("alice"));
  }
}
