package org.saturn;

import static java.util.concurrent.Executors.newScheduledThreadPool;

import com.moandjiezana.toml.Toml;
import java.io.File;
import java.util.HashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.service.DataBaseService;
import org.saturn.app.service.impl.DataBaseServiceImpl;

@Slf4j
public class ApplicationRunner {
  public static ApplicationRunner applicationRunner;
  private static ScheduledExecutorService healthCheckScheduler = newScheduledThreadPool(1);
  private final DataBaseService dbService;
  private final Toml config;
  private static EngineImpl host;
  private boolean autoReconnectEnabled;
  private long healthCheckInterval;

  public ApplicationRunner() {
    File tomlFile = new File("config.toml");
    this.config = new Toml().read(tomlFile);

    try {
      this.autoReconnectEnabled = config.getBoolean("autoReconnect");
      this.healthCheckInterval = config.getLong("healthCheckInterval");
    } catch (Exception e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace", e);

      System.exit(1);
    }

    this.dbService = new DataBaseServiceImpl(this.config.getString("dbPath"));
  }

  public static void main(String[] args) {
    applicationRunner = new ApplicationRunner();
    log.warn("Running at user dir: {}", System.getProperty("user.dir"));
    applicationRunner.start();

    // Register a shutdown hook for graceful shutdown
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  log.info("Shutdown initiated... Stopping services.");
                  applicationRunner.stopApplication();
                  log.info("Shutdown complete.");
                }));
  }

  public void start() {
    if (autoReconnectEnabled) {
      log.info("Scheduling health check every: {} minutes", healthCheckInterval);
      if (healthCheckScheduler.isShutdown()) {
        healthCheckScheduler = newScheduledThreadPool(1);
        log.info("Set new scheduler");
      }

      healthCheckScheduler.scheduleAtFixedRate(
          this::healthCheck, 0, healthCheckInterval, TimeUnit.MINUTES);
    } else {
      log.warn("AutoReconnect is disabled..");
      log.info("Starting application manually");
      host = new EngineImpl(dbService.getConnection(), config, EngineType.HOST);
      /* setting host reference, so each replica can access it through static reference */
      host.setHostRef(host);
      host.start();
    }
  }

  private void healthCheck() {
    log.info("Health: performing health check...");
    try {
      if (host != null) {
        if (host.isConnected()) {
          log.info("Health: Connected");
          return;
        } else {
          log.info("Health: Connection is closed... Restarting the bot.");
        }

        /* try gracefully */
        host.stop();
        Thread.sleep(1_000);

        /* nullify the bot */
        host = null;
        Thread.sleep(1_000);
        Runtime.getRuntime().gc();
      } else {
        log.warn("Health: Bot is not set");
      }

      /* reset */
      host = new EngineImpl(dbService.getConnection(), config, EngineType.HOST);
      /* setting host reference, so each replica can access it through static reference */
      host.setHostRef(host);
      host.start();
      log.warn("Health: Bot has been restarted");
    } catch (Exception e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace", e);
      host = null;
      Runtime.getRuntime().gc();
    } finally {
      log.info("Health: finished checking");
    }
  }

  // Stop method to gracefully shut down the application
  public void stopBot() {
    log.info("Stop: Disconnecting the bot");

    // Stop the host
    if (host != null) {
      try {
        host.stop();
        Thread.sleep(1_000);
        host = null;
        Runtime.getRuntime().gc();

        log.info("Stop: Stopped the bot");
      } catch (Exception e) {
        log.error("Error while stopping the bot: ", e);
      }
    } else {
      log.warn("Stop: Engine reference is nullified.");
    }
  }

  public void stopApplication() {
    log.info("Stop: Stopping the health check scheduler and application..");

    // Shut down the scheduler
    healthCheckScheduler.shutdown();
    try {
      if (!healthCheckScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
        log.warn("Stop: Scheduler did not terminate in the specified time. Forcing shutdown...");
        healthCheckScheduler.shutdownNow();
      }
      log.info("Stop: Scheduler stopped.");

      stopBot();

    } catch (InterruptedException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace", e);
      Thread.currentThread().interrupt(); // Preserve interrupt status
      healthCheckScheduler.shutdownNow();
    }
  }
}
