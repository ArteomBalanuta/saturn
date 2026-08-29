package org.saturn.app.agent.tool.execution;

import com.google.gson.JsonObject;
import java.util.Objects;
import org.saturn.app.agent.api.AgentTool;
import org.saturn.app.agent.api.AgentToolDescriptor;
import org.saturn.app.agent.llm.LlmToolCall;

/** Immutable hand-off from tool contract validation to request-local execution accounting. */
record ValidatedToolCall(
    LlmToolCall source,
    AgentTool tool,
    AgentToolDescriptor descriptor,
    JsonObject arguments,
    String invocationKey) {
  ValidatedToolCall {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(tool, "tool");
    Objects.requireNonNull(descriptor, "descriptor");
    arguments = Objects.requireNonNull(arguments, "arguments").deepCopy();
    Objects.requireNonNull(invocationKey, "invocationKey");
  }
}
