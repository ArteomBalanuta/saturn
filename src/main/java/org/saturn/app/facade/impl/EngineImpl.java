package org.saturn.app.facade.impl;

import static org.saturn.app.util.DateUtil.getDifference;
import static org.saturn.app.util.DateUtil.toZoneDateTimeUTC;
import static org.saturn.app.util.Util.extractFieldFromJson;

import com.google.gson.JsonObject;
import com.moandjiezana.toml.Toml;
import java.io.IOException;
import java.sql.Connection;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.saturn.app.agent.routing.AgentRuntimeFactory;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.command.factory.CommandFactory;
import org.saturn.app.command.impl.admin.WhiskeyReplicaCommandImpl.ProxyTestResult;
import org.saturn.app.facade.Base;
import org.saturn.app.facade.Engine;
import org.saturn.app.facade.EngineType;
import org.saturn.app.listener.Listener;
import org.saturn.app.listener.impl.ConnectionListenerImpl;
import org.saturn.app.listener.impl.IncomingMessageListenerImpl;
import org.saturn.app.listener.impl.InfoMessageListenerImpl;
import org.saturn.app.listener.impl.OnlineSetListenerImpl;
import org.saturn.app.listener.impl.UserJoinedListenerImpl;
import org.saturn.app.listener.impl.UserLeftListenerImpl;
import org.saturn.app.listener.impl.UserMessageListenerImpl;
import org.saturn.app.model.MessageAuditEvent;
import org.saturn.app.model.dto.Afk;
import org.saturn.app.model.dto.BanRecord;
import org.saturn.app.model.dto.Mail;
import org.saturn.app.model.dto.Proxy;
import org.saturn.app.model.dto.User;
import org.saturn.app.util.DateUtil;

@Slf4j
public class EngineImpl extends Base implements Engine {
  private static EngineImpl hostRef = null;
  public final Map<String, EngineImpl> replicasMappedByChannel = new ConcurrentHashMap<>();
  public final Map<String, List<ProxyTestResult>> backupProxiesByChannel = new HashMap<>();
  public List<String> proxies;
  public final CommandFactory commandFactory;
  protected org.saturn.app.facade.impl.Connection hcConnection;
  public final Set<String> subscribers = new HashSet<>();
  public final Map<String, Afk> afkUsers = new HashMap<>();
  private final Map<String, Listener> payloadListeners = new HashMap<>();
  private Listener onlineSetListener = new OnlineSetListenerImpl(this);
  private final Listener userJoinedListener = new UserJoinedListenerImpl(this);
  private final Listener userLeftListener = new UserLeftListenerImpl(this);
  private final Listener chatMessageListener = new UserMessageListenerImpl(this);
  private final Listener infoMessageListener = new InfoMessageListenerImpl(this);
  private final Listener connectionListener = new ConnectionListenerImpl(this);
  private final Listener incomingMessageListener = new IncomingMessageListenerImpl(this);

  public void setOnlineSetListener(Listener listener) {
    this.onlineSetListener = listener;
    registerPayloadListener("onlineSet", listener);
  }

  public EngineImpl(Connection dbConnection, Toml config, EngineType engineType) {
    super(dbConnection, config, engineType);
    if (super.proxies != null) {
      if (!super.proxies.isEmpty() || !super.proxies.isBlank()) {
        this.proxies = Arrays.asList(super.proxies.split(","));
      }
    }

    registerDefaultPayloadListeners();
    this.commandFactory = new CommandFactory(this, CommandAliases.class);
    if (dbPath != null) {
      setAgentService(AgentRuntimeFactory.create(this, config, dbPath, outService));
    }
  }

  private void registerDefaultPayloadListeners() {
    registerPayloadListener("onlineSet", onlineSetListener);
    registerPayloadListener("onlineAdd", userJoinedListener);
    registerPayloadListener("onlineRemove", userLeftListener);
    registerPayloadListener("chat", chatMessageListener);
    registerPayloadListener("info", infoMessageListener);
  }

  public void registerPayloadListener(String command, Listener listener) {
    payloadListeners.put(command, listener);
  }

  public void setHostRef(EngineImpl hostRef) {
    EngineImpl.hostRef = hostRef;
  }

  public EngineImpl getHostRef() {
    return EngineImpl.hostRef;
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
      hcConnection =
          new org.saturn.app.facade.impl.Connection(
              baseWsURL, List.of(connectionListener, incomingMessageListener), null, this);
      hcConnection.startNonBlocking();
      log.debug("Started non-blocking connection");
    } catch (Exception e) {
      log.info("Error: {}", e.getMessage());
      log.error("Exception: ", e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public void start(Proxy proxy) {
    try {
      hcConnection =
          new org.saturn.app.facade.impl.Connection(
              baseWsURL, List.of(connectionListener, incomingMessageListener), proxy, this);
      hcConnection.startNonBlocking();
      log.debug("Started non-blocking connection");
    } catch (Exception e) {
      log.info("Error: {}", e.getMessage());
      log.error("Exception: ", e);
      throw new RuntimeException(e);
    }
  }

  public void sendJoinMessage() {
    String joinPayload = buildJoinPayload(channel, nick, password);
    hcConnection.write(joinPayload);
    log.debug("Sent join payload: {}", joinPayload);
  }

  public synchronized void shareMessages() {
    while (true) {
      boolean sent = false;
      String outgoingMessage = outgoingMessageQueue.poll();
      if (outgoingMessage != null) {
        flushMessage(buildChatPayload(outgoingMessage));
        sent = true;
      }
      String outgoingRawMessage = outgoingRawMessageQueue.poll();
      if (outgoingRawMessage != null) {
        flushMessage(outgoingRawMessage);
        sent = true;
      }
      if (!sent) {
        return;
      }
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

  static String buildChatPayload(String message) {
    JsonObject payload = new JsonObject();
    payload.addProperty("cmd", "chat");
    payload.addProperty("text", message);
    return payload.toString();
  }

  static String buildJoinPayload(String channel, String nick, String password) {
    JsonObject payload = new JsonObject();
    payload.addProperty("cmd", "join");
    payload.addProperty("channel", channel);
    payload.addProperty("nick", "%s#%s".formatted(nick, password));
    return payload.toString();
  }

  @Override
  public void stop() {
    try {
      stopReplicas();
      if (hcConnection != null) {
        log.debug("Closing the host WS connection...");
        this.hcConnection.close();
        log.debug("Closed the WS connection...");
      } else {
        log.debug("WS Connection is already closed");
      }

    } catch (Exception e) {
      log.info("Error: {}", e.getMessage());
      log.error("Exception: ", e);
    } finally {
      if (getAgentService() != null) {
        getAgentService().close();
      }
      closeDbConnection();
    }
  }

  private void stopReplicas() {
    if (replicasMappedByChannel.isEmpty()) {
      return;
    }

    List<Map.Entry<String, EngineImpl>> replicas =
        new ArrayList<>(replicasMappedByChannel.entrySet());
    replicasMappedByChannel.clear();
    for (Map.Entry<String, EngineImpl> replicaEntry : replicas) {
      String channel = replicaEntry.getKey();
      EngineImpl replica = replicaEntry.getValue();
      log.warn("Shutting down replica in channel: {}", channel);
      replica.stop();
      // Trigger reconnection with backup proxy if available (async)
      CompletableFuture.runAsync(
          () -> {
            try {
              org.saturn.app.command.impl.admin.WhiskeyReplicaCommandImpl.reconnectWithBackupProxy(
                  this, "system", channel, "portal");
            } catch (Exception e) {
              log.error("Failed to trigger reconnection for channel: {}", channel, e);
            }
          });
    }
  }

  public final void dispatchMessage(String jsonText) {
    try {
      log.debug("Dispatching message: {}", jsonText);
      String cmd = extractFieldFromJson(jsonText, "cmd");
      if ("join".equals(cmd)) {
        return;
      }

      Listener listener = payloadListeners.get(cmd);
      if (listener == null) {
        log.warn("Non functional payload: {}", jsonText);
        return;
      }

      listener.notify(jsonText);
    } catch (Exception e) {
      log.error("Warning: {}", e.getMessage());
      log.error("Stack trace:", e);
    }
  }

  @Override
  public void addReplica(EngineImpl engine) {
    this.replicasMappedByChannel.put(engine.channel, engine);
  }

  public void shareUserInfo(User user) {
    String joinedUserData = sqlService.getBasicUserData(user.getHash(), user.getTrip());
    for (String subTrip : subscribers) {
      for (User currentUser : currentChannelUsers) {
        if (!subTrip.equalsIgnoreCase(currentUser.getTrip())) {
          continue;
        }
        log.warn(
            "Sharing hash, nick lists with subscriber: {}, trip: {} ",
            currentUser.getNick(),
            currentUser.getTrip());
        outService.enqueueMessageForSending(
            currentUser.getNick(), " -\\n\\n%s".formatted(joinedUserData), true);
      }
    }
  }

  public void kickIfShadowBanned(User user) {
    Optional<BanRecord> bannedUser = modService.isShadowBanned(user);
    if (bannedUser.isPresent()) {
      log.info("Channel: {}, user is banned: {}", user.getChannel(), bannedUser.get());
      modService.kick(user.getNick());
      log.warn("User: {} has been kicked", user.getNick());
    }
  }

  public void removeActiveUser(String leftUser) {
    for (User user : currentChannelUsers) {
      if (leftUser.equals(user.getNick())) {
        currentChannelUsers.remove(user);
        log.info("User left: {}", user.getNick());
        logRepository.logMessage(
            MessageAuditEvent.publicMessage(
                user.getTrip(),
                user.getNick(),
                user.getHash(),
                "LEFT",
                this.channel,
                DateUtil.getTimestampNow()));
      }
    }
  }

  public void addActiveUser(User newUser) {
    currentChannelUsers.add(newUser);
    log.info("Added user: {}, to list of active users", newUser.getNick());
    logRepository.logMessage(
        MessageAuditEvent.publicMessage(
            newUser.getTrip(),
            newUser.getNick(),
            newUser.getHash(),
            "JOINED",
            this.channel,
            DateUtil.getTimestampNow()));
  }

  //
  //        if (command.is(VOTEKICK) && (trustedUsers.contains(trip) || admins.contains(trip))) {
  //            String nick = cmd.substring(9);
  //            modService.votekick(nick);
  //            outService.enqueueMessageForSending(" Vote kick started, please type    :vote
  // reason_here    to vote " +
  //                    "yes. Execution will proceed as 3 votes are reached.");
  //        }
  //        if (command.is(VOTE)) {
  //            modService.vote(author);
  //        }
  //        if (command.is(SENTRY)) {
  //            outService.enqueueMessageForSending("@" + author + " Sentry on!");
  //        }
  //        if (command.is(FISH)) {
  //            outService.enqueueMessageForSending("@" + author + " Bloop bloop!");
  //        }
  //        if (command.is(BABAKIUERIA)) {
  //            outService.enqueueMessageForSending("@" + author + "
  // https://www.youtube.com/watch?v=NqcFg4z6EYY");
  //        }
  //        if (command.is(DRRUDI)) {
  //            outService.enqueueMessageForSending("@" + author + "
  // https://www.youtube.com/watch?v=uPaZWM4bxrM");
  //        }
  //        if (command.is(RUST)) {
  //            outService.enqueueMessageForSending("@" + author + "
  // https://doc.rust-lang.org/book/title-page.html");
  //        }
  //        if (command.is(SOLID)) {
  //            outService.enqueueMessageForSending(Constants.SOLID + " @" + author);
  //        }
  //        if (command.is(SCP)) {
  //            scpService.executeRandomSCP(author);
  //        }
  //        if (command.is(SEARCH)) {
  //            // executeSearch(author, cmd);

  public void notifyUserNotAfkAnymore(User user) {
    Afk afk = afkUsers.get(user.getTrip());
    if (afk != null) {
      String ago = "was afk for %s".formatted(getDifference(ZonedDateTime.now(), afk.getAfkOn()));
      String reason = afk.getReason();
      outService.enqueueMessageForSending(
          user.getNick(), "%s\\n reason: %s".formatted(ago, reason), false);
      afkUsers.remove(user.getTrip());
      log.debug("Removed user: {}, trip: {}, from afk list", user.getNick(), user.getTrip());
    }
  }

  /* TODO: clean up this mess */
  public void printYoutubeThumbnailAndDetails(String author, String messageText) {
    String endingChar = getGetEndingChar(messageText);
    if (messageText.contains("watch?v=")) {
      messageText += " ";
      String id = StringUtils.substringBetween(messageText, "watch?v=", endingChar);
      shareYoutubeThumbnailAndDetails(author, id);
      return;
    }

    if (messageText.contains("youtu.be/")) {
      String id;
      if (messageText.contains("?list")) {
        id = StringUtils.substringBetween(messageText, "youtu.be/", "?list");
      } else {
        messageText += " ";
        id = StringUtils.substringBetween(messageText, "youtu.be/", " ");
      }
      shareYoutubeThumbnailAndDetails(author, id);
    }
  }

  /* TODO: clean up this mess */
  private void shareYoutubeThumbnailAndDetails(String author, String id) {
    String youtubeVidDetails = getYoutubeVidDetails(id);
    String title = StringEscapeUtils.escapeJava(extractFieldFromJson(youtubeVidDetails, "title"));

    String url = "![%s](https://i.ytimg.com/vi/VIDEO_ID/maxresdefault.jpg)".formatted(title);
    String urlFormatted = url.replace("VIDEO_ID", id);
    String payload = "Title: %s%n%s".formatted(title, urlFormatted);
    outService.enqueueMessageForSending(author, StringEscapeUtils.escapeJava(payload), false);
  }

  /* TODO: clean up this mess */
  private String getGetEndingChar(String messageText) {
    String ending = " ";
    int idIndex = messageText.indexOf("watch?v=\"");
    int optIndex = messageText.indexOf('&');
    if (optIndex > idIndex) {
      ending = "&";
    }
    return ending;
  }

  /* TODO: clean up this mess */
  private String getYoutubeVidDetails(String videoId) {
    CloseableHttpClient httpClient = HttpClients.createDefault();
    String uri =
        String.format(
            "https://www.youtube.com/oembed?format=text&url=https://youtube.com/watch?v=%s",
            videoId);
    HttpGet request = new HttpGet(uri);

    // add request headers
    request.addHeader(HttpHeaders.USER_AGENT, "Firefox 59.9.0");

    try (CloseableHttpResponse response = httpClient.execute(request)) {
      String result = null;
      HttpEntity entity = response.getEntity();
      if (entity != null) {
        // return it as a String
        result = EntityUtils.toString(entity);
      }

      if (response.getStatusLine().getStatusCode() != 200) {
        result = "Oopsie.";
      }
      return result;
    } catch (IOException e) {
      e.printStackTrace();
    }

    return null;
  }

  public void notifyIsAfkIfUserIsMentioned(String author, String messageText) {
    for (Afk afk : afkUsers.values()) {
      List<User> users = afk.getUsers();
      for (User user : users) {
        if (!isUserMentioned(messageText, user)) {
          continue;
        }
        outService.enqueueMessageForSending(
            author,
            "Users:%s, trip: %s are currently away from keyboard! Reason: %s"
                .formatted(extractNicknames(users), user.getTrip(), afk.getReason()),
            false);
        return;
      }
    }
  }

  public boolean isUserMentioned(String message, User user) {
    String messageText = message.trim();
    boolean isTripMentioned = messageText.contains(user.getTrip());
    boolean isNickMentioned =
        StringUtils.startsWith(messageText, user.getNick() + " ")
            || StringUtils.startsWith(messageText, "@" + user.getNick() + " ")
            || StringUtils.endsWith(messageText, " " + user.getNick())
            || StringUtils.endsWith(messageText, " @" + user.getNick())
            || messageText.equals(user.getNick())
            || messageText.equals("@" + user.getNick())
            || messageText.contains(" @" + user.getNick() + " ")
            || messageText.contains(" " + user.getNick() + " ");
    return isTripMentioned || isNickMentioned;
  }

  public void deliverMailIfPresent(String author, String trip) {
    List<Mail> messages = mailService.getMailByTrip(trip);
    if (messages.isEmpty()) {
      return;
    }

    List<Mail> whisperMails = new java.util.ArrayList<>();
    List<Mail> publicMessages = new java.util.ArrayList<>();
    for (Mail mail : messages) {
      if (Boolean.parseBoolean(mail.isWhisper)) {
        whisperMails.add(mail);
      } else {
        publicMessages.add(mail);
      }
    }

    if (!whisperMails.isEmpty()) {
      log.info("User: {}, got pending whisper messages", author);
      String whisperMailPayload = formatMail(whisperMails);
      outService.enqueueMessageForSending(
          author, " new mail: \\n %s".formatted(whisperMailPayload), true);
    }

    if (!publicMessages.isEmpty()) {
      log.info("User: {}, got pending messages", author);
      String publicMailPayload = formatMail(publicMessages);
      outService.enqueueMessageForSending(
          author, " new mail: \\n %s".formatted(publicMailPayload), false);
    }

    for (Mail mail : messages) {
      mailService.updateMailStatus(mail.id);
      log.debug("Updated message status with ID: {}, to 'DELIVERED'", mail.id);
    }
  }

  private String formatMail(List<Mail> messages) {
    StringBuilder whisperStrings = new StringBuilder();
    for (Mail mail : messages) {
      String header =
          "%s. %s ago."
              .formatted(
                  DateUtil.formatRfc1123(mail.createdDate, TimeUnit.MILLISECONDS, "UTC"),
                  getDifference(ZonedDateTime.now(), toZoneDateTimeUTC(mail.createdDate)));
      String body = "%s: %s".formatted(mail.owner, mail.message);
      whisperStrings.append(header).append("\\n").append(body).append("\\n &nbsp; \\n");
    }

    return whisperStrings.toString();
  }

  private List<String> extractNicknames(List<User> users) {
    List<String> nicknames = new java.util.ArrayList<>(users.size());
    for (User user : users) {
      nicknames.add(user.getNick());
    }
    return nicknames;
  }

  public boolean isConnected() {
    return this.hcConnection.isConnected();
  }
}
