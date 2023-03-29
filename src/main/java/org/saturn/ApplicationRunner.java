package org.saturn;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.saturn.app.facade.Engine;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.service.DataBaseConnectionService;
import org.saturn.app.service.LogService;
import org.saturn.app.service.impl.DataBaseConnectionServiceImpl;
import org.saturn.app.service.impl.LogServiceImpl;
import org.saturn.app.util.Util;

import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.Executors.newScheduledThreadPool;

public class ApplicationRunner {
    private final ScheduledExecutorService healthCheckScheduler = newScheduledThreadPool(1);

    private Configuration config;
    private final DataBaseConnectionService dbConnection;
    private final LogService internalService;

    public ApplicationRunner() {
        this.dbConnection = new DataBaseConnectionServiceImpl();
        this.internalService = new LogServiceImpl(this.dbConnection.getConnection(), false);

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
        Engine saturn = new EngineImpl(dbConnection.getConnection(), config, true);
        saturn.start();

//        healthCheckScheduler.scheduleWithFixedDelay(() -> {
//            boolean isOffline = (Util.getTimestampNow() - saturn.lastPingTimestamp) > 30_000;
//            if (isOffline) {
//                System.out.println(getUTCnow() + "Resurrecting the bot..");
//                saturn.stop(); //npe here
//                saturn.sleep(15_000);
//
//                saturn.start();
//            }
//        }, 0, 15, TimeUnit.SECONDS);
    }
}
