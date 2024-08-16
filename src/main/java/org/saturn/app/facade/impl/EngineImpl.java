package org.saturn.app.facade.impl;

import org.apache.commons.configuration2.Configuration;
import org.saturn.app.facade.Base;
import org.saturn.app.facade.Engine;
import org.saturn.app.listener.Listener;
import org.saturn.app.listener.impl.*;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.factory.CommandFactory;
import org.saturn.app.model.dto.Mail;
import org.saturn.app.model.dto.Proxy;
import org.saturn.app.model.dto.User;
import org.saturn.app.util.DateUtil;

import java.net.URISyntaxException;
import java.sql.Connection;
import java.util.*;
import java.util.stream.Collectors;

import static org.saturn.app.util.Constants.CHAT_JSON;
import static org.saturn.app.util.Constants.JOIN_JSON;
import static org.saturn.app.util.Util.*;

public class EngineImpl extends Base implements Engine {
    public List<String> proxies;
    public final CommandFactory commandFactory;
    protected org.saturn.app.facade.impl.Connection hcConnection;
    public final Set<String> subscribers = new HashSet<>();
    private Listener onlineSetListener = new OnlineSetListenerImpl(this);
    private final Listener userJoinedListener = new UserJoinedListenerImpl(this);
    private final Listener userLeftListener = new UserLeftListenerImpl(this);
    private final Listener chatMessageListener = new UserMessageListenerImpl(this);
    private final Listener infoMessageListener = new InfoMessageListenerImpl(this);
    private final Listener connectionListener = new ConnectionListenerImpl(this);
    private final Listener incomingMessageListener = new IncomingMessageListenerImpl(this);

    public final Set<String> afkUsers = new HashSet<>();

    public void setOnlineSetListener(Listener listener) {
        this.onlineSetListener = listener;
    }

    public EngineImpl(Connection dbConnection, Configuration config, Boolean isMain) {
        super(dbConnection, config, isMain);

        if (super.proxies != null) {
            if (!super.proxies.isEmpty() || !super.proxies.isBlank()) {
                this.proxies = Arrays.asList(super.proxies.split(","));
            }
        }

        this.commandFactory = new CommandFactory(this, "org.saturn.app.model.command.impl", CommandAliases.class);
    }

    @Override
    public void setBaseWsUrl(String address) {
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
            hcConnection = new org.saturn.app.facade.impl.Connection(baseWsURL, List.of(connectionListener, incomingMessageListener), null, this);
            hcConnection.start();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void start(Proxy proxy) {
        try {
            hcConnection = new org.saturn.app.facade.impl.Connection(baseWsURL, List.of(connectionListener, incomingMessageListener), proxy, this);
            hcConnection.startNonBlocking();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendJoinMessage() {
        String joinPayload = String.format(JOIN_JSON, channel, nick, password);
        hcConnection.write(joinPayload);
    }

    public void shareMessages() {
        if (!outgoingMessageQueue.isEmpty()) {
            String chatPayload = String.format(CHAT_JSON, outgoingMessageQueue.poll());
            flushMessage(chatPayload);
        }
        if (!outgoingRawMessageQueue.isEmpty()) {
            flushMessage(outgoingRawMessageQueue.poll());
        }
    }

    public void flushMessage(String message) {
        if (hcConnection == null && message != null) {
            System.out.println("Connection has been closed, couldn't deliver: " + message);
            return;
        }
        hcConnection.write(message);
    }

    @Override
    public void stop() {
        try {
            if (hcConnection != null) {
                this.hcConnection.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void dispatchMessage(String jsonText) {
        try {
            String cmd = extractCmdFromJson(jsonText);
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
                    chatMessageListener.notify(jsonText);
                    break;
                }
                case "info": {
                    infoMessageListener.notify(jsonText);
                    break;
                }
                default:
                    System.out.printf("Incoming payload: %s \n", jsonText);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Something went wrong: " + e);
        }
    }

    public void shareUserInfo(User user) {
        String joinedUserData = sqlService.getBasicUserData(user.getHash(), user.getTrip());
        /* possible bug if called through whisper */
        subscribers.forEach(mod -> outService.enqueueMessageForSending("", "/whisper " + mod + " -\\n\\n" + joinedUserData, false));
    }

    public void proceedBanned(User user) {
        logService.logMessage(user.getTrip(), user.getNick(), user.getHash(), "JOINED", DateUtil.getTimestampNow());

        boolean isBanned = modService.isBanned(user);

        System.out.println("isBanned: " + isBanned);
        if (isBanned) {
            logService.logMessage(user.getTrip(), user.getNick(), user.getHash(), "is banned", DateUtil.getTimestampNow());
            modService.kick(user.getNick());
            this.removeActiveUser(user.getNick());
        }
    }

    public void removeActiveUser(String leftUser) {
        for (User user : currentChannelUsers) {
            if (leftUser.equals(user.getNick())) {
                currentChannelUsers.remove(user);
                logService.logMessage(user.getTrip(), user.getNick(), user.getHash(), "LEFT", DateUtil.getTimestampNow());
                logService.logEvent("user " + leftUser + " left channel", "", DateUtil.getTimestampNow());
            }
        }
    }

    public void addActiveUser(User newUser) {
        logService.logMessage(newUser.getTrip(), newUser.getNick(), newUser.getHash(), "JOINED", DateUtil.getTimestampNow());
        logService.logEvent("user " + newUser.getNick() + " joined channel", "successfully", DateUtil.getTimestampNow());
        currentChannelUsers.add(newUser);
    }

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

    public void notifyUserNotAfkAnymore(String author) {
        if (this.afkUsers.contains(author)) {
            afkUsers.remove(author);
            outService.enqueueMessageForSending(author," isn't afk anymore ", false);
        }
    }

    public void notifyIsAfkIfUserIsMentioned(String author, String messageText) {
        afkUsers.forEach(user -> {
            if (messageText.contains(user)) {
                outService.enqueueMessageForSending(author,", user: " + user + " is currently away from keyboard!", false);
            }
        });
    }

    public void deliverMailIfPresent(String author, String trip) {
        List<Mail> messages = mailService.getMailByNickOrTrip(getAuthor(author), getAuthor(trip));
        if (messages.isEmpty()) {
            return;
        }

        List<Mail> whisperMails = getMail(messages, true);
        if (!whisperMails.isEmpty()) {
            String whisperStrings = mailToStrings(whisperMails);
            outService.enqueueMessageForSending("","/whisper @" + author + " Incoming mail: \\n " + whisperStrings, false);
        }

        List<Mail> publicMessages = getMail(messages, false);
        if (!publicMessages.isEmpty()) {
            String publicStrings = mailToStrings(publicMessages);
            outService.enqueueMessageForSending(author,"@" + author + " Incoming mail: \\n " + publicStrings, false);
        }

        mailService.updateMailStatus(author);
        mailService.updateMailStatus(trip);
    }

    private static List<Mail> getMail(List<Mail> messages, boolean isWhisper) {
        return messages.stream().filter(m -> String.valueOf(isWhisper).equals(m.isWhisper)).collect(Collectors.toList());
    }

    private String mailToStrings(List<Mail> messages) {
        StringBuilder whisperStrings = new StringBuilder();
        messages.forEach(mail -> {
            String row = DateUtil.formatZone(mail.createdDate, "UTC") + " " + mail.owner + ": " + mail.message + "\\n";
            whisperStrings.append(row);
        });

        return whisperStrings.toString();
    }

    public boolean isConnected() {
       return this.hcConnection.isConnected();
    }
}
