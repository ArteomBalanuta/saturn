package org.saturn.app.facade;

import com.google.gson.Gson;
import org.apache.commons.configuration2.Configuration;
import org.saturn.app.model.command.UserCommand;
import org.saturn.app.model.dto.ChatMessage;
import org.saturn.app.model.dto.Connection;
import org.saturn.app.model.dto.User;
import org.saturn.app.service.*;
import org.saturn.app.service.impl.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.Executors.newScheduledThreadPool;
import static org.saturn.app.util.Constants.CHAT_JSON;
import static org.saturn.app.util.Constants.THREAD_NUMBER;

public abstract class Base {
    protected List<UserCommand> enabledUserCommands = new ArrayList<>();
    protected static final Gson gson = new Gson();
    protected String baseWsURL = "wss://hack.chat/chat-ws";
    protected Connection hcConnection;
    protected boolean isMainThread;
    public String prefix;
    public String channel;
    public String nick;
    public String trip;
    public String isSql;
    public String userTrips;
    public String adminTrips;

    protected final OutService outService;
    protected final LogService logService;
    protected final SCPService scpService;
    protected final NoteService noteService;
    protected final SearchService searchService;
    protected final MailService mailService;
    protected final SQLService sqlService;
    protected final PingService pingService;
    protected final ModService modService;
    protected final WeatherService weatherService;

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

    protected final BlockingQueue<String> incomingStringQueue = new ArrayBlockingQueue<>(256);
    protected final BlockingQueue<ChatMessage> incomingChatMessageQueue = new ArrayBlockingQueue<>(256);
    protected final BlockingQueue<String> outgoingMessageQueue = new ArrayBlockingQueue<>(256);
    protected CopyOnWriteArrayList<User> currentChannelUsers = new CopyOnWriteArrayList<>();
    
    protected ExecutorService appExecutor = Executors.newFixedThreadPool(THREAD_NUMBER);
    protected ScheduledExecutorService executorScheduler = newScheduledThreadPool(THREAD_NUMBER);
    protected Configuration config;

    public Base(java.sql.Connection dbConnection, Configuration config, Boolean isMainThread) {
        this.outService = new OutService(outgoingMessageQueue);
        this.scpService = new SCPServiceImpl(outgoingMessageQueue);
        this.noteService = new NoteServiceImpl(dbConnection, outgoingMessageQueue);
        this.mailService = new MailServiceImpl(dbConnection, outgoingMessageQueue);
        this.sqlService = new SQLServiceImpl(dbConnection, outgoingMessageQueue);
        this.pingService = new PingServiceImpl(outgoingMessageQueue);
        this.searchService = new SearchServiceImpl();                                       /* TODO:  add logging */
        this.modService = new ModServiceImpl(this.sqlService, outgoingMessageQueue);
        this.weatherService = new WeatherServiceImpl(outgoingMessageQueue);
        this.isMainThread = isMainThread;
        this.config = config;

        if (isMainThread && config != null) {
            this.isSql = config.getString("isSql");
            this.prefix = config.getString("cmdPrefix");
            this.channel = config.getString("channel");
            this.nick = config.getString("nick");
            this.trip = config.getString("trip");
            this.userTrips = config.getString("userTrips");
            this.adminTrips = config.getString("adminTrips");
        }

        this.logService = new LogServiceImpl(dbConnection, Boolean.parseBoolean(this.isSql));
    }
    
    public void sendChatMessage(String message) {
        String chatPayload = String.format(CHAT_JSON, message);
        hcConnection.write(chatPayload);
    }
    
    public void setChannel(String chanel) {
        this.channel = chanel;
    }
    
    public void setNick(String nick) {
        this.nick = nick;
    }
    
    public void setTrip(String trip) {
        this.trip = trip;
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
