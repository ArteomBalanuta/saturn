package org.saturn.app.model.command;

import io.github.classgraph.*;
import org.saturn.app.facade.impl.EngineImpl;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class CommandFactory {

    private final EngineImpl engine;
    private final Map<ClassInfo, String[]> aliasesMappedByClassInfo;

    public CommandFactory(EngineImpl engine, String commandImplPackage, Class<? extends Annotation> commandAnnotation) {
        this.engine = engine;
        this.aliasesMappedByClassInfo = getAliases(commandImplPackage, commandAnnotation);
    }

    public UserCommand getCommand(String cmd) {
        AtomicReference<List<String>> aliases = new AtomicReference<>();

        Optional<Map.Entry<ClassInfo, String[]>> first = aliasesMappedByClassInfo.entrySet().stream()
                .peek(e -> aliases.set(Arrays.asList(e.getValue())))
                .filter(e -> Arrays.asList(e.getValue()).contains(cmd))
                .findFirst();

        if (first.isEmpty()) {
            throw new RuntimeException("Cant find command implementation for command: " + cmd);
        }

        ClassInfo info = first.get().getKey();
        try {
            Class<?> cl = Class.forName(info.getName());
            Constructor<?> declaredConstructor = cl.getDeclaredConstructors()[0];

            UserCommand userCommand = (UserCommand) declaredConstructor.newInstance(this.engine, aliases.get());
            return userCommand;

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

    protected Map<ClassInfo, String[]> getAliases(String pkg, Class<? extends Annotation> annotation) {
        try (ScanResult scanResult =
                     new ClassGraph()
                             .verbose(false)          // Log to stderr
                             .enableAllInfo()         // Scan classes, methods, fields, annotations
                             .acceptPackages(pkg)     // Scan `pkg` and subpackages (omit to scan all packages)
                             .scan()) {               // Start the scan
            Map<ClassInfo, String[]> aliasesMappedByClassInfo = new HashMap<>();
            for (ClassInfo routeClassInfo : scanResult.getClassesWithAnnotation(annotation)) {
                AnnotationInfo routeAnnotationInfo = routeClassInfo.getAnnotationInfo(annotation);
                List<AnnotationParameterValue> parameterValues = routeAnnotationInfo.getParameterValues();
                List<String[]> collect = parameterValues.stream()
                        .filter(s -> "aliases".equals(s.getName()))
                        .map(v -> (String[]) v.getValue())
                        .collect(Collectors.toList());

                String[] aliases = collect.get(0);
                aliasesMappedByClassInfo.put(routeClassInfo, aliases);

                System.out.println(routeClassInfo.getName() + " is annotated with aliases: " + Arrays.toString(aliases));
            }

            return aliasesMappedByClassInfo;
        }
    }
}
