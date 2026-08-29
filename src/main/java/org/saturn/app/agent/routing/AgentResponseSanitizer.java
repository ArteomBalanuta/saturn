package org.saturn.app.agent.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.saturn.app.agent.llm.LlmMessage;

/** Normalizes legacy agent persona output without changing factual model content. */
public final class AgentResponseSanitizer {
  private static final Pattern LEGACY_OPENING =
      Pattern.compile(
          "(?is)^\\s*Ah,\\s*[^\\n.!?:]{1,80}[.!?:]\\s*"
              + "(?:You ask about\\s+[^\\n.!?]{1,120}[.!?]\\s*)?");
  private static final Pattern MARKDOWN_LIST_ITEM =
      Pattern.compile("^\\h*(?:[*•]|\\d+[.)])\\h+(.+)$");

  /**
   * Sanitizes provider content before it becomes user-visible.
   *
   * @param content the content input; null handling follows the validation performed by this
   *     declaration
   * @return the computed result; empty or false indicates that no applicable value was available
   */
  String sanitize(String content) {
    if (content == null || content.isBlank()) {
      return "";
    }
    String withoutOpening =
        content
            .replaceAll("(?i)\\[sips tea(?: slowly)?[^\\]]*\\]\\s*", "")
            .replaceFirst("(?m)\\A\\s*(?:[*_~`#>]+\\s*)+", "")
            .strip();
    withoutOpening = LEGACY_OPENING.matcher(withoutOpening).replaceFirst("");

    return withoutOpening
        .lines()
        .filter(line -> !isLegacyPersonaBoilerplate(line))
        .map(this::formatListItem)
        .collect(Collectors.joining("\n"))
        .stripTrailing();
  }

  /**
   * Implements the {@code excludeLegacyPersonaTurns} operation for this agent component.
   *
   * @param loaded input argument used by this operation
   * @return the operation result
   */
  public List<LlmMessage> excludeLegacyPersonaTurns(List<LlmMessage> loaded) {
    List<LlmMessage> clean = new ArrayList<>(loaded.size());
    for (LlmMessage message : loaded) {
      if (message == null) {
        continue;
      }
      if ("assistant".equals(message.role()) && containsLegacyPersona(message.content())) {
        if (!clean.isEmpty() && "user".equals(clean.getLast().role())) {
          clean.removeLast();
        }
        continue;
      }
      clean.add(message);
    }
    return List.copyOf(clean);
  }

  /**
   * Checks whether content contains a legacy persona marker.
   *
   * @param content the content input; null handling follows the validation performed by this
   *     declaration
   * @return the computed result; empty or false indicates that no applicable value was available
   */
  boolean containsLegacyPersona(String content) {
    if (content == null || content.isBlank()) {
      return false;
    }
    String normalized = content.toLowerCase(Locale.ROOT);
    return normalized.contains("[sips tea")
        || normalized
            .lines()
            .anyMatch(
                line -> line.strip().toLowerCase(Locale.ROOT).startsWith("the archives reveal"))
        || normalized
            .lines()
            .anyMatch(line -> line.strip().matches("(?i)[*_`]*carpe diem[*_`]*[,.].*"))
        || LEGACY_OPENING.matcher(content.strip()).find();
  }

  /**
   * Implements the {@code isLegacyPersonaBoilerplate} operation for this agent component.
   *
   * @param line input argument used by this operation
   * @return the operation result
   */
  private boolean isLegacyPersonaBoilerplate(String line) {
    String normalized = line.strip().toLowerCase(Locale.ROOT);
    return normalized.startsWith("the archives reveal")
        || normalized.equals("your history shows:")
        || normalized.matches("^[*_]*carpe diem[*_]*[,.].*");
  }

  /**
   * Implements the {@code formatListItem} operation for this agent component.
   *
   * @param line input argument used by this operation
   * @return the operation result
   */
  private String formatListItem(String line) {
    Matcher matcher = MARKDOWN_LIST_ITEM.matcher(line);
    return matcher.matches() ? "\u2009-\u2009" + matcher.group(1) : line.stripTrailing();
  }
}
