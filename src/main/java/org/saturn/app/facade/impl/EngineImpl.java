package org.saturn.app.facade.impl;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.saturn.app.facade.Base;
import org.saturn.app.facade.Engine;
import org.saturn.app.model.command.UserCommand;
import org.saturn.app.model.command.impl.HelpUserCommandImpl;
import org.saturn.app.model.command.impl.ListUserCommandImpl;
import org.saturn.app.model.command.impl.SayUserCommandImpl;
import org.saturn.app.model.dto.*;
import org.saturn.app.service.ListCommandListener;
import org.saturn.app.service.Listener;
import org.saturn.app.service.listener.*;
import org.saturn.app.util.Cmd;

import java.net.URISyntaxException;
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

import static org.saturn.app.util.Constants.CHAT_JSON;
import static org.saturn.app.util.Constants.JOIN_JSON;
import static org.saturn.app.util.Util.getAuthor;
import static org.saturn.app.util.Util.getCmdFromJson;
import static org.saturn.app.util.Util.getTimestampNow;

public class EngineImpl extends Base implements Engine {
    protected org.saturn.app.model.dto.Connection hcConnection;
    List<String> admins;
    List<String> trustedUsers;
    public List<String> tripsWhiteList = new ArrayList<>();

    Listener onlineSetListener = new OnlineSetListenerImpl(this);
    Listener userJoinedListener = new UserJoinedListenerImpl(this);
    Listener userLeftListener = new UserLeftListenerImpl(this);
    Listener userMessageListener = new UserMessageListenerImpl(this);
    Listener connectionListener = new ConnectionListenerImpl(this);
    Listener incomingMessageListener = new IncomingMessageListenerImpl(this);

    public ListCommandListener listCommandListener;
    boolean isJoined;

    public void setListCommandListener(ListCommandListener listCommandListener) {
        this.listCommandListener = listCommandListener;
    }

    public EngineImpl(Connection dbConnection, Configuration config, Boolean isMain) {
        super(dbConnection, config, isMain);

        if (adminTrips != null && userTrips != null) {
            admins = Arrays.asList(adminTrips.split(","));
            trustedUsers = Arrays.asList(userTrips.split(","));

            tripsWhiteList.addAll(admins);
            tripsWhiteList.addAll(trustedUsers);
        }

        UserCommand sayUserCommand = new SayUserCommandImpl(this, tripsWhiteList);
        UserCommand helpUserCommand = new HelpUserCommandImpl(this, tripsWhiteList);
        UserCommand listUserCommand = new ListUserCommandImpl(this, tripsWhiteList);
        super.enabledUserCommands.add(sayUserCommand);
        super.enabledUserCommands.add(helpUserCommand);
        super.enabledUserCommands.add(listUserCommand);
    }

    @Override
    public void setBaseWsUrl(String address){
        this.baseWsURL = address;
    }
    @Override
    public void setChannel(String channel) {
        super.setChannel(channel);
    }
    @Override
    public void setPassword(String password) {
        super.setTrip(password);
    }

    @Override
    public void say(String message) {
        outService.enqueueMessageForSending(message);
    }

    @Override
    public List<User> getActiveUsers() {
        return new ArrayList<>(currentChannelUsers);
    }

    @Override
    public void setActiveUsers(List<User> users) {
        this.currentChannelUsers.addAll(users);
    }

    @Override
    public void setNick(String nick) {
        super.setNick(nick);
    }

    @Override
    public void start() {
        try {
            hcConnection = new org.saturn.app.model.dto.Connection(baseWsURL, List.of(connectionListener, incomingMessageListener));
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }
    
    public void sendJoinMessage() {
        String joinPayload = String.format(JOIN_JSON, channel, nick, trip);
        hcConnection.write(joinPayload);
    }
    
    public void shareMessages() {
        if (!outgoingMessageQueue.isEmpty()) {
            flushMessage(outgoingMessageQueue.poll());
        }
    }
    public void flushMessage(String message) {
        String chatPayload = String.format(CHAT_JSON, message);
        if (hcConnection == null && message != null) {
            System.out.println("Connection has been closed, couldn't deliver: " + chatPayload);
            return;
        }
        hcConnection.write(chatPayload);
    }
    @Override
    public void stop() {
        try {
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
                
                flushMessage(StringEscapeUtils.escapeJson(message));
            }
        }
    }
    
//    private void setupWorkers() {
//        executorScheduler.scheduleWithFixedDelay(() -> {
//            try {
//                dispatchMessage();
//                processMessage();
//                shareMessages();
//                //                shareDBmessages(); /* TODO: fix/remove */
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }, 0, 50, TimeUnit.MILLISECONDS);
//    }
    
    public void dispatchMessage(String jsonText) {
        String cmd = getCmdFromJson(jsonText);
        switch (cmd) {
            case "join": {
                break;
            }
            case "onlineSet": {
                onlineSetListener.notify(jsonText);
                break;
            }
            case "onlineAdd": {
                userJoinedListener.notify(jsonText);
                break;
            }
            case "onlineRemove": {
                userLeftListener.notify(jsonText);
                break;
            }
            
            case "chat": {
                userMessageListener.notify(jsonText);
                break;
            }
            default:
                System.out.printf("Text payload: %s \n", jsonText);
                break;
        }
    }

    private final Set<String> subscribers = new HashSet<>();

    public void shareUserInfo(User user) {
        String joinedUserData = sqlService.getBasicUserData(user.getHash(), user.getTrip());
        subscribers.forEach(mod -> outService.enqueueMessageForSending("/whisper " + mod + " -\\n\\n" + joinedUserData));
    }

    public void proceedBanned(User user) {
        logService.logMessage(user.getTrip(), user.getNick(), user.getHash(), "JOINED", getTimestampNow());
        
        boolean isBanned = modService.isBanned(user);
        
        System.out.println("isBanned: " + isBanned);
        if (isBanned) {
            logService.logMessage(user.getTrip(), user.getNick(), user.getHash(), "is banned", getTimestampNow());
            modService.kick(user.getNick());
            this.removeActiveUser(user.getNick());
        }
    }
    
    public void removeActiveUser(String leftUser) {
        for (User user : currentChannelUsers) {
            if (user.getNick().equals(leftUser)) {
                currentChannelUsers.remove(user);
                logService.logMessage(user.getTrip(), user.getNick(), user.getHash(), "LEFT", getTimestampNow());
                logService.logEvent("user " + leftUser + " left channel", "", getTimestampNow());
            }
        }
    }
    
    public void addActiveUser(User newUser) {
        logService.logMessage(newUser.getTrip(), newUser.getNick(), newUser.getHash(), "JOINED", getTimestampNow());
        logService.logEvent("user " + newUser.getNick() + " joined channel", "successfully", getTimestampNow());
        currentChannelUsers.add(newUser);
    }



//        if (command.is(ECHO)) {
//            StringBuilder arguments = new StringBuilder();
//            command.getArguments().forEach(arg -> arguments.append(arg).append(" "));
//            outService.enqueueMessageForSending(command.getCommandName() + " " + arguments);
//        }
//
//        if (command.is(BAN) && admins.contains(trip)) {
//            command.getArguments()
//                    .stream()
//                    .map(nick -> nick.replace("@", ""))
//                    .map(nick -> currentChannelUsers.stream()
//                            .filter(user -> nick.equals(user.getNick()))
//                            .map(User::getHash)
//                            .findFirst()
//                            .orElse(null))
//                    .findFirst()
//                    .ifPresentOrElse(hash -> {
//                        modService.ban(nick, hash);
//                        outService.enqueueMessageForSending("/whisper @" + author + " banned: " + nick + " hash: " + hash);
//                        modService.kick(nick);
//                    }, () -> {
//                        /* target isn't in the room */
//                        modService.ban(nick);
//                        outService.enqueueMessageForSending("/whisper @" + author + " banned: " + nick);
//                    });
//        }
//
//        if (command.is(UNBAN) && admins.contains(trip)) {
//            command.getArguments().stream()
//                    .findFirst()
//                    .ifPresent(target -> {
//                        modService.unban(target);
//                        outService.enqueueMessageForSending("/whisper @" + author + " unbanned " + target);
//                    });
//        }
//        if (command.is(BANLIST) && admins.contains(trip)) {
//            modService.listBanned();
//        }
//        if (command.is(INFO) && (trustedUsers.contains(trip) || admins.contains(trip))) {
//            command.getArguments().stream()
//                    .findFirst()
//                    .ifPresent(nick -> currentChannelUsers.stream()
//                            .filter(user -> nick.equals(user.getNick()))
//                            .findFirst()
//                            .ifPresentOrElse(user -> outService.enqueueMessageForSending("/whisper @" + author + " " +
//                                            "\\n User trip: " + user.getTrip() +
//                                            "\\n User hash: " + user.getHash()),
//                                    () -> outService.enqueueMessageForSending("/whisper @" + author + " " +
//                                            "\\n User: " + nick + " not found!")));
//        }
//        if (command.is(SUB) && (trustedUsers.contains(trip) || admins.contains(trip))) {
//            subscribers.add(author);
//            outService.enqueueMessageForSending("/whisper @" + author + " You will get related hashes, trips and " +
//                    "nicks whispered for each joining user. You can use :votekick to kick.");
//        }
//
//        if (command.is(VOTEKICK) && (trustedUsers.contains(trip) || admins.contains(trip))) {
//            String nick = cmd.substring(9);
//            modService.votekick(nick);
//            outService.enqueueMessageForSending(" Vote kick started, please type    :vote reason_here    to vote " +
//                    "yes. Execution will proceed as 3 votes are reached.");
//        }
//        if (command.is(VOTE)) {
//            modService.vote(author);
//        }
//        if (command.is(SQL) && admins.contains(trip)) {
//            String result = sqlService.executeSql(cmd, true);
//            outService.enqueueMessageForSending(result);
//        }
//        if (command.is(HELP)) {
//            outService.enqueueMessageForSending(HELP_RESPONSE);
//        }
//        if (command.is(SENTRY)) {
//            outService.enqueueMessageForSending("@" + author + " Sentry on!");
//        }
//        if (command.is(FISH)) {
//            outService.enqueueMessageForSending("@" + author + " Bloop bloop!");
//        }
//        if (command.is(LIST)) {
//            executeListCommand(command, author);
//        }
//        if (command.is(BABAKIUERIA)) {
//            outService.enqueueMessageForSending("@" + author + " https://www.youtube.com/watch?v=NqcFg4z6EYY");
//        }
//        if (command.is(DRRUDI)) {
//            outService.enqueueMessageForSending("@" + author + " https://www.youtube.com/watch?v=uPaZWM4bxrM");
//        }
//        if (command.is(RUST)) {
//            outService.enqueueMessageForSending("@" + author + " https://doc.rust-lang.org/book/title-page.html");
//        }
//        if (command.is(PING)) {
//            pingService.executePing(author);
//        }
//        if (command.is(SOLID)) {
//            outService.enqueueMessageForSending(Constants.SOLID + " @" + author);
//        }
//        if (command.is(SCP)) {
//            scpService.executeRandomSCP(author);
//        }
//        if (command.is(NOTESPURGE)) {
//            noteService.executeNotesPurge(author, trip);
//        }
//        if (command.is(NOTE)) {
//            noteService.executeAddNote(trip, command.getCommandName());
//        }
//        if (command.is(NOTES)) {
//            noteService.executeListNotes(author, trip);
//        }
//        if (command.is(SEARCH)) {
//            // executeSearch(author, cmd);
//        }
//        if (command.is(MAIL)) {
//            mailService.executeMail(author, command);
//        }
//        if (command.is(WEATHER) || trustedUsers.contains(trip) ) {
//            weatherService.executeWeather(author, command);
//        }
//        if (command.is(MSGCHANNEL)) {
//            executeMsgChannelCmd(trip, command);
//        }

    public void deliverMailIfPresent(String author) {
        List<Mail> messages = mailService.getMailByNick(getAuthor(author));
        if (messages.isEmpty()) {
            return;
        }
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
    
    private void executeMsgChannelCmd(String author, UserCommand cmd) {
        String[] args = cmd.getArguments().toArray(new String[0]);
        String list = cmd.getCommandName();
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
            
            Engine listBot = new EngineImpl(null, null, false); // no db connection, nor config for this one is needed

            int length = 8;
            boolean useLetters = true;
            boolean useNumbers = true;
            String generatedNick = RandomStringUtils.random(length, useLetters, useNumbers);
            listBot.setNick(generatedNick);
            listBot.setPassword(trip);
            
            listBot.start();
            listBot.say("Message from room: " + this.channel + ", trip:" + author + ", for " + channel +
                    ", message: " + message);
            listBot.stop();
            
            outService.enqueueMessageForSending("@" + author + ", message for " + channel + " has been delivered!");
        }
    }
}
