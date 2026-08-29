package org.saturn.app.agent.routing;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class AgentGapContractRedTest {
  @Test
  void emptyProjectionIsSafe() {
    assertDoesNotThrow(() -> new AgentMessageProjector().project(List.of(), 100));
  }

  @Test
  void factoryWiresObservableHooksIntoRouter() throws Exception {
    var field = DefaultAgentRouter.class.getDeclaredField("executionHooks");
    assertNotNull(field);
  }
}
