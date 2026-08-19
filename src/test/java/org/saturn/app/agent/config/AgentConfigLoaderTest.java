package org.saturn.app.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentConfigLoaderTest {
  @Test
  void loadsConfigurationThroughTheDedicatedLoader() {
    AgentConfig config =
        AgentConfigLoader.load(null, Map.of("SATURN_AGENT_ENDPOINT", "https://example.test/"));

    assertEquals("https://example.test", config.endpoint().toString());
  }
}
