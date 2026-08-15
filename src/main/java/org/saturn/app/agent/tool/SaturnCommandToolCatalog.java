package org.saturn.app.agent.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.classgraph.ClassGraph;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.saturn.app.agent.AgentCapability;
import org.saturn.app.agent.AgentToolRegistry;
import org.saturn.app.agent.ToolEffect;
import org.saturn.app.agent.ToolExample;
import org.saturn.app.command.UserCommand;
import org.saturn.app.command.annotation.CommandAliases;

/**
 * Reflection-backed source of contextual agent contracts for Saturn command handlers.
 *
 * <p>Aliases are intentionally read from {@link CommandAliases}; this catalog owns only agent
 * metadata such as structured input, authority, and execution behavior.
 */
public final class SaturnCommandToolCatalog {
  private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(10);
  private static final List<CommandToolDefinition> ENTRIES = loadEntries();

  private SaturnCommandToolCatalog() {}

  /** Returns one immutable command tool contract for every annotated Saturn command handler. */
  public static List<CommandToolDefinition> entries() {
    return ENTRIES;
  }

  /** Registers every reflected command contract with the supplied agent tool registry. */
  public static void registerAll(AgentToolRegistry registry, SaturnCommandGateway gateway) {
    entries().forEach(definition -> registry.register(new SaturnCommandTool(definition, gateway)));
  }

  private static List<CommandToolDefinition> loadEntries() {
    try (var scan =
        new ClassGraph()
            .enableClassInfo()
            .enableAnnotationInfo()
            .acceptPackages("org.saturn.app.command.impl")
            .scan()) {
      List<CommandToolDefinition> entries =
          scan.getClassesWithAnnotation(CommandAliases.class).stream()
              .map(SaturnCommandToolCatalog::definitionFor)
              .sorted(Comparator.comparing(CommandToolDefinition::toolName))
              .toList();
      if (entries.stream().map(CommandToolDefinition::toolName).distinct().count()
          != entries.size()) {
        throw new IllegalStateException("Saturn command tools must have unique names");
      }
      return List.copyOf(entries);
    }
  }

  @SuppressWarnings("unchecked")
  private static CommandToolDefinition definitionFor(io.github.classgraph.ClassInfo classInfo) {
    try {
      Class<?> rawType = classInfo.loadClass();
      if (!UserCommand.class.isAssignableFrom(rawType)) {
        throw new IllegalStateException(
            "Annotated type is not a Saturn command: " + rawType.getName());
      }
      Class<? extends UserCommand> handlerType = (Class<? extends UserCommand>) rawType;
      List<String> aliases = aliasesFor(classInfo);
      if (aliases.isEmpty()) {
        throw new IllegalStateException("Saturn command has no aliases: " + handlerType.getName());
      }
      String commandAlias = aliases.getFirst();
      CommandProfile profile = profileFor(handlerType);
      return new CommandToolDefinition(
          handlerType,
          "saturn_" + commandAlias.toLowerCase(Locale.ROOT),
          commandAlias,
          aliases,
          argumentSchema(),
          profile.requiredCapabilities(),
          profile.effect(),
          false,
          COMMAND_TIMEOUT,
          "Execute Saturn's `%s` command. Supply `arguments` using the same argument syntax a user "
              + "would provide after `*%s`. Saturn performs the final authorization and command "
              + "validation.".formatted(commandAlias, commandAlias),
          List.of(
              "Use only when the user explicitly asks Saturn to execute `%s`."
                  .formatted(commandAlias)),
          List.of(
              "Do not use for a question that can be answered without executing `%s`."
                  .formatted(commandAlias)),
          List.of(
              new ToolExample(
                  "saturn_" + commandAlias.toLowerCase(Locale.ROOT),
                  "{\"arguments\":\"\"}",
                  "Runs Saturn's `%s` command without extra arguments.".formatted(commandAlias))));
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalStateException("Could not load Saturn command tool contract", exception);
    }
  }

  private static List<String> aliasesFor(io.github.classgraph.ClassInfo classInfo) {
    return classInfo.getAnnotationInfo(CommandAliases.class).getParameterValues().stream()
        .filter(parameter -> "aliases".equals(parameter.getName()))
        .findFirst()
        .map(parameter -> Arrays.asList((String[]) parameter.getValue()))
        .orElseThrow(
            () ->
                new IllegalStateException("Saturn command has no aliases: " + classInfo.getName()));
  }

  private static JsonObject argumentSchema() {
    JsonObject argument = new JsonObject();
    argument.addProperty("type", "string");
    argument.addProperty("maxLength", 4_000);
    argument.addProperty(
        "description", "Exact text after the command alias, without the Saturn prefix.");
    JsonObject properties = new JsonObject();
    properties.add("arguments", argument);
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    schema.add("properties", properties);
    schema.add("required", new JsonArray());
    schema.addProperty("additionalProperties", false);
    return schema;
  }

  private static CommandProfile profileFor(Class<? extends UserCommand> handlerType) {
    String packageName = handlerType.getPackageName();
    if (packageName.contains(".impl.admin")) {
      return new CommandProfile(
          EnumSet.of(AgentCapability.ADMIN_COMMANDS), ToolEffect.ROOM_MESSAGE);
    }
    if (packageName.contains(".impl.moderator")) {
      if (handlerType.getSimpleName().matches("(BanUser|UnBanUser|UnBanAllUser)CommandImpl")) {
        return new CommandProfile(EnumSet.of(AgentCapability.PERMANENT_BAN), ToolEffect.MODERATION);
      }
      return new CommandProfile(
          EnumSet.of(AgentCapability.MODERATION_COMMANDS), ToolEffect.MODERATION);
    }
    return new CommandProfile(EnumSet.noneOf(AgentCapability.class), ToolEffect.ROOM_MESSAGE);
  }

  /** Immutable contextual contract for one reflected Saturn command handler. */
  public record CommandToolDefinition(
      Class<? extends UserCommand> handlerType,
      String toolName,
      String commandAlias,
      List<String> aliases,
      JsonObject parameters,
      Set<AgentCapability> requiredCapabilities,
      ToolEffect effect,
      boolean isIdempotent,
      Duration timeout,
      String description,
      List<String> whenToUse,
      List<String> whenNotToUse,
      List<ToolExample> examples) {
    public CommandToolDefinition {
      handlerType = Objects.requireNonNull(handlerType, "handlerType");
      toolName = Objects.requireNonNull(toolName, "toolName");
      commandAlias = Objects.requireNonNull(commandAlias, "commandAlias");
      aliases = List.copyOf(aliases);
      parameters = Objects.requireNonNull(parameters, "parameters").deepCopy();
      requiredCapabilities = Set.copyOf(requiredCapabilities);
      effect = Objects.requireNonNull(effect, "effect");
      timeout = Objects.requireNonNull(timeout, "timeout");
      description = Objects.requireNonNull(description, "description");
      whenToUse = List.copyOf(whenToUse);
      whenNotToUse = List.copyOf(whenNotToUse);
      examples = List.copyOf(examples);
    }

    /**
     * Renders the validated optional argument tail consumed by Saturn's existing command parser.
     */
    public String renderArguments(JsonObject arguments) {
      return arguments.has("arguments") ? arguments.get("arguments").getAsString().strip() : "";
    }
  }

  private record CommandProfile(Set<AgentCapability> requiredCapabilities, ToolEffect effect) {}
}
