package org.saturn.app.agent;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.saturn.app.agent.llm.LlmMessage;

final class AgentFreshnessPolicy {
  static final String USER_MESSAGE_HISTORY = "user_message_history";

  private static final String NICK_BODY = "[\\p{L}\\p{N}_-]{1,100}";
  private static final String NICK = "@?" + NICK_BODY;
  private static final String EXPLICIT_USER_TARGET =
      "(?:user\\s+(?:named\\s+@?|@)" + NICK_BODY + "|" + NICK + "\\s+(?:user|member))";
  private static final Pattern EXPLICIT_USER_PROFILE =
      Pattern.compile(
          "(?is).*\\b(?:tell\\s+me\\s+about|describe|profile|summari[sz]e|analy[sz]e)"
              + "\\s+"
              + EXPLICIT_USER_TARGET
              + "\\b.*");
  private static final Pattern PREFIX_USER_PROFILE =
      Pattern.compile(
          "(?is).*\\b(?:tell\\s+me\\s+about|describe|profile|summari[sz]e|analy[sz]e)"
              + "\\s+user\\s+(?<target>"
              + NICK
              + ")\\b.*");
  private static final Set<String> NON_NICK_PROFILE_TERMS =
      Set.of("experience", "interface", "research", "behavior", "behaviour");
  private static final Pattern SIMPLE_USER_PROFILE =
      Pattern.compile(
          "(?is).*\\btell\\s+me\\s+about\\s+"
              + "(?<target>"
              + NICK
              + ")[?.!\\s]*$");
  private static final Pattern POSSESSIVE_USER_PROFILE =
      Pattern.compile(
          "(?is).*\\b(?:show(?:\\s+me)?|give\\s+me|describe|summari[sz]e|analy[sz]e)\\s+"
              + NICK
              + "(?:'|\\x{2019})s\\s+(?:profile|messages?|history|activity)\\b.*");
  private static final Pattern WHO_IS_USER =
      Pattern.compile(
          "(?is).*\\bwho\\s+is\\s+(?<target>" + NICK + ")[?.!\\s]*$");
  private static final Pattern EXPLICIT_WHO_IS_USER =
      Pattern.compile(
          "(?is).*\\bwho\\s+is\\s+" + EXPLICIT_USER_TARGET + "[?.!\\s]*$");
  private static final Pattern USER_SPEECH =
      Pattern.compile(
          "(?is).*\\bwhat\\s+(?:did|has)\\s+(?<target>" + NICK + ")\\s+"
              + "(?:say|said|post|posted|write|wrote|written)\\b.*");
  private static final Pattern USER_HISTORY =
      Pattern.compile(
          "(?is).*\\b(?:messages?|history|activity)\\s+(?:of|for|from|by)\\s+"
              + "(?<target>"
              + NICK
              + ")\\b.*");
  private static final Pattern HISTORY_FOLLOW_UP =
      Pattern.compile(
          "(?is)^\\s*(?:please\\s+)?(?:"
              + "(?:check|look\\s+up)\\s+(?:it|him|her|them|that)(?:\\s+(?:again|elsewhere))?"
              + "|do\\s+it)"
              + "(?:\\s+@"
              + NICK_BODY
              + ")?[?.!\\s]*$");

  Optional<String> requiredTool(String prompt, List<LlmMessage> history) {
    return requiredTool(prompt, history, List.of());
  }

  Optional<String> requiredTool(
      String prompt, List<LlmMessage> history, List<String> roomUsers) {
    if (requiresNamedUserHistory(prompt, roomUsers)) {
      return Optional.of(USER_MESSAGE_HISTORY);
    }
    if (isHistoryFollowUp(prompt)
        && latestUser(history)
            .map(previousPrompt -> requiresNamedUserHistory(previousPrompt, roomUsers))
            .orElse(false)) {
      return Optional.of(USER_MESSAGE_HISTORY);
    }
    return Optional.empty();
  }

  private static boolean requiresNamedUserHistory(String prompt, List<String> roomUsers) {
    return prompt != null
        && (EXPLICIT_USER_PROFILE.matcher(prompt).matches()
            || matchesExplicitPrefixUser(prompt)
            || POSSESSIVE_USER_PROFILE.matcher(prompt).matches()
            || EXPLICIT_WHO_IS_USER.matcher(prompt).matches()
            || matchesTrustedRoomUser(SIMPLE_USER_PROFILE, prompt, roomUsers)
            || matchesTrustedRoomUser(WHO_IS_USER, prompt, roomUsers)
            || matchesTrustedRoomUser(USER_SPEECH, prompt, roomUsers)
            || matchesTrustedRoomUser(USER_HISTORY, prompt, roomUsers));
  }

  private static boolean matchesExplicitPrefixUser(String prompt) {
    var matcher = PREFIX_USER_PROFILE.matcher(prompt);
    if (!matcher.matches()) {
      return false;
    }
    return !NON_NICK_PROFILE_TERMS.contains(withoutMention(matcher.group("target")).toLowerCase());
  }

  private static boolean matchesTrustedRoomUser(
      Pattern pattern, String prompt, List<String> roomUsers) {
    var matcher = pattern.matcher(prompt);
    if (!matcher.matches()) {
      return false;
    }
    String target = matcher.group("target");
    if (target.startsWith("@")) {
      return true;
    }
    return roomUsers.stream()
        .map(AgentFreshnessPolicy::withoutMention)
        .anyMatch(target::equalsIgnoreCase);
  }

  private static String withoutMention(String nick) {
    return nick != null && nick.startsWith("@") ? nick.substring(1) : nick;
  }

  private static boolean isHistoryFollowUp(String prompt) {
    return prompt != null && HISTORY_FOLLOW_UP.matcher(prompt).matches();
  }

  private static Optional<String> latestUser(List<LlmMessage> history) {
    for (int index = history.size() - 1; index >= 0; index--) {
      LlmMessage message = history.get(index);
      if ("user".equals(message.role())) {
        return Optional.ofNullable(message.content());
      }
    }
    return Optional.empty();
  }
}
