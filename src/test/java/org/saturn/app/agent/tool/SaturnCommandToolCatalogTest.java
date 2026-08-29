package org.saturn.app.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import io.github.classgraph.ClassGraph;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.api.AgentCapability;
import org.saturn.app.command.annotation.CommandAliases;

class SaturnCommandToolCatalogTest {
  private static final String USER_PACKAGE = "org.saturn.app.command.impl.user";
  private static final String MODERATOR_PACKAGE = "org.saturn.app.command.impl.moderator";
  private static final String IMPLEMENTATION_PACKAGE = "org.saturn.app.command.impl";

  @Test
  void exposesOneClosedContractForEveryAnnotatedUserAndModeratorCommand() {
    var entries = SaturnCommandToolCatalog.entries();
    Set<String> expectedHandlerNames = annotatedHandlerNames(USER_PACKAGE, MODERATOR_PACKAGE);
    Set<String> actualHandlerNames =
        entries.stream().map(entry -> entry.handlerType().getName()).collect(Collectors.toSet());

    assertEquals(expectedHandlerNames, actualHandlerNames);
    assertEquals(expectedHandlerNames.size(), entries.size());
    assertEquals(
        entries.size(),
        entries.stream()
            .map(SaturnCommandToolCatalog.CommandToolDefinition::handlerType)
            .distinct()
            .count());
    assertEquals(
        entries.size(),
        entries.stream()
            .map(SaturnCommandToolCatalog.CommandToolDefinition::toolName)
            .distinct()
            .count());
    assertTrue(entries.stream().allMatch(entry -> entry.aliases().contains(entry.commandAlias())));
    assertTrue(
        entries.stream()
            .allMatch(
                entry -> entry.parameters().get("additionalProperties").getAsBoolean() == false));
    assertTrue(entries.stream().allMatch(entry -> !entry.whenNotToUse().isEmpty()));
    assertFalse(entries.isEmpty());
  }

  @Test
  void exposesOnlyAllowlistedHandlersAndRetainsModeratorProfiles() {
    var entries = SaturnCommandToolCatalog.entries();
    var entryTypes = entries.stream().map(entry -> entry.handlerType().getName()).toList();
    var nonAllowlistedHandlers =
        annotatedHandlerNames(IMPLEMENTATION_PACKAGE).stream()
            .filter(name -> !name.startsWith(USER_PACKAGE + "."))
            .filter(name -> !name.startsWith(MODERATOR_PACKAGE + "."))
            .toList();

    assertTrue(
        entries.stream()
            .allMatch(
                entry ->
                    entry.handlerType().getPackageName().equals(USER_PACKAGE)
                        || entry.handlerType().getPackageName().equals(MODERATOR_PACKAGE)));
    assertTrue(nonAllowlistedHandlers.stream().noneMatch(entryTypes::contains));
    assertTrue(entryTypes.stream().noneMatch(name -> name.contains(".impl.admin.")));
    assertTrue(entryTypes.stream().noneMatch(name -> name.contains(".impl.dbz.")));
    assertTrue(entryTypes.contains(MODERATOR_PACKAGE + ".BanUserCommandImpl"));
    assertTrue(entryTypes.contains(MODERATOR_PACKAGE + ".MuteUserCommandImpl"));
    assertTrue(
        entries.stream()
            .filter(entry -> entry.handlerType().getPackageName().equals(MODERATOR_PACKAGE))
            .anyMatch(
                entry -> entry.requiredCapabilities().contains(AgentCapability.PERMANENT_BAN)));
    assertTrue(
        entries.stream()
            .filter(entry -> entry.handlerType().getPackageName().equals(MODERATOR_PACKAGE))
            .anyMatch(
                entry ->
                    entry.requiredCapabilities().contains(AgentCapability.MODERATION_COMMANDS)));
  }

  private static Set<String> annotatedHandlerNames(String... packages) {
    try (var scan =
        new ClassGraph().enableClassInfo().enableAnnotationInfo().acceptPackages(packages).scan()) {
      return Set.copyOf(scan.getClassesWithAnnotation(CommandAliases.class).getNames());
    }
  }

  @Test
  void rendersOptionalArgumentsAndDefaultsWhenAbsent() {
    var definition = SaturnCommandToolCatalog.entries().getFirst();
    JsonObject arguments = new JsonObject();
    arguments.addProperty("arguments", "  --value  ");

    assertEquals("--value", definition.renderArguments(arguments));
    assertEquals("", definition.renderArguments(new JsonObject()));
  }
}
