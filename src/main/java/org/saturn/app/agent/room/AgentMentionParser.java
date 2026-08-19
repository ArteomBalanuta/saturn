package org.saturn.app.agent.room;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses agent mentions and their accompanying prompt text from room messages. */
public final class AgentMentionParser {
  public Optional<String> parse(String text, String botNick) {
    if (text == null || text.isBlank() || botNick == null || botNick.isBlank()) {
      return Optional.empty();
    }
    Pattern mention =
        Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}_])@" + Pattern.quote(botNick.strip()) + "(?![\\p{L}\\p{N}_-])");
    Matcher matcher = mention.matcher(text);
    if (!matcher.find()) {
      return Optional.empty();
    }

    String remaining = matcher.replaceAll(" ").strip();
    remaining = remaining.replaceFirst("^[\\s,;:.-]+", "").strip();
    remaining = remaining.replaceAll("\\s+([?!.,])", "$1");
    remaining = remaining.replaceFirst(",[?!]$", remaining.endsWith("?") ? "?" : "!");
    return remaining.isBlank() ? Optional.empty() : Optional.of(remaining);
  }
}
