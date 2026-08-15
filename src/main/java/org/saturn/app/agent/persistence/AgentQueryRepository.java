package org.saturn.app.agent.persistence;

import com.google.gson.JsonObject;
import org.saturn.app.agent.AgentContext;

public interface AgentQueryRepository {
  JsonObject execute(String queryName, JsonObject arguments, AgentContext context);
}
