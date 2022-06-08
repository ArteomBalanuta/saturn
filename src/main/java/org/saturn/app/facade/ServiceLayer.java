package org.saturn.app.facade;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.RandomStringUtils;
import org.saturn.app.model.WebSocketFrame;
import org.saturn.app.model.impl.ChatMessage;
import org.saturn.app.model.impl.Mail;
import org.saturn.app.model.impl.User;
import org.saturn.app.model.impl.WebSocketStandardFrameImpl;
import org.saturn.app.service.LogService;
import org.saturn.app.service.MailService;
import org.saturn.app.service.ModService;
import org.saturn.app.service.NoteService;
import org.saturn.app.service.PingService;
import org.saturn.app.service.SCPService;
import org.saturn.app.service.SQLService;
import org.saturn.app.service.SearchService;
import org.saturn.app.service.impl.LogServiceImpl;
import org.saturn.app.service.impl.MailServiceImpl;
import org.saturn.app.service.impl.ModServiceImpl;
import org.saturn.app.service.impl.NoteServiceImpl;
import org.saturn.app.service.impl.OutService;
import org.saturn.app.service.impl.PingServiceImpl;
import org.saturn.app.service.impl.SCPServiceImpl;
import org.saturn.app.service.impl.SQLServiceImpl;
import org.saturn.app.service.impl.SearchServiceImpl;
import org.saturn.app.util.Cmd;
import org.saturn.app.util.Constants;

import java.io.IOException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.saturn.app.util.Cmd.BABAKIUERIA;
import static org.saturn.app.util.Cmd.BAN;
import static org.saturn.app.util.Cmd.DRRUDI;
import static org.saturn.app.util.Cmd.FISH;
import static org.saturn.app.util.Cmd.HELP;
import static org.saturn.app.util.Cmd.LIST;
import static org.saturn.app.util.Cmd.MAIL;
import static org.saturn.app.util.Cmd.MSG_CHANNEL;
import static org.saturn.app.util.Cmd.NOTE;
import static org.saturn.app.util.Cmd.NOTES;
import static org.saturn.app.util.Cmd.NOTES_PURGE;
import static org.saturn.app.util.Cmd.PING;
import static org.saturn.app.util.Cmd.RUST;
import static org.saturn.app.util.Cmd.SCP;
import static org.saturn.app.util.Cmd.SEARCH;
import static org.saturn.app.util.Cmd.SENTRY;
import static org.saturn.app.util.Cmd.SOLID;
import static org.saturn.app.util.Cmd.SQL;
import static org.saturn.app.util.Constants.HELP_RESPONSE;
import static org.saturn.app.util.Constants.JOIN_JSON;
import static org.saturn.app.util.Constants.UPGRADE_REQUEST;
import static org.saturn.app.util.Util.getCmdFromJson;
import static org.saturn.app.util.Util.getTimestampNow;
import static org.saturn.app.util.Util.is;

public class ServiceLayer extends Base {
    protected final OutService outService;
    
    protected final LogService logService;
    protected final SCPService scpService;
    protected final NoteService noteService;
    protected final SearchService searchService;
    protected final MailService mailService;
    protected final SQLService sqlService;
    protected final PingService pingService;
    protected final ModService modService;
    
    public ServiceLayer(Connection dbConnection, Configuration config
    ) {
        super(dbConnection, config);
        this.outService = new OutService(outgoingMessageQueue);
        this.scpService = new SCPServiceImpl(outgoingMessageQueue);
        this.noteService = new NoteServiceImpl(dbConnection, outgoingMessageQueue);
        this.mailService = new MailServiceImpl(dbConnection, outgoingMessageQueue);
        this.sqlService = new SQLServiceImpl(dbConnection, outgoingMessageQueue);
        this.pingService = new PingServiceImpl(outgoingMessageQueue);
        this.logService = new LogServiceImpl(dbConnection);
        this.searchService = new SearchServiceImpl();           /* TODO:  add logging */
        this.modService = new ModServiceImpl(dbConnection, outgoingMessageQueue);
    }
    
    public void start() {
        setUpConnectionToHackChat();
        setupWorkers();
        sendUpgradeRequest();
        
        sleep(this.joinDelay);
        sendJoinMessage();
    }
    
    public void setUpConnectionToHackChat() {
        String uri = "hack.chat";
        int port = 443;
        hcConnection = new org.saturn.app.model.impl.Connection(uri, port, this.isMainThread);
    }
    
    public void sendUpgradeRequest() {
        try {
            hcConnection.write(UPGRADE_REQUEST.getBytes(UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /*TODO: make sure join payload size always fits standard frame size */
    public void sendJoinMessage() {
        String joinPayload = String.format(JOIN_JSON, channel, nick, trip);
        WebSocketFrame joinFrame = new WebSocketStandardFrameImpl(joinPayload);
        
        try {
            hcConnection.write(joinFrame.getWebSocketWriteTextBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void shareMessages() {
        if (!outgoingMessageQueue.isEmpty()) {
            sendChatMessage(outgoingMessageQueue.poll());
        }
    }
    
    public void stop() {
        try {
            this.appExecutor.shutdownNow();
            this.executorScheduler.shutdownNow();
            
            this.hcConnection.close();
            this.hcConnection = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void setupWorkers() {
        initExecutors();
        executorScheduler.scheduleWithFixedDelay(this::websocketFrameDispatcher, 0, 50, TimeUnit.MILLISECONDS);
        
        executorScheduler.scheduleWithFixedDelay(() -> {
            try {
                messageDispatcher();
                messageProcessor();
                shareMessages();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
    }
    
    private void messageDispatcher() {
        if (!incomingFramesQueue.isEmpty()) {
            WebSocketFrame frame = incomingFramesQueue.poll();
            String jsonText = new String(frame.getWebSocketReadTextBytes());
            
            String cmd = getCmdFromJson(jsonText);
            switch (cmd) {
                case "join": {
                    break;
                }
                case "onlineSet": {
                    /*
                     * {"cmd":"onlineSet", "nicks":["test","JavaBot"], "users": [
                     * {"channel":"forge", "isme":false, "nick":"test",
                     * "trip":"8Wotmg","uType":"user","hash":"Nn2jIz8w2Wk9qbo","level":100,"userid":
                     * 3707326840729,"isBot":false,"color":false}, {"channel":"forge", "isme":true,
                     * "nick":"JavaBot","trip":"XBotUU","uType":"user","hash":"Nn2jIz8w2Wk9qbo",
                     * "level":100,"userid":6883928675253,"isBot":false,"color":false} ]
                     * ,"channel":"forge","time":1624984540229}
                     *
                     *
                     * channel,isme bool, nick, trip, uType, hash, level int , userId long, isBot
                     * bool, color bool
                     */
                    if (this.isMainThread) {
                        setupActiveUsers(jsonText);
                        logService.logEvent("joined channel", "successfully", getTimestampNow());
                        break;
                    } else {
                        // 'list cmd users setter
                        incomingSetOnlineMessageQueue.add(jsonText);
                        break;
                    }
                }
                case "onlineAdd": {
                    //{"cmd":"onlineAdd","nick":"test","trip":"UqqSDd","uType":"user","hash":"Nn2jIz8w2Wk9qbo",
                    // "level":100,"userid":3734549386118,"isBot":false,"color":false,"channel":"programming",
                    // "time":1629034304212}
                    
                    JsonElement element = new JsonParser().parse(jsonText);
                    JsonObject object = element.getAsJsonObject();
                    User user = gson.fromJson(object, User.class);
                    
                    proceedBanned(user);
                    addActiveUser(user);
                    break;
                }
                case "onlineRemove": {
                    //{"cmd":"onlineRemove","userid":3910366301486,"nick":"newuser2","channel":"programming",
                    // "time":1629034916215}
                    JsonElement element = new JsonParser().parse(jsonText);
                    JsonObject object = element.getAsJsonObject();
                    User leftUser = gson.fromJson(object, User.class);
                    
                    removeActiveUser(leftUser.getNick());
                    break;
                }
                
                case "chat": {
                    ChatMessage message = gson.fromJson(jsonText, ChatMessage.class);
                    
                    logService.logMessage(message.getTrip(), message.getNick(), message.getHash(), message.getText(),
                            getTimestampNow());
                    
                    boolean isBotMessage = message.getNick().equals(this.nick);
                    if (isBotMessage) {
                        break;
                    }
                    
                    incomingChatMessageQueue.add(message);
                    
                    break;
                    
                }
                default:
                    System.out.printf("Text payload: %s \n", jsonText);
                    break;
            }
        }
    }
    
    private void proceedBanned(User user) {
        logService.logMessage(user.getTrip(), user.getNick(), user.getHash(), "JOINED", getTimestampNow());
        
        boolean isBanned = modService.isBanned(user);
        
        System.out.println("isBanned: " + isBanned);
        if (isBanned) {
            logService.logMessage(user.getTrip(), user.getNick(), user.getHash(), "is banned", getTimestampNow());
            modService.kick(user.getNick());
            this.removeActiveUser(user.getNick());
        }
    }
    
    private synchronized void removeActiveUser(String leftUser) {
        for (User user : currentChannelUsers) {
            if (user.getNick().equals(leftUser)) {
                currentChannelUsers.remove(user);
                logService.logMessage(user.getTrip(), user.getNick(), user.getHash(), "LEFT", getTimestampNow());
                logService.logEvent("user " + leftUser + " left channel", "", getTimestampNow());
            }
        }
    }
    
    private void addActiveUser(User newUser) {
        logService.logMessage(newUser.getTrip(), newUser.getNick(), newUser.getHash(), "JOINED", getTimestampNow());
        logService.logEvent("user " + newUser.getNick() + " joined channel", "successfully", getTimestampNow());
        currentChannelUsers.add(newUser);
    }
    
    private void setupActiveUsers(String jsonText) {
        JsonElement element = new JsonParser().parse(jsonText);
        JsonElement listingElement = element.getAsJsonObject().get("users");
        User[] users = gson.fromJson(listingElement, User[].class);
        
        currentChannelUsers.addAll(Arrays.asList(users));
    }
    
    public void messageProcessor() {
        if (!incomingChatMessageQueue.isEmpty()) {
            ChatMessage message = incomingChatMessageQueue.poll();
            
            String author = message.getNick();
            String trip = message.getTrip();
            
            String cmd = message.getText().trim();
            
            /* Mail service check */
            deliverMailIfPresent(author);
            
            if (!cmd.startsWith(prefix)) {
                return;
            }
            
            cmd = cmd.substring(1);
            if (is(cmd, BAN) && "8Wotmg".equals(trip)) {
                modService.ban(cmd.substring(4));
                System.out.println("Banned " + cmd.substring(4));
            }
            if (is(cmd, SQL) && "8Wotmg".equals(trip)) {
                sqlService.executeSQLCmd(cmd);
            }
            if (is(cmd, HELP)) {
                outService.enqueueMessageForSending(HELP_RESPONSE);
            } else if (is(cmd, SENTRY)) {
                outService.enqueueMessageForSending("@" + author + " Sentry on!");
            } else if (is(cmd, FISH)) {
                outService.enqueueMessageForSending("@" + author + " Bloop bloop!");
            } else if (is(cmd, LIST)) {
                executeListCommand(cmd, author);
            } else if (is(cmd, BABAKIUERIA)) {
                outService.enqueueMessageForSending("@" + author + " https://www.youtube.com/watch?v=NqcFg4z6EYY");
            } else if (is(cmd, DRRUDI)) {
                outService.enqueueMessageForSending("@" + author + " https://www.youtube.com/watch?v=uPaZWM4bxrM");
            } else if (is(cmd, RUST)) {
                outService.enqueueMessageForSending("@" + author + " https://doc.rust-lang.org/book/title-page.html");
            } else if (is(cmd, PING)) {
                pingService.executePing(author);
            } else if (is(cmd, SOLID)) {
                outService.enqueueMessageForSending(Constants.SOLID + " @" + author);
            } else if (is(cmd, SCP)) {
                scpService.executeRandomSCP(author);
            } else if (is(cmd, NOTES_PURGE)) {
                noteService.executeNotesPurge(author, trip);
            } else if (is(cmd, NOTE)) {
                noteService.executeAddNote(trip, cmd);
            } else if (is(cmd, NOTES)) {
                noteService.executeListNotes(author, trip);
            } else if (is(cmd, SEARCH)) {
                // executeSearch(author, cmd);
            } else if (is(cmd, MAIL)) {
                mailService.executeMail(author, cmd);
            } else if (is(cmd, MSG_CHANNEL)) {
                executeMsgChannelCmd(author, cmd);
            }
        }
    }
    
    private void deliverMailIfPresent(String author) {
        List<Mail> messages = mailService.getMailByNick(author.replace("@", ""));
        if (!messages.isEmpty()) {
            StringBuilder mailMessage = new StringBuilder();
            
            messages.forEach(mail -> {
                String row =
                        "date: [" + mail.createdDate + "] from: [" + mail.owner + "] message: [" + mail.message + "] " +
                                "\\n";
                mailMessage.append(row);
            });
            
            outService.enqueueMessageForSending("@" + author + " " + " incoming mail: \\n " + mailMessage);
            mailService.updateMailStatus(author);
        }
    }
    
    //
    private void executeMsgChannelCmd(String author, String cmd) {
        String[] args = cmd.split(" ");
        String list = args[0];
        String channel = null;
        StringBuilder message = new StringBuilder();
        if (args.length > 1) {
            if (args[1].charAt(0) == '?') {
                channel = args[1].substring(1);
            } else {
                channel = args[1];
            }
            
            for (int i = 2; i < args.length; i++) {
                message.append(' ').append(args[i]);
            }
        }
        
        //:msgchannel ?your-channel hello faggots
        if (list.equals(Cmd.MSG_CHANNEL.getCmdCode())) {
            if (channel != null && !channel.equals(this.channel)) {
                
                Facade listBot = new Facade(null, null); // no db connection, nor config for this one is needed
                listBot.isMainThread = false;
                listBot.setChannel(channel);
                listBot.joinDelay = 1000;
                
                int length = 8;
                boolean useLetters = true;
                boolean useNumbers = true;
                String generatedNick = RandomStringUtils.random(length, useLetters, useNumbers);
                listBot.setNick(generatedNick);
                listBot.setTrip(trip);
                
                listBot.start();
                listBot.sendChatMessage("Message from room: " + this.channel + ", author:" + author + ", for " + channel + ", message: " + message);
                listBot.stop();
                
                outService.enqueueMessageForSending("@" + author + ", message for " + channel + " has been delivered!");
            }
        }
    }
    
    private void executeListCommand(String cmd, String author) {
        String[] args = cmd.split(" ");
        String list = args[0];
        String channel = null;
        if (args.length > 1) {
            channel = args[1];
        }
        
        if (list.equals(Cmd.LIST.getCmdCode())) {
            if (channel != null && !channel.equals(this.channel)) {
                List<String> nickList = getNicksFromChannel(channel);
                
                if (nickList.isEmpty()) {
                    outService.enqueueMessageForSending("@" + author + ", channel - " + channel + " is empty");
                } else {
                    outService.enqueueMessageForSending("@" + author + ", users in '" + channel + "' channel: " + nickList);
                }
            } else {
                // parse nicks from current channel
                String userNames = this.currentChannelUsers.toString();
                outService.enqueueMessageForSending("@" + author + "\\n```Text \\n Users online: " + userNames + "\\n" +
                        " ```");
            }
        }
    }
    
    private List<String> getNicksFromChannel(String channel) {
        Facade listBot = new Facade(null, null); // no db connection, nor config for this one is needed
        listBot.isMainThread = false;
        listBot.setChannel(channel);
        listBot.joinDelay = 1000;
        
        int length = 8;
        boolean useLetters = true;
        boolean useNumbers = true;
        String generatedNick = RandomStringUtils.random(length, useLetters, useNumbers);
        listBot.setNick(generatedNick);
        listBot.setTrip(trip);
        
        listBot.start();
        
        List<String> incomingSetOnlineMessageQueue = listBot.getIncomingSetOnlineMessageQueue();
        while (incomingSetOnlineMessageQueue.isEmpty()) {
            incomingSetOnlineMessageQueue = listBot.getIncomingSetOnlineMessageQueue();
        }
        
        listBot.stop();
        String jsonString = incomingSetOnlineMessageQueue.get(0);
        
        Gson gson = listBot.gson;
        JsonElement element = new JsonParser().parse(jsonString); //parse to json tree
        JsonElement listingElement = element.getAsJsonObject().get("nicks"); // extract key
        String[] nicksArray = gson.fromJson(listingElement, String[].class);
        List<String> nickList = new ArrayList<>(List.of(nicksArray));
        nickList.remove(generatedNick);
        return nickList;
    }
    
    
    //    private void executeSearch(String author, String cmd) {
    //        String[] args = cmd.split(" ");
    //        StringBuilder searchString = new StringBuilder();
    //        for (int i = 1; i < args.length; i++) {
    //            searchString.append(" ").append(args[i]);
    //        }
    //        String resultJson = searchService.search(searchString.toString());
    //        enqueueMessageForSending("@" + author + " \\n" + resultJson);
    //    }
    
    
}
