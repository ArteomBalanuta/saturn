package org.saturn.app.agent.tool;

import org.saturn.app.agent.api.AgentContext;

/**
 * Command boundary used by agent tools to invoke Saturn without depending on command internals.
 *
 * <p>Implementations execute an already capability-validated command and report whether Saturn
 * accepted it. They may also return model-visible data about the delivered output.
 */
@FunctionalInterface
public interface SaturnCommandGateway {
  /**
   * Delegates an already authorized command to Saturn's command boundary.
   *
   * @param context caller and room context used by Saturn authorization
   * @param command canonical command name
   * @param arguments command argument text in Saturn's native syntax
   * @return whether Saturn accepted and executed the command
   */
  boolean execute(AgentContext context, String command, String arguments);

  /**
   * Implements the {@code executeWithResult} operation for this agent component.
   *
   * @param context input argument used by this operation
   * @param command input argument used by this operation
   * @param arguments input argument used by this operation
   * @return the operation result
   */
  default CommandExecution executeWithResult(
      AgentContext context, String command, String arguments) {
    boolean executed = execute(context, command, arguments);
    return new CommandExecution(
        executed,
        executed
            ? "Saturn command '%s' executed; its output was sent to the room. No other Saturn command was executed."
                .formatted(command)
            : "");
  }

  /** Carries the command execution value used by the enclosing agent component. */
  record CommandExecution(boolean executed, String modelData) {}
}
