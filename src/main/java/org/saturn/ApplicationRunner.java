package org.saturn;

import static java.util.concurrent.Executors.newScheduledThreadPool;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.saturn.app.facade.BotFacade;
import org.saturn.app.service.DataBaseConnection;
import org.saturn.app.service.LogService;
import org.saturn.app.service.impl.DataBaseConnectionImpl;
import org.saturn.app.service.impl.LogServiceImpl;
import org.saturn.app.util.Util;

public class ApplicationRunner {
    private final ScheduledExecutorService healthCheckScheduler = newScheduledThreadPool(1);

    private Configuration config;
    private final DataBaseConnection dbConnection;
    private final LogService internalService;

    public ApplicationRunner() {
        this.dbConnection = new DataBaseConnectionImpl();
        this.internalService = new LogServiceImpl(this.dbConnection.getConnection());

        Parameters params = new Parameters();
        FileBasedConfigurationBuilder<FileBasedConfiguration> builder = new FileBasedConfigurationBuilder<FileBasedConfiguration>(
                PropertiesConfiguration.class).configure(params.properties().setFileName("application.properties"));
        try {
            this.config = builder.getConfiguration();
        } catch (ConfigurationException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        ApplicationRunner applicationRunner = new ApplicationRunner();
        applicationRunner.start();
    }

    void start() {
        logStartBotEvent();
        runSaturnBot();
    }

    private void logStartBotEvent() {
        internalService.logEvent("appStart", "started", Util.getTimestampNow());
    }

    private void runSaturnBot() {

        BotFacade saturn = new BotFacade(dbConnection.getConnection(), config);
        saturn.isMainThread = true;
        saturn.joinDelay = 1000;

        saturn.start();

        healthCheckScheduler.scheduleWithFixedDelay(() -> {
            boolean isOffline = (Util.getTimestampNow() - saturn.lastPingTimestamp) > 30_000;
            if (isOffline) {
                System.out.println("Resurecting the bot..");
                saturn.stop(); //npe here
                saturn.sleep(15_000);
                
                saturn.start();
            } else {
                System.out.println(Util.getTimestampNow() + " Health check - online");
            }
        }, 0, 15, TimeUnit.SECONDS);
    }
}
