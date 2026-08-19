package org.saturn.app.agent.api;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Set;
import org.saturn.app.agent.tool.contract.AgentToolSchemas;

/**
 * SDK extension point for a capability exposed to the LLM as a validated function definition.
 *
 * <p>Implementations must make {@link #descriptor(AgentContext)} agree with runtime behavior and
 * return errors as {@link AgentToolResult} rather than throwing for expected invalid input.
 */
public interface AgentTool {
  String name();

  default String description() {
    return name();
  }

  default JsonObject parameters() {
    // The default descriptor has no declared properties; keep it open for legacy SDK tools.
    // Tools that need a closed contract explicitly publish additionalProperties: false.
    return AgentToolSchemas.object();
  }

  default JsonObject parameters(AgentContext context) {
    return parameters();
  }

  default boolean isAvailableTo(AgentContext context) {
    return true;
  }

  default Set<String> requiredSuccessfulTools() {
    return Set.of();
  }

  default AgentToolDescriptor descriptor(AgentContext context) {
    return new AgentToolDescriptor(
        name(),
        name(),
        description(),
        "general",
        ToolAccess.PUBLIC,
        ToolEffect.READ_ONLY,
        ToolResultMode.MODEL_DATA,
        parameters(context),
        List.of(),
        List.of("Do not use outside the declared parameters and access rules."),
        List.of(),
        Set.of(),
        requiredSuccessfulTools());
  }

  /** Executes validated arguments for the contextual caller. */
  AgentToolResult execute(AgentContext context, JsonObject arguments);
}
