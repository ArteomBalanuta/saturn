package org.saturn.app.service.impl;

import java.util.List;

final class AgentLoadingAnimation {
  private static final List<String> FRAMES =
      List.of("⠋ thinking", "⠙ thinking", "⠹ thinking", "⠸ thinking");

  private AgentLoadingAnimation() {}

  static int frameCount() {
    return FRAMES.size();
  }

  static String frame(int index) {
    return FRAMES.get(Math.floorMod(index, FRAMES.size()));
  }
}
