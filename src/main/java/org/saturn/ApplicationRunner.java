package org.saturn;

import static java.util.concurrent.Executors.newScheduledThreadPool;

import com.moandjiezana.toml.Toml;
import java.io.File;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.service.DataBaseService;
import org.saturn.app.service.impl.DataBaseServiceImpl;

@Slf4j
public class ApplicationRunner {
  private static final long STOP_WAIT_TIMEOUT_SECONDS = 10;
  private static final long BOT_STOP_DELAY_MILLIS = 1_000;
  private static ScheduledExecutorService healthCheckScheduler = newScheduledThreadPool(1);
  private final DataBaseService dbService;
  private final Toml config;
  private final boolean autoReconnectEnabled;
  private final long healthCheckInterval;
  private final Object lifecycleLock = new Object();
  private static EngineImpl host;

  public ApplicationRunner() {
    this(loadConfig());
  }

  ApplicationRunner(Toml config) {
    log.info("Running at user dir: {}", System.getProperty("user.dir"));
    this.config = config;
    this.autoReconnectEnabled = readRequiredBoolean(config, "autoReconnect");
    this.healthCheckInterval = readRequiredLong(config, "healthCheckInterval");
    this.dbService = new DataBaseServiceImpl(this.config.getString("dbPath"));
  }

  public static void main(String[] args) {
    ApplicationRunner runner = new ApplicationRunner();
    ApplicationLifecycle.getInstance().bind(runner);
    runner.start();
    Runtime.getRuntime().addShutdownHook(new Thread(runner::shutdownHook));
  }

  public void start() {
    if (autoReconnectEnabled) {
      scheduleHealthChecks();
      return;
    }

    log.warn("AutoReconnect is disabled.");
    startHost();
  }

  private void healthCheck() {
    log.info("Health: performing health check...");
    try {
      synchronized (lifecycleLock) {
        if (isHostHealthy()) {
          log.info("Health: Connected");
          return;
        }

        if (host == null) {
          log.warn("Health: Bot is not set");
        } else {
          log.info("Health: Connection is closed... Restarting the bot.");
        }

        restartHost();
        log.warn("Health: Bot has been restarted");
      }
    } catch (Exception e) {
      logFailure(e);
      host = null;
      Runtime.getRuntime().gc();
    } finally {
      log.info("Health: finished checking");
    }
  }

  // Stop method to gracefully shut down the application
  public void stopBot() {
    log.info("Stop: Disconnecting the bot");
    synchronized (lifecycleLock) {
      if (host == null) {
        log.warn("Stop: Engine reference is nullified.");
        return;
      }

      stopHostGracefully();
    }
  }

  public void stopApplication() {
    log.info("Stop: Stopping the health check scheduler and application..");
    healthCheckScheduler.shutdown();
    try {
      if (!healthCheckScheduler.awaitTermination(STOP_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        log.warn("Stop: Scheduler did not terminate in the specified time. Forcing shutdown...");
        healthCheckScheduler.shutdownNow();
      }
      log.info("Stop: Scheduler stopped.");

      stopBot();

    } catch (InterruptedException e) {
      logFailure(e);
      Thread.currentThread().interrupt();
      healthCheckScheduler.shutdownNow();
    }
  }

  private void scheduleHealthChecks() {
    log.info("Scheduling health check every: {} minutes", healthCheckInterval);
    ensureScheduler();
    healthCheckScheduler.scheduleAtFixedRate(
        this::healthCheck, 0, healthCheckInterval, TimeUnit.MINUTES);
  }

  private void ensureScheduler() {
    if (!healthCheckScheduler.isShutdown()) {
      return;
    }

    healthCheckScheduler = newScheduledThreadPool(1);
    log.info("Set new scheduler");
  }

  private boolean isHostHealthy() {
    return host != null && host.isConnected();
  }

  private void startHost() {
    log.info("Starting application manually");
    synchronized (lifecycleLock) {
      host = createHostEngine();
      host.start();
    }
  }

  private void restartHost() throws InterruptedException {
    stopHostIfRunning();
    host = createHostEngine();
    host.start();
  }

  public void restartHostNow() {
    synchronized (lifecycleLock) {
      try {
        restartHost();
        log.info("Restart: Host and replicas restarted cleanly");
      } catch (InterruptedException e) {
        logFailure(e);
        Thread.currentThread().interrupt();
      }
    }
  }

  private EngineImpl createHostEngine() {
    EngineImpl engine = new EngineImpl(dbService.getConnection(), config, EngineType.HOST);
    engine.setHostRef(engine);
    return engine;
  }

  private void stopHostIfRunning() throws InterruptedException {
    if (host == null) {
      return;
    }

    host.stop();
    host.setHostRef(null);
    pauseForShutdown();
    host = null;
    pauseForShutdown();
    Runtime.getRuntime().gc();
  }

  private void stopHostGracefully() {
    try {
      stopHostIfRunning();
      log.info("Stop: Stopped the bot");
    } catch (Exception e) {
      log.error("Error while stopping the bot: ", e);
    }
  }

  private void pauseForShutdown() throws InterruptedException {
    Thread.sleep(BOT_STOP_DELAY_MILLIS);
  }

  private void shutdownHook() {
    log.info("Shutdown initiated... Stopping services.");
    stopApplication();
    log.info("Shutdown complete.");
  }

  private static Toml loadConfig() {
    File tomlFile = new File("config.toml");
    return new Toml().read(tomlFile);
  }

  private static boolean readRequiredBoolean(Toml config, String key) {
    try {
      return config.getBoolean(key);
    } catch (Exception e) {
      logFailure(e);
      System.exit(1);
      throw new IllegalStateException("Unreachable");
    }
  }

  private static long readRequiredLong(Toml config, String key) {
    try {
      return config.getLong(key);
    } catch (Exception e) {
      logFailure(e);
      System.exit(1);
      throw new IllegalStateException("Unreachable");
    }
  }

  private static void logFailure(Exception e) {
    log.warn("Error: {}", e.getMessage());
    log.error("Stack trace", e);
  }
}
