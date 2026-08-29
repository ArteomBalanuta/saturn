package org.saturn.app.agent.routing;

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
import org.saturn.app.agent.tool.contract.AgentToolDefinitionJson;

/** Checks agent command prose for unsafe or unsupported output. */
public final class AgentCommandProseGuard {
  private static final String RUN_COMMAND = "run_command";
  private static final Set<String> RUN_COMMAND_ARGUMENTS = Set.of("command", "arguments");
  private static final Pattern BACKTICK_FENCED_CODE =
      Pattern.compile("(?ms)^[ \\t]{0,3}`{3,}[^\\r\\n]*\\R(.*?)^[ \\t]{0,3}`{3,}[ \\t]*$");
  private static final Pattern TILDE_FENCED_CODE =
      Pattern.compile("(?ms)^[ \\t]{0,3}~{3,}[^\\r\\n]*\\R(.*?)^[ \\t]{0,3}~{3,}[ \\t]*$");
  private static final Pattern INLINE_CODE = Pattern.compile("(?<!`)(`+)([^`\\r\\n]+)\\1(?!`)");

  private final Gson gson = new Gson();
  private final Set<String> allowedCommands;

  /**
   * Implements the {@code AgentCommandProseGuard} operation for this agent component.
   *
   * @param allowedCommands input argument used by this operation
   */
  private AgentCommandProseGuard(Set<String> allowedCommands) {
    this.allowedCommands = Set.copyOf(allowedCommands);
  }

  /**
   * Implements the {@code from} operation for this agent component.
   *
   * @param definitions input argument used by this operation
   * @return the operation result
   */
  public static AgentCommandProseGuard from(List<JsonObject> definitions) {
    Set<String> commands = new HashSet<>();
    for (JsonObject definition : definitions) {
      JsonObject function = object(definition, "function");
      if (function == null
          || AgentToolDefinitionJson.functionName(definition)
              .filter(RUN_COMMAND::equals)
              .isEmpty()) {
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

  /**
   * Implements the {@code findCommand} operation for this agent component.
   *
   * @param content input argument used by this operation
   * @return the operation result
   */
  public Optional<String> findCommand(String content) {
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
    return Optional.empty();
  }

  /**
   * Checks whether a candidate command call matches the expected command channel.
   *
   * @param call the call input; null handling follows the validation performed by this declaration
   * @param expectedCommand the expectedCommand input; null handling follows the validation
   *     performed by this declaration
   * @return the computed result; empty or false indicates that no applicable value was available
   */
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

  /**
   * Implements the {@code executedCommand} operation for this agent component.
   *
   * @param call input argument used by this operation
   * @return the operation result
   */
  public Optional<String> executedCommand(LlmToolCall call) {
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

  /**
   * Implements the {@code findIn} operation for this agent component.
   *
   * @param content input argument used by this operation
   * @param pattern input argument used by this operation
   * @param contentGroup input argument used by this operation
   * @return the operation result
   */
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

  /**
   * Implements the {@code commandAtStart} operation for this agent component.
   *
   * @param snippet input argument used by this operation
   * @return the operation result
   */
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

  /**
   * Implements the {@code object} operation for this agent component.
   *
   * @param parent input argument used by this operation
   * @param name input argument used by this operation
   * @return the operation result
   */
  private static JsonObject object(JsonObject parent, String name) {
    if (parent == null) {
      return null;
    }
    JsonElement value = parent.get(name);
    return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
  }

  /**
   * Implements the {@code array} operation for this agent component.
   *
   * @param parent input argument used by this operation
   * @param name input argument used by this operation
   * @return the operation result
   */
  private static JsonArray array(JsonObject parent, String name) {
    if (parent == null) {
      return null;
    }
    JsonElement value = parent.get(name);
    return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
  }
}
