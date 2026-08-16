package org.saturn.app.agent;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import org.saturn.app.agent.llm.LlmToolCall;

/** Resolves and validates provider tool calls before they enter mutable execution accounting. */
final class AgentToolCallValidator {
  private final AgentToolRegistry registry;
  private final Gson gson = new Gson();

  AgentToolCallValidator(AgentToolRegistry registry) {
    this.registry = registry;
  }

  Result validate(AgentContext context, LlmToolCall call, Set<String> allowedTools) {
    return validate(context, call, allowedTools, null);
  }

  Result validate(
      AgentContext context,
      LlmToolCall call,
      Set<String> allowedTools,
      AgentToolDescriptor classifiedDescriptor) {
    if (!allowedTools.isEmpty() && !allowedTools.contains(call.name())) {
      return Result.error(
          error(call, "TOOL_NOT_ALLOWED", "Tool is not allowed in this invocation mode"));
    }
    AgentTool tool = registry.find(context, call.name()).orElse(null);
    if (tool == null) {
      return Result.error(error(call, "UNKNOWN_TOOL", "Unknown tool: " + call.name()));
    }
    AgentToolDescriptor descriptor = classifiedDescriptor;
    if (descriptor == null) {
      try {
        descriptor = tool.descriptor(context);
      } catch (RuntimeException exception) {
        return Result.error(error(call, "INVALID_TOOL_CONTRACT", "Invalid tool contract"));
      }
    }
    if (!tool.name().equals(descriptor.name())) {
      return Result.error(error(call, "INVALID_TOOL_CONTRACT", "Tool contract name mismatch"));
    }
    JsonObject arguments;
    try {
      arguments = parseArguments(call.arguments());
    } catch (JsonParseException | IllegalStateException exception) {
      return Result.error(error(call, "INVALID_ARGUMENTS", "Invalid tool arguments"));
    }
    String validationError =
        AgentToolSchemaValidator.validateArguments(descriptor.parameters(), arguments);
    if (validationError != null) {
      return Result.error(error(call, "INVALID_ARGUMENTS", validationError));
    }
    String invocationKey = call.name() + "|" + canonicalJson(arguments);
    return Result.valid(new ValidatedToolCall(call, tool, descriptor, arguments, invocationKey));
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
      element
          .getAsJsonObject()
          .entrySet()
          .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
      StringJoiner result = new StringJoiner(",", "{", "}");
      sorted.forEach(
          (name, value) -> result.add("%s:%s".formatted(gson.toJson(name), canonicalJson(value))));
      return result.toString();
    }
    if (element.isJsonArray()) {
      StringJoiner result = new StringJoiner(",", "[", "]");
      element.getAsJsonArray().forEach(value -> result.add(canonicalJson(value)));
      return result.toString();
    }
    return gson.toJson(element);
  }

  private static AgentToolResult error(LlmToolCall call, String code, String message) {
    return AgentToolResult.error(call.id(), call.name(), code, message);
  }

  record Result(ValidatedToolCall call, AgentToolResult error) {
    static Result valid(ValidatedToolCall call) {
      return new Result(call, null);
    }

    static Result error(AgentToolResult error) {
      return new Result(null, error);
    }

    boolean isValid() {
      return call != null;
    }
  }
}
