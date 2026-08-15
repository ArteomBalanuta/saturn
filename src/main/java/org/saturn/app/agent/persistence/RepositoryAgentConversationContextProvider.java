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
}
