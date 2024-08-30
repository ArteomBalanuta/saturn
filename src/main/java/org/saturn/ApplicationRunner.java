package org.saturn;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.saturn.app.facade.Engine;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.service.DataBaseService;
import org.saturn.app.service.LogService;
import org.saturn.app.service.impl.DataBaseServiceImpl;
import org.saturn.app.service.impl.DataBaseLogServiceImpl;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.Executors.newScheduledThreadPool;

@Slf4j
public class ApplicationRunner {
    private final ScheduledExecutorService healthCheckScheduler = newScheduledThreadPool(1);

    private Configuration config;
    private final DataBaseService dbConnection;
    private final LogService internalService;

    public ApplicationRunner() {
        Parameters params = new Parameters();
        FileBasedConfigurationBuilder<FileBasedConfiguration> builder = new FileBasedConfigurationBuilder<FileBasedConfiguration>(PropertiesConfiguration.class)
                        .configure(params.properties()
                        .setFileName("application.properties"));
        try {
            this.config = builder.getConfiguration();
        } catch (ConfigurationException e) {
            e.printStackTrace();
        }

        this.dbConnection = new DataBaseServiceImpl(this.config.getString("dbPath"));
        this.internalService = new DataBaseLogServiceImpl(this.dbConnection.getConnection(), false);
    }

    public static void main(String[] args) {
        ApplicationRunner applicationRunner = new ApplicationRunner();
        applicationRunner.start();
    }

    void start() {
        log.info("Starting application");
        Engine saturn = new EngineImpl(dbConnection.getConnection(), config, true);
        saturn.start();
        if (saturn.isConnected()) {
            log.info("Application started");
        };

        if (Objects.equals(this.config.getString("autoReconnect"), "true")) {
            healthCheckScheduler.scheduleWithFixedDelay(() -> {
                if (saturn.isConnected()) {
                    return;
                }

                log.warn("Connection is closed.. Restarting the bot in 15 seconds.");
                saturn.stop();
                try {
                    Thread.sleep(15_000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                saturn.start();
            }, 0, 15, TimeUnit.SECONDS);
        }
    }
}