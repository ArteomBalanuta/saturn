package org.saturn.app.agent;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.agent.llm.LlmToolCall;

@Slf4j
public final class AgentToolExecutor {
  private final AgentToolRegistry registry;
  private final AgentConfig config;
  private final Gson gson = new Gson();
  private final Set<String> invocationKeys = new HashSet<>();
  private final Map<String, Integer> callsByTool = new HashMap<>();
  private final Map<String, Integer> failuresByTool = new HashMap<>();
  private final Set<String> disabledTools = new HashSet<>();
  private final Set<String> successfulTools = new HashSet<>();

  public AgentToolExecutor(AgentToolRegistry registry, AgentConfig config) {
    this.registry = registry;
    this.config = config;
  }

  public AgentToolResult execute(AgentContext context, LlmToolCall call) {
    AgentTool tool = registry.find(context, call.name()).orElse(null);
    if (tool == null) {
      return AgentToolResult.error(call.id(), call.name(), "Unknown tool: " + call.name());
    }
    if (disabledTools.contains(call.name())) {
      return AgentToolResult.error(
          call.id(), call.name(), "Tool disabled after repeated failures: " + call.name());
    }
    Set<String> missingPrerequisites = new HashSet<>(tool.requiredSuccessfulTools());
    missingPrerequisites.removeAll(successfulTools);
    if (!missingPrerequisites.isEmpty()) {
      return AgentToolResult.error(
          call.id(),
          call.name(),
          "Required tool must succeed first: " + String.join(", ", missingPrerequisites));
    }

    JsonObject arguments;
    try {
      arguments = parseArguments(call.arguments());
    } catch (JsonParseException | IllegalStateException exception) {
      recordFailure(call.name());
      return AgentToolResult.error(call.id(), call.name(), "Invalid tool arguments");
    }

    String invocationKey = call.name() + "|" + canonicalJson(arguments);
    if (!invocationKeys.add(invocationKey)) {
      return AgentToolResult.error(
          call.id(), call.name(), "Duplicate tool call; use the previous result");
    }

    int calls = callsByTool.getOrDefault(call.name(), 0);
    if (calls >= config.maxCallsPerTool()) {
      return AgentToolResult.error(
          call.id(), call.name(), "Tool call limit reached for " + call.name());
    }
    callsByTool.put(call.name(), calls + 1);

    try {
      AgentToolResult result = tool.execute(context, arguments).withCallId(call.id());
      if (result.isError()) {
        recordFailure(call.name());
      } else {
        successfulTools.add(call.name());
      }
      return result;
    } catch (RuntimeException exception) {
      recordFailure(call.name());
      log.warn("Agent tool {} failed: {}", call.name(), exception.getMessage());
      return AgentToolResult.error(call.id(), call.name(), "Tool execution failed");
    }
  }

  private JsonObject parseArguments(String rawArguments) {
    if (rawArguments == null || rawArguments.isBlank()) {
      return new JsonObject();
    }
    JsonObject arguments = gson.fromJson(rawArguments, JsonObject.class);
    if (arguments == null) {
      throw new JsonParseException("Tool arguments must be a JSON object");
    }
    return arguments;
  }

  private String canonicalJson(JsonElement element) {
    if (element.isJsonObject()) {
      Map<String, JsonElement> sorted = new TreeMap<>();
      for (var entry : element.getAsJsonObject().entrySet()) {
        sorted.put(entry.getKey(), entry.getValue());
      }
      StringJoiner result = new StringJoiner(",", "{", "}");
      for (var entry : sorted.entrySet()) {
        result.add("%s:%s".formatted(gson.toJson(entry.getKey()), canonicalJson(entry.getValue())));
      }
      return result.toString();
    }
    if (element.isJsonArray()) {
      StringJoiner result = new StringJoiner(",", "[", "]");
      for (JsonElement value : element.getAsJsonArray()) {
        result.add(canonicalJson(value));
      }
      return result.toString();
    }
    return gson.toJson(element);
  }

  private void recordFailure(String toolName) {
    int failures = failuresByTool.merge(toolName, 1, Integer::sum);
    if (failures >= config.maxToolFailures()) {
      disabledTools.add(toolName);
    }
  }
}
