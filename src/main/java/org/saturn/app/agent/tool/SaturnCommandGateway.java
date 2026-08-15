package org.saturn.app.agent.tool;

import org.saturn.app.agent.AgentContext;

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

  record CommandExecution(boolean executed, String modelData) {}
}
