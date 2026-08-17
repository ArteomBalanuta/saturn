package org.saturn.app.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AgentLoadingAnimationTest {
  @Test
  void cyclesThroughCompactLoadingFrames() {
    assertEquals("⠋ thinking", AgentLoadingAnimation.frame(0));
    assertEquals("⠙ thinking", AgentLoadingAnimation.frame(1));
    assertEquals("⠋ thinking", AgentLoadingAnimation.frame(AgentLoadingAnimation.frameCount()));
  }
}
