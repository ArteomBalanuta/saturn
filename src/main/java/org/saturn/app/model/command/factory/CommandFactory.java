package org.saturn.app.model.command.factory;

import io.github.classgraph.AnnotationInfo;
import io.github.classgraph.AnnotationParameterValue;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.command.UserCommand;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.util.Util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
public class CommandFactory {
    private static final ScanResult scanResult = new ClassGraph()
            .verbose(false)
            .disableNestedJarScanning()
            .enableAllInfo()         // Scan classes, methods, fields, annotations
            .acceptPackages("org.saturn.app.model.command.impl")     // Scan `pkg` and subpackages (omit to scan all packages)
            .scan();
    private final EngineImpl engine;
    private final Map<ClassInfo, String[]> aliasesMappedByClassInfo;

    public CommandFactory(EngineImpl engine, String commandImplPackage, Class<? extends Annotation> commandAnnotation) {
        this.engine = engine;
        this.aliasesMappedByClassInfo = getAliases(commandAnnotation);
    }
    public Optional<UserCommand> getCommand(ChatMessage message, String cmd) {
        AtomicReference<List<String>> aliases = new AtomicReference<>();

        Optional<Map.Entry<ClassInfo, String[]>> first = aliasesMappedByClassInfo.entrySet().stream()
                .peek(e -> aliases.set(Arrays.asList(e.getValue())))
                .filter(e ->  Util.checkAnagrams(cmd, Arrays.asList(e.getValue()))) // anagram check.
                .findFirst();

        if (first.isEmpty()) {
            log.warn("No implementation found for: {}", cmd);
            return Optional.empty();
        }

        ClassInfo info = first.get().getKey();
        try {
            Class<?> cl = Class.forName(info.getName());
            Constructor<?> declaredConstructor = cl.getDeclaredConstructors()[0];

            log.debug("Using implementation class, aliases: {}, [{}]",  info.getName(), aliases.get());
            return Optional.of((UserCommand) declaredConstructor.newInstance(this.engine, message, aliases.get()));

        } catch (ClassNotFoundException ex) {
            throw new RuntimeException(ex);
        } catch (InvocationTargetException ex) {
            throw new RuntimeException(ex);
        } catch (InstantiationException ex) {
            throw new RuntimeException(ex);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    protected Map<ClassInfo, String[]> getAliases(Class<? extends Annotation> annotation) {
        Map<ClassInfo, String[]> aliasesMappedByClassInfo = new HashMap<>();
        ClassInfoList classesWithAnnotation = scanResult.getClassesWithAnnotation(annotation);

        classesWithAnnotation.forEach(routeClassInfo -> {
            AnnotationInfo routeAnnotationInfo = routeClassInfo.getAnnotationInfo(annotation);
            List<AnnotationParameterValue> parameterValues = routeAnnotationInfo.getParameterValues();
            List<String[]> collect = parameterValues.stream()
                    .filter(s -> "aliases".equals(s.getName()))
                    .map(v -> (String[]) v.getValue())
                    .collect(Collectors.toList());

            String[] aliases = collect.get(0);
            aliasesMappedByClassInfo.put(routeClassInfo, aliases);

            if (engine.isMain) {
                log.info(routeClassInfo.getName() + " is annotated with aliases: {}", Arrays.toString(aliases));
            }
        });

        return aliasesMappedByClassInfo;
    }
}
