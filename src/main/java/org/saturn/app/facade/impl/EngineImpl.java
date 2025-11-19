package org.saturn.app.facade.impl;

import static org.saturn.app.util.Constants.CHAT_JSON;
import static org.saturn.app.util.Constants.JOIN_JSON;
import static org.saturn.app.util.DateUtil.getDifference;
import static org.saturn.app.util.DateUtil.toZoneDateTimeUTC;
import static org.saturn.app.util.Util.extractFieldFromJson;

import com.moandjiezana.toml.Toml;

import java.io.IOException;
import java.sql.Connection;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.command.factory.CommandFactory;
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
import org.saturn.app.model.dto.Afk;
import org.saturn.app.model.dto.BanRecord;
import org.saturn.app.model.dto.Mail;
import org.saturn.app.model.dto.Proxy;
import org.saturn.app.model.dto.User;
import org.saturn.app.util.DateUtil;

@Slf4j
public class EngineImpl extends Base implements Engine {
  private static EngineImpl hostRef = null;
  public final Map<String, EngineImpl> replicasMappedByChannel = new HashMap<>();
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

  public EngineImpl(Connection dbConnection, Toml config, EngineType engineType) {
    super(dbConnection, config, engineType);
    if (super.proxies != null) {
      if (!super.proxies.isEmpty() || !super.proxies.isBlank()) {
        this.proxies = Arrays.asList(super.proxies.split(","));
      }
    }

    this.commandFactory = new CommandFactory(this, CommandAliases.class);
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
    if (!this.replicasMappedByChannel.isEmpty()) {
      this.replicasMappedByChannel.forEach(
          (channel, replica) -> {
            log.warn("Shutting down replica in channel: {}", channel);
            try {
              replica.hcConnection.close();
            } catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
          });
    }

    try {
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
    }
  }

  public final void dispatchMessage(String jsonText) {
    try {
      log.debug("Dispatching message: {}", jsonText);
      String cmd = extractFieldFromJson(jsonText, "cmd");
      switch (cmd) {
        case "join" -> {}
        case "onlineSet" -> onlineSetListener.notify(jsonText);
        case "onlineAdd" -> userJoinedListener.notify(jsonText);
        case "onlineRemove" -> userLeftListener.notify(jsonText);
        case "chat" -> chatMessageListener.notify(jsonText);
        case "info" -> infoMessageListener.notify(jsonText);
        default -> log.warn("Non functional payload: {}", jsonText);
      }
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
      List<User> tripUsers =
          currentChannelUsers.stream().filter(u -> u.getTrip().equalsIgnoreCase(subTrip)).toList();
      tripUsers.forEach(
          u -> {
            log.warn(
                "Sharing hash, nick lists with subscriber: {}, trip: {} ",
                u.getNick(),
                u.getTrip());
            outService.enqueueMessageForSending(u.getNick(), " -\\n\\n" + joinedUserData, true);
          });
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
            user.getTrip(),
            user.getNick(),
            user.getHash(),
            "LEFT",
            this.channel,
            DateUtil.getTimestampNow());
      }
    }
  }

  public void addActiveUser(User newUser) {
    currentChannelUsers.add(newUser);
    log.info("Added user: {}, to list of active users", newUser.getNick());
    logRepository.logMessage(
        newUser.getTrip(),
        newUser.getNick(),
        newUser.getHash(),
        "JOINED",
        this.channel,
        DateUtil.getTimestampNow());
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
    Optional<Afk> afk = Optional.empty();
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
    String title = extractFieldFromJson(youtubeVidDetails, "title");

    String url = "![" + title + "](https://i.ytimg.com/vi/VIDEO_ID/maxresdefault.jpg)";
    String urlFormatted = url.replace("VIDEO_ID", id);
    String payload = "Title: " + StringEscapeUtils.escapeJava(title) + "\\n" + urlFormatted;
    outService.enqueueMessageForSending(author,  payload, false);
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
    afkUsers.forEach(
        (trip, afk) -> {
          List<User> users = afk.getUsers();
          List<String> afkNicks = users.stream().map(User::getNick).toList();
          for (User user : users) {
            if (isUserMentioned(messageText, user)) {
              outService.enqueueMessageForSending(
                  author,
                  "Users:"
                      + afkNicks
                      + ", trip: "
                      + user.getTrip()
                      + " are currently away from keyboard! Reason: "
                      + afk.getReason(),
                  false);
              return;
            }
          }
        });
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

    List<Mail> whisperMails = getMail(messages, true);
    if (!whisperMails.isEmpty()) {
      log.info("User: {}, got pending whisper messages", author);
      String whisperMailPayload = formatMail(whisperMails);
      outService.enqueueMessageForSending(author, " new mail: \\n " + whisperMailPayload, true);
    }

    List<Mail> publicMessages = getMail(messages, false);
    if (!publicMessages.isEmpty()) {
      log.info("User: {}, got pending messages", author);
      String publicMailPayload = formatMail(publicMessages);
      outService.enqueueMessageForSending(author, " new mail: \\n " + publicMailPayload, false);
    }

    messages.forEach(
        m -> {
          mailService.updateMailStatus(m.id);
          log.debug("Updated message status with ID: {}, to 'DELIVERED'", m.id);
        });
  }

  private static List<Mail> getMail(List<Mail> messages, boolean isWhisper) {
    return messages.stream()
        .filter(m -> String.valueOf(isWhisper).equals(m.isWhisper))
        .collect(Collectors.toList());
  }

  private String formatMail(List<Mail> messages) {
    StringBuilder whisperStrings = new StringBuilder();
    messages.forEach(
        mail -> {
          String header =
              DateUtil.formatRfc1123(mail.createdDate, TimeUnit.MILLISECONDS, "UTC")
                  + ". "
                  + getDifference(ZonedDateTime.now(), toZoneDateTimeUTC(mail.createdDate))
                  + " ago.";
          String body = mail.owner + ": " + mail.message;
          whisperStrings.append(header).append("\\n").append(body).append("\\n &nbsp; \\n");
        });

    return whisperStrings.toString();
  }

  public boolean isConnected() {
    return this.hcConnection.isConnected();
  }
}
