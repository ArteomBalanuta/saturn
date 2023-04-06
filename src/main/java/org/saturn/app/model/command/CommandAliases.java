package org.saturn.app.model.command;

public @interface CommandAliases {
    int currentRevision() default 1;
    String[] aliases();
}