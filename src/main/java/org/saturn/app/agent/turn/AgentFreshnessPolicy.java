package org.saturn.app.agent.turn;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.saturn.app.agent.llm.LlmMessage;

/** Defines freshness requirements for data used during an agent turn. */
public final class AgentFreshnessPolicy {
  public static final String USER_MESSAGE_HISTORY = "user_message_history";

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
  private static final Pattern UNQUOTED_NAMED_USER_PROFILE =
      Pattern.compile(
          "(?is).*\\b(?:tell\\s+me\\s+about|describe|profile|summari[sz]e|analy[sz]e)"
              + "\\s+user\\s+named\\s+(?<target>"
              + NICK
              + ")\\b.*");
  private static final Pattern QUOTED_NAMED_USER_PROFILE =
      Pattern.compile(
          "(?is).*\\b(?:tell\\s+me\\s+about|describe|profile|summari[sz]e|analy[sz]e)"
              + "\\s+user\\s+named\\s+[\\\"'](?<target>"
              + NICK
              + ")[\\\"'].*");
  private static final Pattern TRAILING_USER_TARGET =
      Pattern.compile(
          "(?is).*\\b(?:tell\\s+me\\s+about|describe|profile|summari[sz]e|analy[sz]e)\\s+"
              + "(?<target>"
              + NICK
              + ")\\s+(?:user|member)\\b.*");
  private static final Set<String> NON_NICK_PROFILE_TERMS =
      Set.of(
          "experience",
          "interface",
          "research",
          "behavior",
          "behaviour",
          "java",
          "here",
          "there",
          "shakespeare",
          "rome");
  private static final Pattern SIMPLE_USER_PROFILE =
      Pattern.compile("(?is).*\\btell\\s+me\\s+about\\s+" + "(?<target>" + NICK + ")[?.!\\s]*$");
  private static final Pattern POSSESSIVE_USER_PROFILE =
      Pattern.compile(
          "(?is).*\\b(?:show(?:\\s+me)?|give\\s+me|describe|summari[sz]e|analy[sz]e)\\s+"
              + NICK
              + "(?:'|\\x{2019})s\\s+(?:profile|messages?|history|activity)\\b.*");
  private static final Pattern WHO_IS_USER =
      Pattern.compile("(?is).*\\bwho\\s+is\\s+(?<target>" + NICK + ")[?.!\\s]*$");
  private static final Pattern EXPLICIT_WHO_IS_USER =
      Pattern.compile("(?is).*\\bwho\\s+is\\s+" + EXPLICIT_USER_TARGET + "[?.!\\s]*$");
  private static final Pattern USER_SPEECH =
      Pattern.compile(
          "(?is).*\\bwhat\\s+(?:did|has)\\s+(?<target>"
              + NICK
              + ")\\s+"
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

  public Optional<String> requiredTool(String prompt, List<LlmMessage> history) {
    return requiredTool(prompt, history, List.of());
  }

  public Optional<String> requiredTool(
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

  public Optional<String> requiredNick(
      String prompt, List<LlmMessage> history, List<String> roomUsers) {
    Optional<String> current = extractNick(prompt, roomUsers);
    if (current.isPresent()) {
      return current;
    }
    if (isHistoryFollowUp(prompt)) {
      return latestUser(history).flatMap(previous -> extractNick(previous, roomUsers));
    }
    return Optional.empty();
  }

  private static Optional<String> extractNick(String prompt, List<String> roomUsers) {
    String normalizedPrompt = normalizePrompt(prompt);
    for (Pattern pattern :
        List.of(
            QUOTED_NAMED_USER_PROFILE,
            UNQUOTED_NAMED_USER_PROFILE,
            PREFIX_USER_PROFILE,
            TRAILING_USER_TARGET,
            SIMPLE_USER_PROFILE,
            WHO_IS_USER,
            USER_SPEECH,
            USER_HISTORY)) {
      var matcher = pattern.matcher(normalizedPrompt);
      if (matcher.matches() && matchesTrustedRoomUser(pattern, normalizedPrompt, roomUsers)) {
        return Optional.of(withoutMention(matcher.group("target")));
      }
    }
    return Optional.empty();
  }

  private static boolean requiresNamedUserHistory(String prompt, List<String> roomUsers) {
    String normalizedPrompt = normalizePrompt(prompt);
    return prompt != null
        && (EXPLICIT_USER_PROFILE.matcher(normalizedPrompt).matches()
            || QUOTED_NAMED_USER_PROFILE.matcher(normalizedPrompt).matches()
            || UNQUOTED_NAMED_USER_PROFILE.matcher(normalizedPrompt).matches()
            || matchesExplicitPrefixUser(normalizedPrompt)
            || POSSESSIVE_USER_PROFILE.matcher(normalizedPrompt).matches()
            || EXPLICIT_WHO_IS_USER.matcher(normalizedPrompt).matches()
            || matchesTrustedRoomUser(SIMPLE_USER_PROFILE, normalizedPrompt, roomUsers)
            || matchesTrustedRoomUser(WHO_IS_USER, normalizedPrompt, roomUsers)
            || matchesTrustedRoomUser(USER_SPEECH, normalizedPrompt, roomUsers)
            || matchesTrustedRoomUser(USER_HISTORY, normalizedPrompt, roomUsers));
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
    if (NON_NICK_PROFILE_TERMS.contains(withoutMention(target).toLowerCase())) {
      return false;
    }
    // A nick may be offline. Presence is useful for disambiguation, but it must not
    // decide whether the database is consulted for a profile request.
    return true;
  }

  private static String withoutMention(String nick) {
    return AgentNickNormalizer.normalize(nick);
  }

  private static String normalizePrompt(String prompt) {
    return prompt == null ? "" : prompt.replace("\\_", "_");
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
