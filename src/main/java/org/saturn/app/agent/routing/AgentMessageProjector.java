package org.saturn.app.agent.routing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.saturn.app.agent.llm.LlmMessage;
import org.saturn.app.agent.llm.LlmToolCall;

/** Pairing-aware, copy-only provider context projection. */
public final class AgentMessageProjector {
  public AgentContextProjection project(List<LlmMessage> source, int budget) {
    if (source.isEmpty()) {
      return new AgentContextProjection(
          List.of(), 0, 0, budget, false, false, 0, fingerprint(List.of()));
    }
    return project(source, budget, true);
  }

  AgentContextProjection projectAfterTool(List<LlmMessage> source, int budget) {
    return project(source, budget, false);
  }

  private AgentContextProjection project(List<LlmMessage> source, int budget, boolean liveTail) {
    if (!"system".equals(source.getFirst().role())) {
      List<LlmMessage> copied = source.stream().map(AgentMessageProjector::copyMessage).toList();
      int chars = serializedLength(copied);
      return new AgentContextProjection(
          copied, chars, (chars + 3) / 4, budget, false, chars > budget, 0, fingerprint(copied));
    }
    List<LlmMessage> projected = new ArrayList<>();
    projected.add(copyMessage(source.getFirst()));
    boolean malformed = false;
    List<List<LlmMessage>> units = new ArrayList<>();
    int historyEnd = liveTail ? source.size() - 1 : source.size();
    for (int i = 1; i < historyEnd; i++) {
      LlmMessage message = source.get(i);
      if (!message.toolCalls().isEmpty()) {
        List<LlmMessage> unit = new ArrayList<>();
        unit.add(copyMessage(message));
        Set<String> ids =
            message.toolCalls().stream()
                .map(LlmToolCall::id)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, Integer> resultCounts = new java.util.HashMap<>();
        int j = i + 1;
        while (j < historyEnd && "tool".equals(source.get(j).role())) {
          unit.add(copyMessage(source.get(j)));
          resultCounts.merge(source.get(j).toolCallId(), 1, Integer::sum);
          j++;
        }
        if (resultCounts.size() != ids.size()
            || !ids.stream().allMatch(id -> resultCounts.getOrDefault(id, 0) == 1)) {
          malformed = true;
        } else {
          units.add(List.copyOf(unit));
        }
        i = j - 1;
      } else if (!"tool".equals(message.role())) {
        units.add(List.of(copyMessage(message)));
      } else {
        malformed = true;
      }
    }
    projected.addAll(units.stream().flatMap(List::stream).toList());
    if (liveTail) {
      projected.add(copyMessage(source.getLast()));
    }
    int removed = 0;
    while (serializedLength(projected) > budget && projected.size() > 2 && !units.isEmpty()) {
      List<LlmMessage> first = units.removeFirst();
      projected.subList(1, 1 + first.size()).clear();
      removed++;
    }
    int chars = serializedLength(projected);
    return new AgentContextProjection(
        List.copyOf(projected),
        chars,
        (chars + 3) / 4,
        budget,
        removed > 0,
        malformed || chars > budget,
        removed,
        fingerprint(projected));
  }

  private static int serializedLength(List<LlmMessage> messages) {
    return messages.stream()
        .mapToInt(
            message ->
                (message.role()
                        + "|"
                        + String.valueOf(message.content())
                        + "|"
                        + String.valueOf(message.toolCallId())
                        + message.toolCalls())
                    .getBytes(StandardCharsets.UTF_8)
                    .length)
        .sum();
  }

  public static String fingerprintOf(List<LlmMessage> messages) {
    return fingerprint(messages);
  }

  private static LlmMessage copyMessage(LlmMessage message) {
    return new LlmMessage(
        message.role(), message.content(), List.copyOf(message.toolCalls()), message.toolCallId());
  }

  private static String fingerprint(List<LlmMessage> messages) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      messages.forEach(
          message ->
              digest.update(
                  (message.role()
                          + "|"
                          + message.content()
                          + "|"
                          + message.toolCallId()
                          + message.toolCalls())
                      .getBytes(StandardCharsets.UTF_8)));
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException exception) {
      throw new AssertionError(exception);
    }
  }
}
