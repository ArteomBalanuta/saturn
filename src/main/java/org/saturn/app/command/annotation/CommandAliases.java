package org.saturn.app.command.annotation;

public @interface CommandAliases {
  int currentRevision() default 1;

  String[] aliases();
}
