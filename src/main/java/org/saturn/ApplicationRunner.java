package org.saturn;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.service.DataBaseService;
import org.saturn.app.service.impl.DataBaseServiceImpl;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.Executors.newScheduledThreadPool;

@Slf4j
public class ApplicationRunner {
    private final ScheduledExecutorService healthCheckScheduler = newScheduledThreadPool(1);
    private final DataBaseService dbService;
    private Configuration config;
    private EngineImpl host;
    private boolean autoReconnectEnabled;

    public ApplicationRunner() {
        Parameters params = new Parameters();
        FileBasedConfigurationBuilder<FileBasedConfiguration> builder = new FileBasedConfigurationBuilder<FileBasedConfiguration>(PropertiesConfiguration.class)
                .configure(params.properties()
                        .setFileName("application.properties"));
        try {
            this.config = builder.getConfiguration();
            this.autoReconnectEnabled = Objects.equals(this.config.getString("autoReconnect"), "true");
        } catch (ConfigurationException e) {
            System.exit(1);
        }

        this.dbService = new DataBaseServiceImpl(this.config.getString("dbPath"));
    }

    public static void main(String[] args) {
        ApplicationRunner applicationRunner = new ApplicationRunner();
        log.warn("Running at user dir: {}", System.getProperty("user.dir"));
        applicationRunner.start();

        // Register a shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown initiated... Stopping services.");
            applicationRunner.stop();
            log.info("Shutdown complete.");
        }));
    }

    private void start() {
        if (autoReconnectEnabled) {
            log.info("Scheduling health check every 5 minutes");
            healthCheckScheduler.scheduleWithFixedDelay(this::healthCheck, 0, 5, TimeUnit.MINUTES);
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
                    log.debug("Health: Connected");
                    return;
                } else {
                    log.debug("Health: Connection is closed... Restarting the bot.");
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
        }
    }

    // Stop method to gracefully shut down the application
    private void stop() {
        log.info("Stop: Stopping the host and health check scheduler...");

        // Stop the host
        if (host != null) {
            try {
                host.stop();
                Thread.sleep(1_000);
                host = null;
                Runtime.getRuntime().gc();

                log.info("Stop: Stopped the host.");
            } catch (Exception e) {
                log.error("Error while stopping the host: ", e);
            }
        } else {
            log.warn("Stop: Bot is not set...");
        }

        // Shut down the scheduler
        healthCheckScheduler.shutdown();
        try {
            if (!healthCheckScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("Stop: Scheduler did not terminate in the specified time. Forcing shutdown...");
                healthCheckScheduler.shutdownNow();
            }
            log.info("Stop: Scheduler stopped.");
        } catch (InterruptedException e) {
            log.info("Error: {}", e.getMessage());
            log.error("Stack trace", e);
            Thread.currentThread().interrupt();  // Preserve interrupt status
            healthCheckScheduler.shutdownNow();
        }
    }
}