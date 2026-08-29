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
  /**
   * Returns the stable tool identifier exposed to the model.
   *
   * @return a lowercase identifier suitable for a provider function name
   */
  String name();

  /**
   * Implements the {@code description} operation for this agent component.
   *
   * @return the operation result
   */
  default String description() {
    return name();
  }

  /**
   * Implements the {@code parameters} operation for this agent component.
   *
   * @return the operation result
   */
  default JsonObject parameters() {
    // The default descriptor has no declared properties; keep it open for legacy SDK tools.
    // Tools that need a closed contract explicitly publish additionalProperties: false.
    return AgentToolSchemas.object();
  }

  /**
   * Implements the {@code parameters} operation for this agent component.
   *
   * @param context input argument used by this operation
   * @return the operation result
   */
  default JsonObject parameters(AgentContext context) {
    return parameters();
  }

  /**
   * Implements the {@code isAvailableTo} operation for this agent component.
   *
   * @param context input argument used by this operation
   * @return the operation result
   */
  default boolean isAvailableTo(AgentContext context) {
    return true;
  }

  /**
   * Implements the {@code requiredSuccessfulTools} operation for this agent component.
   *
   * @return the operation result
   */
  default Set<String> requiredSuccessfulTools() {
    return Set.of();
  }

  /**
   * Implements the {@code descriptor} operation for this agent component.
   *
   * @param context input argument used by this operation
   * @return the operation result
   */
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

  /**
   * Executes validated arguments for the contextual caller.
   *
   * <p>Expected invalid input, unavailable capabilities, and command failures are represented by
   * the returned {@link AgentToolResult}; implementations should not use exceptions for those
   * expected outcomes.
   *
   * @param context caller, room, capability, and privacy context
   * @param arguments arguments already parsed as a JSON object and validated against the descriptor
   * @return the truthful execution result, including failures that are safe to expose
   * @throws RuntimeException when an unexpected implementation failure prevents result creation
   */
  AgentToolResult execute(AgentContext context, JsonObject arguments);
}
