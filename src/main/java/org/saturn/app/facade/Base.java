package org.saturn.app.facade;

import org.apache.commons.configuration2.Configuration;
import org.saturn.app.model.command.UserCommand;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.*;
import org.saturn.app.service.impl.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.Executors.newScheduledThreadPool;
import static org.saturn.app.util.Constants.THREAD_NUMBER;

public abstract class Base {
    protected List<UserCommand> enabledUserCommands = new ArrayList<>();
    protected String baseWsURL;
    public String proxies;
    public boolean isMain;
    public String prefix;
    public String channel;
    public String nick;
    public String password;
    public String isSql;
    public String userTrips;
    public String adminTrips;
    public String dbPath;

    public final OutService outService;
    public final LogService logService;
    public final SCPService scpService;
    public final NoteService noteService;
    public final SearchService searchService;
    public final MailService mailService;
    public final SQLService sqlService;
    public final PingService pingService;
    public final ModService modService;
    public final WeatherService weatherService;

    public final UserService userService;

    public PingService getPingService() {
        return pingService;
    }

    public ModService getModService() {
        return modService;
    }

    public WeatherService getWeatherService() {
        return weatherService;
    }

    public List<UserCommand> getEnabledCommands() {
        return enabledUserCommands;
    }

    public final BlockingQueue<String> incomingStringQueue = new ArrayBlockingQueue<>(256);
    public final BlockingQueue<ChatMessage> incomingChatMessageQueue = new ArrayBlockingQueue<>(256);
    public final BlockingQueue<String> outgoingMessageQueue = new ArrayBlockingQueue<>(256);
    public final BlockingQueue<String> outgoingRawMessageQueue = new ArrayBlockingQueue<>(256);
    public CopyOnWriteArrayList<User> currentChannelUsers = new CopyOnWriteArrayList<>();

    public ScheduledExecutorService executorScheduler = newScheduledThreadPool(THREAD_NUMBER);
    public Configuration config;

    public Base(java.sql.Connection dbConnection, Configuration config, Boolean isMain) {
        this.outService = new OutService(outgoingMessageQueue, outgoingRawMessageQueue);
        this.scpService = new SCPServiceImpl(outgoingMessageQueue);
        this.noteService = new NoteServiceImpl(dbConnection, outgoingMessageQueue);
        this.mailService = new MailServiceImpl(dbConnection, outgoingMessageQueue);
        this.sqlService = new SQLServiceImpl(dbConnection, outgoingMessageQueue);
        this.pingService = new PingServiceImpl(outgoingMessageQueue);
        this.searchService = new SearchServiceImpl();                                       /* TODO:  add logging */
        this.modService = new ModServiceImpl(this.sqlService, outgoingMessageQueue, outgoingRawMessageQueue);
        this.userService = new UserServiceImpl(dbConnection, outgoingMessageQueue);
        this.weatherService = new WeatherServiceImpl(outgoingMessageQueue);
        this.isMain = isMain;
        this.config = config;

        if (isMain && config == null) {
            throw new RuntimeException("Configuration isn't set for main thread.");
        }

        /* master */
        if (isMain) {
            this.isSql = config.getString("isSqlEnabled");
            this.prefix = config.getString("cmdPrefix");
            this.channel = config.getString("channel");
            this.nick = config.getString("nick");
            this.password = config.getString("trip");
            this.userTrips = config.getString("userTrips");
            this.adminTrips = config.getString("adminTrips");
            this.dbPath = config.getString("dbPath");
            this.baseWsURL = config.getString("wsUrl");
            this.proxies = config.getString("proxies");
        }

        /* slave */
        if (!isMain && config != null) {
            this.baseWsURL = config.getString("wsUrl");
            this.proxies = config.getString("proxies");
            this.password = config.getString("trip");
        }

        this.logService = new DataBaseLogServiceImpl(dbConnection, Boolean.parseBoolean(this.isSql));
    }
    
    public void setChannel(String chanel) {
        this.channel = chanel;
    }
    
    public void setNick(String nick) {
        this.nick = nick;
    }
    
    public void setTrip(String password) {
        this.password = password;
    }

    public OutService getOutService() {
        return outService;
    }

    public LogService getLogService() {
        return logService;
    }

    public SCPService getScpService() {
        return scpService;
    }

    public NoteService getNoteService() {
        return noteService;
    }

    public SearchService getSearchService() {
        return searchService;
    }

    public MailService getMailService() {
        return mailService;
    }

    public SQLService getSqlService() {
        return sqlService;
    }

    public Configuration getConfig() {
        return config;
    }
}
