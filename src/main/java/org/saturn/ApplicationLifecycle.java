package org.saturn;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ApplicationLifecycle {
  private static final ApplicationLifecycle INSTANCE = new ApplicationLifecycle();
  private volatile ApplicationRunner runner;

  private ApplicationLifecycle() {}

  public static ApplicationLifecycle getInstance() {
    return INSTANCE;
  }

  public void bind(ApplicationRunner runner) {
    this.runner = runner;
  }

  public void restartHost() {
    ApplicationRunner boundRunner = requireRunner();
    boundRunner.restartHostNow();
  }

  public void shutdown() {
    ApplicationRunner boundRunner = requireRunner();
    boundRunner.stopApplication();
  }

  private ApplicationRunner requireRunner() {
    ApplicationRunner boundRunner = runner;
    if (boundRunner == null) {
      log.warn("Application lifecycle requested before runner was initialized");
      throw new IllegalStateException("Application runner is not initialized");
    }

    return boundRunner;
  }
}
