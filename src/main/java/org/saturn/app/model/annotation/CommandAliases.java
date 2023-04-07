package org.saturn.app.model.annotation;

public @interface CommandAliases {
    int currentRevision() default 1;
    String[] aliases();
}