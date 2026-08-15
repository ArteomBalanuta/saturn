package org.saturn.app.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.classgraph.ClassGraph;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.saturn.app.command.annotation.CommandAliases;

class SaturnCommandToolCatalogTest {
  @Test
  void exposesOneClosedContractForEveryAnnotatedSaturnCommand() {
    var entries = SaturnCommandToolCatalog.entries();
    Set<String> handlerNames;
    try (var scan =
        new ClassGraph()
            .enableClassInfo()
            .enableAnnotationInfo()
            .acceptPackages("org.saturn.app.command.impl")
            .scan()) {
      handlerNames = Set.copyOf(scan.getClassesWithAnnotation(CommandAliases.class).getNames());
    }

    assertEquals(
        handlerNames,
        entries.stream()
            .map(entry -> entry.handlerType().getName())
            .collect(java.util.stream.Collectors.toSet()));
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
}
