package org.saturn.app.facade;

import org.apache.commons.configuration2.Configuration;
import org.saturn.app.model.dto.User;
import org.saturn.app.service.AuthorizationService;
import org.saturn.app.service.LogRepository;
import org.saturn.app.service.MailService;
import org.saturn.app.service.ModService;
import org.saturn.app.service.NoteService;
import org.saturn.app.service.PingService;
import org.saturn.app.service.SCPService;
import org.saturn.app.service.SQLService;
import org.saturn.app.service.SearchService;
import org.saturn.app.service.UserService;
import org.saturn.app.service.WeatherService;
import org.saturn.app.service.impl.AuthorizationServiceImpl;
import org.saturn.app.service.impl.LogRepositoryImpl;
import org.saturn.app.service.impl.MailServiceImpl;
import org.saturn.app.service.impl.ModServiceImpl;
import org.saturn.app.service.impl.NoteServiceImpl;
import org.saturn.app.service.impl.OutService;
import org.saturn.app.service.impl.PingServiceImpl;
import org.saturn.app.service.impl.SCPServiceImpl;
import org.saturn.app.service.impl.SQLServiceImpl;
import org.saturn.app.service.impl.SearchServiceImpl;
import org.saturn.app.service.impl.UserServiceImpl;
import org.saturn.app.service.impl.WeatherServiceImpl;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class Base {
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
    public final LogRepository logRepository;
    public final SCPService scpService;
    public final NoteService noteService;
    public final SearchService searchService;
    public final MailService mailService;
    public final SQLService sqlService;
    public final PingService pingService;
    public final ModService modService;
    public final WeatherService weatherService;

    public final AuthorizationService authorizationService;

    public final UserService userService;

    public final BlockingQueue<String> outgoingMessageQueue = new ArrayBlockingQueue<>(256);
    public final BlockingQueue<String> outgoingRawMessageQueue = new ArrayBlockingQueue<>(256);
    public final CopyOnWriteArrayList<User> currentChannelUsers = new CopyOnWriteArrayList<>();
    public Configuration config;

    public Base(java.sql.Connection connection, Configuration config, Boolean isMain) {
        this.outService = new OutService(outgoingMessageQueue, outgoingRawMessageQueue);
        this.scpService = new SCPServiceImpl(outgoingMessageQueue);
        this.noteService = new NoteServiceImpl(connection, outgoingMessageQueue);
        this.mailService = new MailServiceImpl(connection, outgoingMessageQueue);
        this.sqlService = new SQLServiceImpl(connection, outgoingMessageQueue);
        this.pingService = new PingServiceImpl(outgoingMessageQueue);
        this.searchService = new SearchServiceImpl();                                       /* TODO:  add logging */
        this.modService = new ModServiceImpl(connection, outgoingMessageQueue, outgoingRawMessageQueue);
        this.userService = new UserServiceImpl(connection, outgoingMessageQueue);
        this.weatherService = new WeatherServiceImpl(outgoingMessageQueue);
        this.authorizationService = new AuthorizationServiceImpl(connection, outgoingMessageQueue);
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

        this.logRepository = new LogRepositoryImpl(connection);
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

    public Configuration getConfig() {
        return config;
    }

    public String getPrefix() { return prefix; }
}
