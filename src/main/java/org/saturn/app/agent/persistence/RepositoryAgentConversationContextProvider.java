package org.saturn.app.agent.persistence;

import com.google.gson.JsonObject;
import java.util.Objects;
import org.saturn.app.agent.AgentContext;
import org.saturn.app.agent.AgentConversationContextProvider;

public final class RepositoryAgentConversationContextProvider
    implements AgentConversationContextProvider {
  private final AgentQueryRepository repository;
  private final int messageLimit;

  public RepositoryAgentConversationContextProvider(
      AgentQueryRepository repository, int messageLimit) {
    this.repository = Objects.requireNonNull(repository, "repository");
    if (messageLimit <= 0) {
      throw new IllegalArgumentException("messageLimit must be positive");
    }
    this.messageLimit = messageLimit;
  }

  @Override
  public String load(AgentContext context) {
    JsonObject arguments = new JsonObject();
    arguments.addProperty("room", context.room());
    arguments.addProperty("limit", messageLimit);
    return repository.execute("recent_messages_for_room", arguments, context).toString();
  }

  @Override
  public String load(AgentContext context, String author, String text) {
    JsonObject arguments = new JsonObject();
    arguments.addProperty("room", context.room());
    arguments.addProperty("limit", messageLimit);
    JsonObject result = repository.execute("recent_messages_for_room", arguments, context);
    if (author == null || text == null || !result.has("rows") || !result.get("rows").isJsonArray()) {
      return result.toString();
    }
    var rows = result.getAsJsonArray("rows");
    for (int index = rows.size() - 1; index >= 0; index--) {
      var row = rows.get(index);
      if (!row.isJsonObject()) {
        continue;
      }
      JsonObject value = row.getAsJsonObject();
      if (author.equals(stringValue(value, "name")) && text.equals(stringValue(value, "message"))) {
        rows.remove(index);
        break;
      }
    }
    return result.toString();
  }

  private static String stringValue(JsonObject value, String member) {
    if (!value.has(member) || value.get(member).isJsonNull()) {
      return null;
    }
    return value.get(member).getAsString();
  }
}
