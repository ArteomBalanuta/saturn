package org.saturn.app.agent.persistence;

import com.google.gson.JsonObject;
import org.saturn.app.agent.api.AgentContext;

/** Provides read-only query operations for persisted agent data. */
public interface AgentQueryRepository {
  JsonObject execute(String queryName, JsonObject arguments, AgentContext context);
}
