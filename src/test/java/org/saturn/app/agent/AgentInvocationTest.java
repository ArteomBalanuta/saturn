package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentInvocation;

class AgentInvocationTest {
  @Test
  void compatibilityConstructorDefaultsCommandOriginToFalse() {
    AgentInvocation invocation = new AgentInvocation("request", context(), "prompt");

    assertFalse(invocation.commandOriginated());
  }

  @Test
  void rejectsNullAndBlankRequestIds() {
    AgentContext context = context();

    assertThrows(
        IllegalArgumentException.class, () -> new AgentInvocation(null, context, "prompt"));
    assertThrows(
        IllegalArgumentException.class, () -> new AgentInvocation("  \n  ", context, "prompt"));
  }

  @Test
  void rejectsNullAndBlankPrompts() {
    AgentContext context = context();

    assertThrows(
        IllegalArgumentException.class, () -> new AgentInvocation("request", context, null));
    assertThrows(
        IllegalArgumentException.class, () -> new AgentInvocation("request", context, "  \n  "));
  }

  private AgentContext context() {
    return new AgentContext("room", "nick", null, null, false, List.of());
  }
}
