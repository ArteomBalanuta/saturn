package org.saturn.app.facade;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.saturn.app.model.Command;
import org.saturn.app.model.WebSocketFrame;
import org.saturn.app.model.impl.ChatMessage;
import org.saturn.app.model.impl.CommandImpl;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.saturn.app.util.Cmd.BABAKIUERIA;
import static org.saturn.app.util.Cmd.BAN;
import static org.saturn.app.util.Cmd.BANLIST;
import static org.saturn.app.util.Cmd.DRRUDI;
import static org.saturn.app.util.Cmd.ECHO;
import static org.saturn.app.util.Cmd.FISH;
import static org.saturn.app.util.Cmd.HELP;
import static org.saturn.app.util.Cmd.INFO;
import static org.saturn.app.util.Cmd.LIST;
import static org.saturn.app.util.Cmd.MAIL;
import static org.saturn.app.util.Cmd.MSGCHANNEL;
import static org.saturn.app.util.Cmd.NOTE;
import static org.saturn.app.util.Cmd.NOTES;
import static org.saturn.app.util.Cmd.NOTESPURGE;
import static org.saturn.app.util.Cmd.PING;
import static org.saturn.app.util.Cmd.RUST;
import static org.saturn.app.util.Cmd.SCP;
import static org.saturn.app.util.Cmd.SEARCH;
import static org.saturn.app.util.Cmd.SENTRY;
import static org.saturn.app.util.Cmd.SOLID;
import static org.saturn.app.util.Cmd.SQL;
import static org.saturn.app.util.Cmd.SUB;
import static org.saturn.app.util.Cmd.UNBAN;
import static org.saturn.app.util.Cmd.VOTE;
import static org.saturn.app.util.Cmd.VOTEKICK;
import static org.saturn.app.util.Constants.HELP_RESPONSE;
import static org.saturn.app.util.Constants.JOIN_JSON;
import static org.saturn.app.util.Constants.UPGRADE_REQUEST;
import static org.saturn.app.util.Util.getAuthor;
import static org.saturn.app.util.Util.getCmdFromJson;
import static org.saturn.app.util.Util.getTimestampNow;

public class ServiceLayer extends Base {
    public static final int FIRST = 0;
    protected final OutService outService;
    
    protected final LogService logService;
    protected final SCPService scpService;
    protected final NoteService noteService;
    protected final SearchService searchService;
    protected final MailService mailService;
    protected final SQLService sqlService;
    protected final PingService pingService;
    protected final ModService modService;
    
    public ServiceLayer(Connection dbConnection, Configuration config) {
        super(dbConnection, config);
        this.outService = new OutService(outgoingMessageQueue);
        this.scpService = new SCPServiceImpl(outgoingMessageQueue);
        this.noteService = new NoteServiceImpl(dbConnection, outgoingMessageQueue);
        this.mailService = new MailServiceImpl(dbConnection, outgoingMessageQueue);
        this.sqlService = new SQLServiceImpl(dbConnection, outgoingMessageQueue);
        this.pingService = new PingServiceImpl(outgoingMessageQueue);
        this.logService = new LogServiceImpl(dbConnection);
        this.searchService = new SearchServiceImpl();                                       /* TODO:  add logging */
        this.modService = new ModServiceImpl(this.sqlService, outgoingMessageQueue);
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
        boolean isSSL = true;
        hcConnection = new org.saturn.app.model.impl.Connection(uri, port, this.isMainThread, isSSL);
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
    
    private void shareDBmessages() throws SQLException {
        Connection connection = this.sqlService.getConnection();
        Statement statement = connection.createStatement();
        
        String sql = "select distinct message from mail where status = 'BOT_SAY' limit 1;";
        statement.execute(sql);
        
        ResultSet resultSet = statement.getResultSet();
        while (resultSet.next()) {
            String message = resultSet.getString(1);
            if (message != null && !message.isEmpty() && !message.isBlank()) {
                
                Statement s = connection.createStatement();
                String update = "DELETE FROM MAIL where status = 'BOT_SAY';";
                s.execute(update);
                
                sendChatMessage(StringEscapeUtils.escapeJson(message));
            }
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
                shareDBmessages();
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
            JsonElement element = JsonParser.parseString(jsonText);
            JsonObject object = element.getAsJsonObject();
            switch (cmd) {
                case "join": {
                    break;
                }
                case "onlineSet": {
                    if (this.isMainThread) {
                        setupActiveUsers(jsonText);
                        outService.enqueueMessageForSending("/color #06C22E");
                        logService.logEvent("joined channel", "successfully", getTimestampNow());
                        break;
                    } else {
                        // 'list cmd users setter
                        incomingSetOnlineMessageQueue.add(jsonText);
                        break;
                    }
                }
                case "onlineAdd": {
                    User user = gson.fromJson(object, User.class);
                    System.out.println("Joined: " + user.toString());
                    
                    addActiveUser(user);
                    shareUserInfo(user);
                    proceedBanned(user);
                    break;
                }
                case "onlineRemove": {
                    //{"cmd":"onlineRemove","userid":3910366301486,"nick":"newuser2","channel":"programming",
                    // "time":1629034916215}
                    User user = gson.fromJson(object, User.class);
                    removeActiveUser(user.getNick());
                    break;
                }
                
                case "chat": {
                    ChatMessage message = gson.fromJson(jsonText, ChatMessage.class);
                    System.out.println(message.getNick() + ": " + message.getText());
                    
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
    
    Set<String> subscribers = new HashSet<>();
    
    private void shareUserInfo(User user) {
        String joinedUserData = sqlService.getBasicUserData(user.getHash(), user.getTrip());
        subscribers.forEach(mod -> outService.enqueueMessageForSending("/whisper " + mod + " -\\n\\n" + joinedUserData));
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
    
    private void removeActiveUser(String leftUser) {
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
        JsonElement element = JsonParser.parseString(jsonText);
        JsonElement listingElement = element.getAsJsonObject().get("users");
        User[] users = gson.fromJson(listingElement, User[].class);
        
        currentChannelUsers.addAll(Arrays.asList(users));
    }
    
    public void messageProcessor() {
        if (incomingChatMessageQueue.isEmpty()) {
            return;
        }
        
        ChatMessage message = incomingChatMessageQueue.poll();
        String author = message.getNick();
        String trip = message.getTrip();
        String cmd = message.getText().trim();
        
        /* Mail service check */
        deliverMailIfPresent(author);
        
        if (!cmd.startsWith(prefix)) {
            return;
        }
        
        List<String> admins = Arrays.asList(adminTrips.split(","));
        List<String> trustedUsers = Arrays.asList(userTrips.split(","));
        
        Command command = new CommandImpl(cmd.substring(1));
        if (command.is(ECHO)) {
            StringBuilder arguments = new StringBuilder();
            command.getArguments().forEach(arg -> arguments.append(arg).append(" "));
            outService.enqueueMessageForSending(command.getCommand() + " " + arguments);
        }
        
        if (command.is(BAN) && admins.contains(trip)) {
            command.getArguments()
                    .stream()
                    .map(nick -> nick.replace("@", ""))
                    .map(nick -> currentChannelUsers.stream()
                            .filter(user -> nick.equals(user.getNick()))
                            .map(User::getHash)
                            .findFirst()
                            .orElse(null))
                    .findFirst()
                    .ifPresentOrElse(hash -> {
                        modService.ban(nick, hash);
                        outService.enqueueMessageForSending("/whisper @" + author + " banned: " + nick + " hash: " + hash);
                        modService.kick(nick);
                    }, () -> {
                        /* target isn't in the room */
                        modService.ban(nick);
                        outService.enqueueMessageForSending("/whisper @" + author + " banned: " + nick);
                    });
        }
        
        if (command.is(UNBAN) && admins.contains(trip)) {
            command.getArguments().stream()
                    .findFirst()
                    .ifPresent(target -> {
                        modService.unban(target);
                        outService.enqueueMessageForSending("/whisper @" + author + " unbanned " + target);
                    });
        }
        if (command.is(BANLIST) && admins.contains(trip)) {
            modService.listBanned();
        }
        if (command.is(INFO) && (trustedUsers.contains(trip) || admins.contains(trip))) {
            command.getArguments().stream()
                    .findFirst()
                    .ifPresent(nick -> currentChannelUsers.stream()
                            .filter(user -> nick.equals(user.getNick()))
                            .findFirst()
                            .ifPresentOrElse(user -> outService.enqueueMessageForSending("/whisper @" + author + " " +
                                            "\\n User trip: " + user.getTrip() +
                                            "\\n User hash: " + user.getHash()),
                                    () -> outService.enqueueMessageForSending("/whisper @" + author + " " +
                                            "\\n User: " + nick + " not found!")));
        }
        if (command.is(SUB) && (trustedUsers.contains(trip) || admins.contains(trip))) {
            subscribers.add(author);
            outService.enqueueMessageForSending("/whisper @" + author + " You will get related hashes, trips and " +
                    "nicks whispered for each joining user. You can use :votekick to kick.");
        }
        
        if (command.is(VOTEKICK) && (trustedUsers.contains(trip) || admins.contains(trip))) {
            String nick = cmd.substring(9);
            modService.votekick(nick);
            outService.enqueueMessageForSending(" Vote kick started, please type    :vote reason_here    to vote " +
                    "yes. Execution will proceed as 3 votes are reached.");
        }
        if (command.is(VOTE)) {
            modService.vote(author);
        }
        if (command.is(SQL) && admins.contains(trip)) {
            String result = sqlService.executeSql(cmd, true);
            outService.enqueueMessageForSending(result);
        }
        if (command.is(HELP)) {
            outService.enqueueMessageForSending(HELP_RESPONSE);
        }
        if (command.is(SENTRY)) {
            outService.enqueueMessageForSending("@" + author + " Sentry on!");
        }
        if (command.is(FISH)) {
            outService.enqueueMessageForSending("@" + author + " Bloop bloop!");
        }
        if (command.is(LIST)) {
            executeListCommand(command, author);
        }
        if (command.is(BABAKIUERIA)) {
            outService.enqueueMessageForSending("@" + author + " https://www.youtube.com/watch?v=NqcFg4z6EYY");
        }
        if (command.is(DRRUDI)) {
            outService.enqueueMessageForSending("@" + author + " https://www.youtube.com/watch?v=uPaZWM4bxrM");
        }
        if (command.is(RUST)) {
            outService.enqueueMessageForSending("@" + author + " https://doc.rust-lang.org/book/title-page.html");
        }
        if (command.is(PING)) {
            pingService.executePing(author);
        }
        if (command.is(SOLID)) {
            outService.enqueueMessageForSending(Constants.SOLID + " @" + author);
        }
        if (command.is(SCP)) {
            scpService.executeRandomSCP(author);
        }
        if (command.is(NOTESPURGE)) {
            noteService.executeNotesPurge(author, trip);
        }
        if (command.is(NOTE)) {
            noteService.executeAddNote(trip, command.getCommand());
        }
        if (command.is(NOTES)) {
            noteService.executeListNotes(author, trip);
        }
        if (command.is(SEARCH)) {
            // executeSearch(author, cmd);
        }
        if (command.is(MAIL)) {
            mailService.executeMail(author, command);
        }
        if (command.is(MSGCHANNEL)) {
            executeMsgChannelCmd(trip, command);
        }
    }
    
    private void deliverMailIfPresent(String author) {
        List<Mail> messages = mailService.getMailByNick(getAuthor(author));
        if (!messages.isEmpty()) {
            StringBuilder mailMessage = new StringBuilder();
            
            messages.forEach(mail -> {
                String row =
                        "date: [" + Instant.ofEpochMilli(mail.createdDate).atZone(ZoneOffset.UTC) + "] from: [" + mail.owner + "] message: [" + mail.message + "] " +
                                "\\n";
                mailMessage.append(row);
            });
            
            outService.enqueueMessageForSending("@" + author + " " + " incoming mail: \\n " + mailMessage);
            mailService.updateMailStatus(author);
        }
    }
    
    
    private void executeMsgChannelCmd(String author, Command cmd) {
        String[] args = cmd.getArguments().toArray(new String[0]);
        String list = cmd.getCommand();
        String channel = null;
        StringBuilder message = new StringBuilder();
        if (args.length > 0) {
            if (args[0].charAt(0) == '?') {
                channel = args[0].substring(1);
            } else {
                channel = args[0];
            }
            
            for (int i = 1; i < args.length; i++) {
                message.append(' ').append(args[i]);
            }
        }
        
        //:msgchannel ?your-channel hello faggots
        if (list.equals(Cmd.MSGCHANNEL.getCmdCode())) {
            //&& !channel.equals(this.channel)
            if (channel == null) {
                return;
            }
            
            Facade listBot = new Facade(null, null); // no db connection, nor config for this one is needed
            listBot.isMainThread = false;
            listBot.setChannel(channel);
            listBot.joinDelay = 500;
            
            int length = 8;
            boolean useLetters = true;
            boolean useNumbers = true;
            String generatedNick = RandomStringUtils.random(length, useLetters, useNumbers);
            listBot.setNick(generatedNick);
            listBot.setTrip(trip);
            
            listBot.start();
            listBot.sendChatMessage("Message from room: " + this.channel + ", trip:" + author + ", for " + channel +
                    ", message: " + message);
            listBot.stop();
            
            outService.enqueueMessageForSending("@" + author + ", message for " + channel + " has been delivered!");
        }
    }
    
    private void executeListCommand(Command cmd, String author) {
        String[] args = cmd.getArguments().toArray(new String[0]);
        String list = cmd.getCommand();
        String channel = null;
        if (args.length > 0) {
            channel = args[0];
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
        JsonElement element = JsonParser.parseString(jsonString); //parse to json tree
        JsonElement listingElement = element.getAsJsonObject().get("nicks"); // extract key
        String[] nicksArray = gson.fromJson(listingElement, String[].class);
        List<String> nickList = new ArrayList<>(List.of(nicksArray));
        nickList.remove(generatedNick);
        return nickList;
    }
}
