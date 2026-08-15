package org.saturn.app.agent;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.saturn.app.agent.llm.LlmToolCall;

final class AgentCommandProseGuard {
  private static final String RUN_COMMAND = "run_command";
  private static final Set<String> RUN_COMMAND_ARGUMENTS = Set.of("command", "arguments");
  private static final Pattern BACKTICK_FENCED_CODE =
      Pattern.compile("(?ms)^[ \\t]{0,3}`{3,}[^\\r\\n]*\\R(.*?)^[ \\t]{0,3}`{3,}[ \\t]*$");
  private static final Pattern TILDE_FENCED_CODE =
      Pattern.compile("(?ms)^[ \\t]{0,3}~{3,}[^\\r\\n]*\\R(.*?)^[ \\t]{0,3}~{3,}[ \\t]*$");
  private static final Pattern INLINE_CODE = Pattern.compile("(?<!`)(`+)([^`\\r\\n]+)\\1(?!`)");

  private final Gson gson = new Gson();
  private final Set<String> allowedCommands;

  private AgentCommandProseGuard(Set<String> allowedCommands) {
    this.allowedCommands = Set.copyOf(allowedCommands);
  }

  static AgentCommandProseGuard from(List<JsonObject> definitions) {
    Set<String> commands = new HashSet<>();
    for (JsonObject definition : definitions) {
      JsonObject function = object(definition, "function");
      if (function == null || !RUN_COMMAND.equals(string(function, "name"))) {
        continue;
      }
      JsonObject parameters = object(function, "parameters");
      JsonObject properties = object(parameters, "properties");
      JsonObject command = object(properties, "command");
      JsonArray values = array(command, "enum");
      if (values == null) {
        continue;
      }
      for (JsonElement value : values) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
          commands.add(value.getAsString().toLowerCase(Locale.ROOT));
        }
      }
    }
    return new AgentCommandProseGuard(commands);
  }

  Optional<String> findCommand(String content) {
    if (content == null || content.isBlank()) {
      return Optional.empty();
    }
    Optional<String> fenced = findIn(content, BACKTICK_FENCED_CODE, 1);
    if (fenced.isEmpty()) {
      fenced = findIn(content, TILDE_FENCED_CODE, 1);
    }
    if (fenced.isPresent()) {
      return fenced;
    }
    Optional<String> inline = findIn(content, INLINE_CODE, 2);
    if (inline.isPresent()) {
      return inline;
    }
    for (String line : content.lines().toList()) {
      String stripped = line.strip();
      Optional<String> plain = commandAtStart(stripped);
      if (plain.isPresent() && !looksLikeNarrative(stripped, plain.get())) {
        return plain;
      }
    }
    return Optional.empty();
  }

  private static boolean looksLikeNarrative(String line, String command) {
    String remainder = line.substring(Math.min(line.length(), command.length())).stripLeading();
    if (remainder.isEmpty()) {
      return false;
    }
    String firstWord = remainder.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
    return Set.of("is", "was", "were", "are", "has", "have", "had", "will", "would", "can", "could")
        .contains(firstWord);
  }

  boolean matches(LlmToolCall call, String expectedCommand) {
    if (!RUN_COMMAND.equals(call.name()) || !allowedCommands.contains(expectedCommand)) {
      return false;
    }
    try {
      JsonObject arguments = gson.fromJson(call.arguments(), JsonObject.class);
      if (arguments == null || !RUN_COMMAND_ARGUMENTS.containsAll(arguments.keySet())) {
        return false;
      }
      JsonElement command = arguments.get("command");
      JsonElement commandArguments = arguments.get("arguments");
      return command != null
          && command.isJsonPrimitive()
          && command.getAsJsonPrimitive().isString()
          && (commandArguments == null
              || (commandArguments.isJsonPrimitive()
                  && commandArguments.getAsJsonPrimitive().isString()))
          && expectedCommand.equals(command.getAsString());
    } catch (JsonParseException | IllegalStateException exception) {
      return false;
    }
  }

  Optional<String> executedCommand(LlmToolCall call) {
    if (!RUN_COMMAND.equals(call.name())) {
      return Optional.empty();
    }
    try {
      JsonObject arguments = gson.fromJson(call.arguments(), JsonObject.class);
      JsonElement command = arguments == null ? null : arguments.get("command");
      if (command == null
          || !command.isJsonPrimitive()
          || !command.getAsJsonPrimitive().isString()) {
        return Optional.empty();
      }
      String normalized = command.getAsString().toLowerCase(Locale.ROOT);
      return allowedCommands.contains(normalized) ? Optional.of(normalized) : Optional.empty();
    } catch (JsonParseException | IllegalStateException exception) {
      return Optional.empty();
    }
  }

  private Optional<String> findIn(String content, Pattern pattern, int contentGroup) {
    var matcher = pattern.matcher(content);
    while (matcher.find()) {
      Optional<String> command = commandAtStart(matcher.group(contentGroup));
      if (command.isPresent()) {
        return command;
      }
    }
    return Optional.empty();
  }

  private Optional<String> commandAtStart(String snippet) {
    String normalized = snippet.stripLeading();
    if (normalized.isEmpty()) {
      return Optional.empty();
    }
    int firstCodePoint = normalized.codePointAt(0);
    if (!Character.isLetterOrDigit(firstCodePoint)) {
      normalized = normalized.substring(Character.charCount(firstCodePoint)).stripLeading();
    }
    int end = 0;
    while (end < normalized.length()) {
      int codePoint = normalized.codePointAt(end);
      if (Character.isWhitespace(codePoint)) {
        break;
      }
      end += Character.charCount(codePoint);
    }
    String command = normalized.substring(0, end).toLowerCase(Locale.ROOT);
    return allowedCommands.contains(command) ? Optional.of(command) : Optional.empty();
  }

  private static JsonObject object(JsonObject parent, String name) {
    if (parent == null) {
      return null;
    }
    JsonElement value = parent.get(name);
    return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
  }

  private static JsonArray array(JsonObject parent, String name) {
    if (parent == null) {
      return null;
    }
    JsonElement value = parent.get(name);
    return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
  }

  private static String string(JsonObject parent, String name) {
    if (parent == null) {
      return "";
    }
    JsonElement value = parent.get(name);
    return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
        ? value.getAsString()
        : "";
  }
}
