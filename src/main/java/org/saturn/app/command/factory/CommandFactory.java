package org.saturn.app.command.factory;

import io.github.classgraph.AnnotationInfo;
import io.github.classgraph.AnnotationParameterValue;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommand;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.util.Util;

@Slf4j
public class CommandFactory {
  private static final ScanResult scanResult =
      new ClassGraph()
          .verbose(false)
          .disableNestedJarScanning()
          .enableAllInfo()
          .acceptPackages("org.saturn.app.command.impl")
          .scan();
  private static final Object commandCatalogMonitor = new Object();
  private static volatile List<CommandDefinition> commandCatalog;
  private static volatile boolean catalogLogged;
  private final EngineImpl engine;
  private final List<CommandDefinition> commandDefinitions;

  public CommandFactory(EngineImpl engine, Class<? extends Annotation> commandAnnotation) {
    this.engine = engine;
    this.commandDefinitions = getCommandCatalog(commandAnnotation);
    logCatalogIfNeeded();
  }

  public Optional<UserCommand> getCommand(ChatMessage message, String cmd) {
    Optional<CommandDefinition> first =
        commandDefinitions.stream()
            .filter(definition -> Util.checkAnagrams(cmd, definition.aliases()))
            .findFirst();

    if (first.isEmpty()) {
      log.warn("No implementation found for: {}", cmd);
      return Optional.empty();
    }

    try {
      CommandDefinition definition = first.get();
      Constructor<? extends UserCommand> constructor = definition.constructor();
      log.debug(
          "Found cmd implementation class, aliases: {}, [{}]",
          definition.className(),
          definition.aliases());
      return Optional.of(constructor.newInstance(this.engine, message, definition.aliases()));

    } catch (InvocationTargetException | InstantiationException | IllegalAccessException ex) {
      throw new RuntimeException(ex);
    }
  }

  protected List<CommandDefinition> getCommandCatalog(Class<? extends Annotation> annotation) {
    List<CommandDefinition> cached = commandCatalog;
    if (cached != null) {
      return cached;
    }

    synchronized (commandCatalogMonitor) {
      if (commandCatalog == null) {
        commandCatalog = loadCommandCatalog(annotation);
      }
      return commandCatalog;
    }
  }

  private List<CommandDefinition> loadCommandCatalog(Class<? extends Annotation> annotation) {
    List<CommandDefinition> definitions = new ArrayList<>();
    ClassInfoList classesWithAnnotation = scanResult.getClassesWithAnnotation(annotation);

    classesWithAnnotation.forEach(
        routeClassInfo -> {
          AnnotationInfo routeAnnotationInfo = routeClassInfo.getAnnotationInfo(annotation);
          List<AnnotationParameterValue> parameterValues = routeAnnotationInfo.getParameterValues();
          List<String[]> collect =
              parameterValues.stream()
                  .filter(s -> "aliases".equals(s.getName()))
                  .map(v -> (String[]) v.getValue())
                  .toList();

          try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            @SuppressWarnings("unchecked")
            Class<? extends UserCommand> commandClass =
                (Class<? extends UserCommand>) classLoader.loadClass(routeClassInfo.getName());
            @SuppressWarnings("unchecked")
            Constructor<? extends UserCommand> constructor =
                (Constructor<? extends UserCommand>) commandClass.getDeclaredConstructors()[0];
            definitions.add(
                new CommandDefinition(
                    routeClassInfo.getName(), Arrays.asList(collect.getFirst()), constructor));
          } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
          }
        });

    return List.copyOf(definitions);
  }

  private void logCatalogIfNeeded() {
    if (!engine.engineType.equals(EngineType.HOST) || catalogLogged) {
      return;
    }

    synchronized (commandCatalogMonitor) {
      if (catalogLogged) {
        return;
      }

      commandDefinitions.forEach(
          definition ->
              log.info(
                  "{} is annotated with aliases: {}",
                  definition.className(),
                  definition.aliases()));
      catalogLogged = true;
    }
  }

  private record CommandDefinition(
      String className, List<String> aliases, Constructor<? extends UserCommand> constructor) {}
}
