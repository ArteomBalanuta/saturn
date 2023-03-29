package org.saturn.app.facade;

import com.google.gson.Gson;
import org.apache.commons.configuration2.Configuration;
import org.saturn.app.model.impl.ChatMessage;
import org.saturn.app.model.impl.Connection;
import org.saturn.app.model.impl.User;

import java.nio.charset.StandardCharsets;
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
    protected final Gson gson = new Gson();
    
    protected Connection hcConnection;
    
    public String prefix;
    public String channel;
    public String nick;
    public String trip;
    public String userTrips;
    public String adminTrips;
    
    public boolean isMainThread;
    
    protected final BlockingQueue<String> incomingStringQueue = new ArrayBlockingQueue<>(256);
    protected final BlockingQueue<ChatMessage> incomingChatMessageQueue = new ArrayBlockingQueue<>(256);
    protected final BlockingQueue<String> outgoingMessageQueue = new ArrayBlockingQueue<>(256);
    
    protected CopyOnWriteArrayList<String> incomingSetOnlineMessageQueue = new CopyOnWriteArrayList<>();
    protected CopyOnWriteArrayList<User> currentChannelUsers = new CopyOnWriteArrayList<>();
    
    protected ExecutorService appExecutor = Executors.newFixedThreadPool(THREAD_NUMBER);
    protected ScheduledExecutorService executorScheduler = newScheduledThreadPool(THREAD_NUMBER);
    
    public Base(java.sql.Connection dbConnection, Configuration config) {
        if (config != null) {
            this.prefix = config.getString("cmdPrefix");
            this.channel = config.getString("channel");
            this.nick = config.getString("nick");
            this.trip = config.getString("trip");
            this.userTrips = config.getString("userTrips");
            this.adminTrips = config.getString("adminTrips");
        }
    }
    
    public void sendChatMessage(String message) {
        String chatPayload = String.format(CHAT_JSON, message);
        hcConnection.write(chatPayload);
    }
    
    public List<String> getIncomingSetOnlineMessageQueue() {
        return incomingSetOnlineMessageQueue;
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
}
