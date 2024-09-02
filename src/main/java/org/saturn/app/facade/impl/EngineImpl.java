package org.saturn.app.facade.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration2.Configuration;
import org.saturn.app.facade.Base;
import org.saturn.app.facade.Engine;
import org.saturn.app.listener.Listener;
import org.saturn.app.listener.impl.*;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.factory.CommandFactory;
import org.saturn.app.model.dto.Afk;
import org.saturn.app.model.dto.Mail;
import org.saturn.app.model.dto.Proxy;
import org.saturn.app.model.dto.User;
import org.saturn.app.util.DateUtil;

import java.net.URISyntaxException;
import java.sql.Connection;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.saturn.app.util.Constants.CHAT_JSON;
import static org.saturn.app.util.Constants.JOIN_JSON;
import static org.saturn.app.util.DateUtil.getDifference;
import static org.saturn.app.util.DateUtil.toZoneDateTimeUTC;
import static org.saturn.app.util.Util.*;

@Slf4j
public class EngineImpl extends Base implements Engine {
    public List<String> proxies;
    public final CommandFactory commandFactory;
    protected org.saturn.app.facade.impl.Connection hcConnection;
    public final Set<String> subscribers = new HashSet<>();
    public final Map<String, Afk> afkUsers = new HashMap<>();
    private Listener onlineSetListener = new OnlineSetListenerImpl(this);
    private final Listener userJoinedListener = new UserJoinedListenerImpl(this);
    private final Listener userLeftListener = new UserLeftListenerImpl(this);
    private final Listener chatMessageListener = new UserMessageListenerImpl(this);
    private final Listener infoMessageListener = new InfoMessageListenerImpl(this);
    private final Listener connectionListener = new ConnectionListenerImpl(this);
    private final Listener incomingMessageListener = new IncomingMessageListenerImpl(this);


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
            log.debug("Started blocking connection");
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
            log.debug("Started non-blocking connection");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendJoinMessage() {
        String joinPayload = String.format(JOIN_JSON, channel, nick, password);
        hcConnection.write(joinPayload);
        log.debug("Sent join payload: {}", joinPayload);
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
        if (hcConnection == null) {
            log.error("Can't flush the message - Connection is closed");
            return;
        }

        if (message != null) {
            log.debug("Flushing message: {}", message);
            hcConnection.write(message);
        } else {
            log.debug("Message can't be null");
        }
    }

    @Override
    public void stop() {
        log.debug("Closing the connection...");
        try {
            if (hcConnection != null) {
                this.hcConnection.close();
                log.debug("Closed the connection...");
            } else {
                log.debug("Connection is already closed");
            }
        } catch (Exception e) {
            log.error("Exception: ", e);
            e.printStackTrace();
        }
    }

    public void dispatchMessage(String jsonText) {
        try {
            log.debug("Dispatching message: {}", jsonText);
            String cmd = extractCmdFromJson(jsonText);
            switch (cmd) {
                case "join" -> {
                }
                case "onlineSet" -> {
                    onlineSetListener.notify(jsonText);
                }
                case "onlineAdd" -> {
                    userJoinedListener.notify(jsonText);
                }
                case "onlineRemove" -> {
                    userLeftListener.notify(jsonText);
                }
                case "chat" -> {
                    chatMessageListener.notify(jsonText);
                }
                case "info" -> {
                    infoMessageListener.notify(jsonText);
                }
                default -> log.warn("Non functional payload: {}", jsonText);
            }
        } catch (Exception e) {
            log.error("Warning: {}", e.getMessage());
            log.error("Stack trace:", e);
        }
    }

    public void shareUserInfo(User user) {
        String joinedUserData = sqlService.getBasicUserData(user.getHash(), user.getTrip());
        for (String subTrip : subscribers) {
            List<User> tripUsers = currentChannelUsers.stream().filter(u -> u.getTrip().equalsIgnoreCase(subTrip)).collect(Collectors.toList());
            tripUsers.forEach(u -> {
                log.warn("Sharing hash, nick lists with subscriber: {}, trip: {} ", u.getNick(), u.getTrip());
                outService.enqueueMessageForSending(u.getNick(), " -\\n\\n" + joinedUserData, true);
            });
        }
    }

    public void proceedShadowBanned(User user) {
        boolean isBanned = modService.isBanned(user);
        if (isBanned) {
            log.info("User is banned: {}", user.getNick());
            modService.kick(user.getNick());
            log.warn("User: {} has been kicked", user.getNick());
        }
    }

    public void removeActiveUser(String leftUser) {
        for (User user : currentChannelUsers) {
            if (leftUser.equals(user.getNick())) {
                currentChannelUsers.remove(user);
                log.info("User left: {}", user.getNick());
                logService.logMessage(user.getTrip(), user.getNick(), user.getHash(), "LEFT", DateUtil.getTimestampNow());
            }
        }
    }

    public void addActiveUser(User newUser) {
        currentChannelUsers.add(newUser);
        log.info("Added user: {}, to list of active users", newUser.getNick());
        logService.logMessage(newUser.getTrip(), newUser.getNick(), newUser.getHash(), "JOINED", DateUtil.getTimestampNow());
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

    public void notifyUserNotAfkAnymore(User user) {
        Optional<Afk> afk =Optional.empty();
        for (String trip : afkUsers.keySet()) {
            if (trip.equals(user.getTrip())) {
                afk = Optional.ofNullable(afkUsers.get(trip));
                break;
            }
        }

        if (afk.isPresent()) {
            String ago = "was afk for " + getDifference(ZonedDateTime.now(), afk.get().getAfkOn());
            String reason = afk.get().getReason();
            outService.enqueueMessageForSending(user.getNick(), ago + "\\n reason: " + reason, false);
            afkUsers.remove(user.getTrip());
            log.debug("Removed user: {}, trip: {}, from afk list", user.getNick(), user.getTrip());
        }
    }

    public void notifyIsAfkIfUserIsMentioned(String author, String messageText) {
        afkUsers.forEach((trip, afk) -> {
            List<User> users = afk.getUsers();
            List<String> afkNicks = users.stream().map(User::getNick).toList();
            for (User user : users) {
                if (messageText.contains(user.getNick()) || messageText.contains(user.getTrip())) {
                    outService.enqueueMessageForSending(author, "Users:" + afkNicks + ", trip: "  + user.getTrip() + " are currently away from keyboard! Reason: " + afk.getReason(), false);
                    return;
                }
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
            log.info("User: {}, got pending whisper messages", author);
            String whisperMailPayload = formatMail(whisperMails);
            outService.enqueueMessageForSending(author," new mail: \\n " + whisperMailPayload, true);
        }

        List<Mail> publicMessages = getMail(messages, false);
        if (!publicMessages.isEmpty()) {
            log.info("User: {}, got pending messages", author);
            String publicMailPayload = formatMail(publicMessages);
            outService.enqueueMessageForSending(author," new mail: \\n " + publicMailPayload, false);
        }

        mailService.updateMailStatus(author);
        mailService.updateMailStatus(trip);
        log.debug("Updated message status to 'DELIVERED' for: {}, {}", author, trip);
    }

    private static List<Mail> getMail(List<Mail> messages, boolean isWhisper) {
        return messages.stream().filter(m -> String.valueOf(isWhisper).equals(m.isWhisper)).collect(Collectors.toList());
    }

    private String formatMail(List<Mail> messages) {
        StringBuilder whisperStrings = new StringBuilder();
        messages.forEach(mail -> {
            String header = DateUtil.formatRfc1123(mail.createdDate, TimeUnit.MILLISECONDS, "UTC") + ". " + getDifference(ZonedDateTime.now(), toZoneDateTimeUTC(mail.createdDate)) + " ago.";
            String body =  mail.owner + ": " + mail.message;
            whisperStrings.append(header).append("\\n").append(body).append("\\n &nbsp; \\n");
        });

        return whisperStrings.toString();
    }

    public boolean isConnected() {
       return this.hcConnection.isConnected();
    }
}
