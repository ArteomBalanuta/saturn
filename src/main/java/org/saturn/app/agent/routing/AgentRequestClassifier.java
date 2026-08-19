package org.saturn.app.agent.routing;

import java.util.Locale;
import java.util.regex.Pattern;
import org.saturn.app.agent.turn.AgentToolEvidence;

/** Small deterministic boundary classifier; it never consults model output or mutable history. */
public final class AgentRequestClassifier {
  private static final Pattern LETTER = Pattern.compile(".*\\p{L}.*", Pattern.DOTALL);
  private static final Pattern ENDING = Pattern.compile(".*[.!?。！？].*", Pattern.DOTALL);
  private static final Pattern ACTION =
      Pattern.compile(
          "^(run|execute|do|make|create|delete|remove|set|get|find|search|lookup|list|show|check|send|post|remember|schedule|weather|who is|what is the weather)\\b.*",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
  private static final Pattern SOCIAL =
      Pattern.compile(
          "^(how are you|what do you think|can you explain|why|how)\\b.*\\?|.*\\b(hello|hi|hey|thanks|thank you|goodbye|bye|okay|ok)\\b.*",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);

  public AgentRequestKind classifyCandidate(AgentRequestInput input) {
    String text = input == null || input.text() == null ? "" : input.text().strip();
    if (text.isBlank()
        || !LETTER.matcher(text).matches()
        || text.codePoints().anyMatch(Character::isISOControl)
        || startsProtocol(text)
        || ACTION.matcher(text).matches()) {
      return AgentRequestKind.UNCLASSIFIED;
    }
    if (SOCIAL.matcher(text).matches() || ENDING.matcher(text).matches()) {
      return AgentRequestKind.TALK;
    }
    return AgentRequestKind.UNCLASSIFIED;
  }

  public AgentRequestKind finalizeKind(AgentRequestKind candidate, AgentToolEvidence evidence) {
    if (evidence != null && evidence.attempted()) {
      return AgentRequestKind.TOOL_CALL;
    }
    return candidate == AgentRequestKind.TALK
        ? AgentRequestKind.TALK
        : AgentRequestKind.UNCLASSIFIED;
  }

  private boolean startsProtocol(String text) {
    String lower = text.toLowerCase(Locale.ROOT);
    return lower.startsWith("{")
        || lower.startsWith("[")
        || lower.startsWith("<")
        || lower.startsWith("```")
        || lower.startsWith("tool_call")
        || lower.startsWith("function_call");
  }
}
