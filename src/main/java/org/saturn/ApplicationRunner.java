package org.saturn;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.saturn.app.facade.Engine;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.service.DataBaseService;
import org.saturn.app.service.LogRepository;
import org.saturn.app.service.impl.DataBaseServiceImpl;
import org.saturn.app.service.impl.LogRepositoryImpl;

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


    public ApplicationRunner() {
        Parameters params = new Parameters();
        FileBasedConfigurationBuilder<FileBasedConfiguration> builder = new FileBasedConfigurationBuilder<FileBasedConfiguration>(PropertiesConfiguration.class)
                        .configure(params.properties()
                        .setFileName("application.properties"));
        try {
            this.config = builder.getConfiguration();
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

    void start() {
        log.info("Starting application");
        host = new EngineImpl(dbService.getConnection(), config, EngineType.HOST);
        /* setting host reference, so each replica can access it through static reference */
        host.setHostRef(host);
        host.start();

        if (host.isConnected()) {
            log.info("Application started");
        }

        if (Objects.equals(this.config.getString("autoReconnect"), "true")) {
            healthCheckScheduler.scheduleWithFixedDelay(() -> {
                try {
                    if (host.isConnected()) {
                        return;
                    }

                    log.warn("Connection is closed... Restarting the bot.");
                    host.stop();
                    host.start();
                } catch (Exception e) {
                    log.error("An unexpected error occurred while restarting the bot: ", e);
                }
            }, 0, 5, TimeUnit.MINUTES);
        } else {
            log.warn("autoReconnect is disabled... Exiting.");
        }
    }

    // Stop method to gracefully shut down the application
    void stop() {
        log.info("Stopping the host and health check scheduler...");

        // Stop the host
        if (host != null) {
            try {
                host.stop();
                log.info("Host stopped.");
            } catch (Exception e) {
                log.error("Error while stopping the host: ", e);
            }
        }

        // Shut down the scheduler
        healthCheckScheduler.shutdown();
        try {
            if (!healthCheckScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("Scheduler did not terminate in the specified time. Forcing shutdown...");
                healthCheckScheduler.shutdownNow();
            }
            log.info("Scheduler stopped.");
        } catch (InterruptedException e) {
            log.error("Error while shutting down the scheduler: ", e);
            Thread.currentThread().interrupt();  // Preserve interrupt status
            healthCheckScheduler.shutdownNow();
        }
    }
}