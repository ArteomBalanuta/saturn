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
  boolean execute(AgentContext context, String command, String arguments);

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
