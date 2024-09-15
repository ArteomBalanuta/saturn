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
    public static final int CHECK_DELAY = 15;
    public static final int RESTART_DELAY_MILL = 15_000;
    private final ScheduledExecutorService healthCheckScheduler = newScheduledThreadPool(1);

    private Configuration config;
    private final DataBaseService dbService;
    private final LogRepository internalService;

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
        this.internalService = new LogRepositoryImpl(this.dbService.getConnection());
    }

    public static void main(String[] args) {
        ApplicationRunner applicationRunner = new ApplicationRunner();
        log.warn("Running at user dir: {}", System.getProperty("user.dir"));
        applicationRunner.start();
    }

    void start() {
        log.info("Starting application");
        Engine host = new EngineImpl(dbService.getConnection(), config, EngineType.HOST);
        host.start();
        if (host.isConnected()) {
            log.info("Application started");
        }

        if (Objects.equals(this.config.getString("autoReconnect"), "true")) {
            healthCheckScheduler.scheduleWithFixedDelay(() -> {
                if (host.isConnected()) {
                    return;
                }

                log.warn("Connection is closed.. Restarting the bot in 15 seconds.");
                host.stop();
                try {
                    Thread.sleep(RESTART_DELAY_MILL);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                host.start();
            }, 0, CHECK_DELAY, TimeUnit.SECONDS);
        }
    }
}