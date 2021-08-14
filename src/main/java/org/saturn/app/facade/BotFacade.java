package org.saturn.app.facade;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.Executors.newScheduledThreadPool;
import static org.saturn.app.model.WebSocketFrame.maskingKey;
import static org.saturn.app.util.Constants.CHAT_JSON;
import static org.saturn.app.util.Constants.HELP_RESPONSE;
import static org.saturn.app.util.Constants.JOIN_JSON;
import static org.saturn.app.util.Constants.SOLID;
import static org.saturn.app.util.Constants.STANDARD_FRAME_MAX_TEXT_PAYLOAD_SIZE;
import static org.saturn.app.util.Constants.THREAD_NUMBER;
import static org.saturn.app.util.Constants.UPGRADE_REQUEST;
import static org.saturn.app.util.OpCode.TEXT;
import static org.saturn.app.util.OpCode.TEXT_EXTENDED;
import static org.saturn.app.util.Util.getCmdFromJson;
import static org.saturn.app.util.Util.getTimestampNow;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.saturn.app.model.WebSocketFrame;
import org.saturn.app.model.impl.ChatMessage;
import org.saturn.app.model.impl.Connection;
import org.saturn.app.model.impl.ReadDto;
import org.saturn.app.model.impl.User;
import org.saturn.app.model.impl.WebSocketExtendedFrameImpl;
import org.saturn.app.model.impl.WebSocketStandardFrameImpl;
import org.saturn.app.service.LogService;
import org.saturn.app.service.NoteService;
import org.saturn.app.service.SearchService;
import org.saturn.app.service.impl.LogServiceImpl;
import org.saturn.app.service.impl.NoteServiceImpl;
import org.saturn.app.service.impl.SCPServiceImpl;
import org.saturn.app.service.impl.SearchServiceImpl;
import org.saturn.app.util.OpCode;

public class BotFacade {
    public final Gson gson = new Gson();    
    
    public boolean isMainThread;
    public int joinDelay;

    // use interface
    private final SCPServiceImpl scpService;
    private final LogService logService;
    private final NoteService noteService;
    private final SearchService searchService;
    
    public final BlockingQueue<WebSocketFrame> incomingFramesQueue = new ArrayBlockingQueue<>(256);
    public final BlockingQueue<ChatMessage> incomingChatMessageQueue = new ArrayBlockingQueue<>(256);
    public final BlockingQueue<String> outgoingMessageQueue = new ArrayBlockingQueue<>(256);
    
    public volatile List<String> incomingSetOnlineMessageQueue = new ArrayList<>();
    public volatile List<User> currentChannelUsers = new ArrayList<>();

    private final ExecutorService appExecutor = Executors.newFixedThreadPool(THREAD_NUMBER);
    private final ScheduledExecutorService executorScheduler = newScheduledThreadPool(THREAD_NUMBER);

    public BotFacade(java.sql.Connection dbConnection) {
        this.scpService = new SCPServiceImpl();
        this.logService = new LogServiceImpl(dbConnection);
        this.noteService = new NoteServiceImpl(dbConnection);
        this.searchService = new SearchServiceImpl(); // add logs
    }

    private Runnable websocketDispatcherRunnable;
    private Runnable pipeLineRunnable;

    private Connection hcConnection;

    private String channel;
    private String nick;

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public List<String> getIncomingSetOnlineMessageQueue() {
        return incomingSetOnlineMessageQueue;
    }

    private void enqueueMessageForSending(String message){
        outgoingMessageQueue.add(message);
    }

    public void start() {
        setUpConnectionToHackChat();
        setUpAppThreads();

        sendUpgradeRequest();
        sleep();
        sendJoinMessage(channel, nick);
    }

    public void stop() {
        this.executorScheduler.shutdownNow();
        this.appExecutor.shutdownNow();
        try {
            boolean awaitTermination = this.appExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        this.hcConnection.close();
        this.hcConnection = null;
    }

    private void sleep() {
        try {
            Thread.sleep(this.joinDelay);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void setUpConnectionToHackChat() {
        String uri = "hack.chat";
        int port = 443;
        hcConnection = new Connection(uri, port, this.isMainThread);
    }

    private void sendUpgradeRequest() {
        try {
            hcConnection.write(UPGRADE_REQUEST.getBytes(UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*TODO: make sure join payload size always fits standard frame size */
    private void sendJoinMessage(String channel, String nick) {
        String joinPayload = String.format(JOIN_JSON, channel, nick);
        WebSocketFrame joinFrame = new WebSocketStandardFrameImpl(joinPayload);

        try {
            hcConnection.write(joinFrame.getWebSocketWriteTextBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendChatMessage(String message) {
        String chatPayload = String.format(CHAT_JSON, message);

        WebSocketFrame chatFrame;
        if (chatPayload.length() > STANDARD_FRAME_MAX_TEXT_PAYLOAD_SIZE) {
            chatFrame = new WebSocketExtendedFrameImpl(chatPayload);
        } else {
            chatFrame = new WebSocketStandardFrameImpl(chatPayload);
        }

        try {
            hcConnection.write(chatFrame.getWebSocketWriteTextBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendPong(Connection connection) {
        int finByte = 0b10001010;
        int maskByte = 0b10000000;
        int[] pong = new int[]{finByte, maskByte, maskingKey[0], maskingKey[1], maskingKey[2], maskingKey[3]};

        byte[] shrinkedBuffer = new byte[6];
        for (int i = 0; i < 6; i++) {
            shrinkedBuffer[i] = (byte) pong[i];
        }

        try {
            connection.write(shrinkedBuffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setUpAppThreads() {
        websocketDispatcherRunnable = () -> executorScheduler.scheduleWithFixedDelay(() -> websocketFrameDispatcher(), 0, 50, TimeUnit.MILLISECONDS);

        // ADD CALLBACKS
        pipeLineRunnable = () -> executorScheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        messageDispatcher();
                        messageProcessor();
                        shareMessages();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }, 0, 50, TimeUnit.MILLISECONDS);

        appExecutor.submit(pipeLineRunnable);
        appExecutor.submit(websocketDispatcherRunnable);

        pipeLineRunnable.run();
        websocketDispatcherRunnable.run();
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
                    {"cmd":"onlineSet",
                    "nicks":["test","JavaBot"],
                    "users":
                    [
                    {"channel":"forge", "isme":false,             "nick":"test",   "trip":"8Wotmg","uType":"user","hash":"Nn2jIz8w2Wk9qbo","level":100,"userid":3707326840729,"isBot":false,"color":false},
                    {"channel":"forge", "isme":true,              "nick":"JavaBot","trip":"XBotUU","uType":"user","hash":"Nn2jIz8w2Wk9qbo","level":100,"userid":6883928675253,"isBot":false,"color":false}
                    ]
                    ,"channel":"forge","time":1624984540229}


                    channel,isme bool, nick, trip, uType, hash, level int , userId long, isBot bool, color bool
                     */
                    if (this.isMainThread) {
                        setupActiveUsers(jsonText);
                        logService.log("joined channel", "successfully", getTimestampNow());
                        break;
                    } else {
                        // 'list cmd users setter 
                        incomingSetOnlineMessageQueue.add(jsonText);
                        break;
                    }
                }
                case "chat": {
                    ChatMessage message = gson.fromJson(jsonText, ChatMessage.class);
                    
                    boolean isBotMessage = message.getNick().equals(this.nick);
                    if (isBotMessage) {
                        break;
                    }
                    
                    incomingChatMessageQueue.add(message);

                    String hcMessage = format("%-5d ", jsonText.length()) + message.getTime() + " "
                            + format("%-6s ", message.getTrip()) + " "
                            + format("%-15s ", message.getNick()) + ":" + " " + message.getText();

                    logService.log("chat", hcMessage, getTimestampNow());
                    break;

                }
                default:
                    System.out.printf("Text payload: %s \n", jsonText);
                    break;
            }
        }
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

            String cmd = message.getText().toLowerCase().trim();

            if (cmd.equals("'help")) {
                enqueueMessageForSending(HELP_RESPONSE);
            } else if (cmd.equals("'fish")) {
                enqueueMessageForSending("@" + author + " Bloop bloop!");
            } else if (cmd.contains("'list")) {
                executeListCommand(cmd, author);
            } else if (cmd.equals("'babakiueria")) {
                enqueueMessageForSending("@" + author + " https://www.youtube.com/watch?v=NqcFg4z6EYY");
            } else if (cmd.equals("'drrudi")) {
                enqueueMessageForSending("@" + author + " https://www.youtube.com/watch?v=uPaZWM4bxrM");
            } else if (cmd.equals("'rust")) {
                enqueueMessageForSending("@" + author + " https://doc.rust-lang.org/book/title-page.html");
            } else if (cmd.equals("'ping")) {
                long ms = executePing();
                enqueueMessageForSending("@" + author + " response time: " + ms + " milliseconds");
            } else if (cmd.equals("'solid")) {
                enqueueMessageForSending(SOLID + " @" + author);
            } else if (cmd.equals("'scp")) {
            
                // http://www.scpwiki.com/scp-XXX
                int randomScpId = RandomUtils.nextInt(1, 5500);

                // consider async flow
                String scpDescription = scpService.getSCPDescription(randomScpId);

                enqueueMessageForSending("```Text \\n" + scpDescription.trim() + " \\n```\\n " + "@" + author);
            
            } else if (cmd.contains("'note ")) {
                String[] args = cmd.split(" ");
                StringBuilder note = new StringBuilder();
                for (int i = 1; i < args.length; i++) {
                    note.append(" ").append(args[i]);
                }
                noteService.save(trip, note.toString());
            
            } else if (cmd.equals("'notes")) {
                List<String> notes = noteService.getNotesByTrip(trip);
                enqueueMessageForSending("@" + author + "'s notes: \\n ```Text \\n" + notes.toString() + "\\n```");
            } else if (cmd.equals("'notes purge")) {
                noteService.clearNotesByTrip(trip);
                enqueueMessageForSending("@" + author + "'s notes are gone");
            } else if (cmd.contains("'s ")) {
                String[] args = cmd.split(" ");
                StringBuilder searchString = new StringBuilder();
                for (int i = 1; i < args.length; i++) {
                    searchString.append(" ").append(args[i]);
                }
                String resultJson = searchService.search(searchString.toString());
                enqueueMessageForSending("@" + author + " \\n" + resultJson);
            }
        }
    }

    private long executePing() {
        long timeToRespond = 0;
        try {
            String hostAddress = "hack.chat";
            int port = 80;
            
            InetAddress inetAddress = InetAddress.getByName(hostAddress);
            InetSocketAddress socketAddress = new InetSocketAddress(inetAddress, port);
    
            SocketChannel sc = SocketChannel.open();
            sc.configureBlocking(true);
    
            Date start = new Date();
            if (sc.connect(socketAddress)) {
               Date stop = new Date();
               timeToRespond = (stop.getTime() - start.getTime());
            }
    
            System.out.println("Response time: " + timeToRespond + " ms");
    
         } catch (IOException ex) {
            System.out.println(ex.getMessage());
         }

         return timeToRespond;
    }

    private void executeListCommand(String cmd, String author) {
        String[] args = cmd.split(" ");
        String list = args[0];
        String channel = null;
        if (args.length > 1) {
            channel = args[1];
        }

        if (list.equals("'list")) {
            if (channel != null && !channel.equals(this.channel)) {
                List<String> nickList = getNicksFromChannel(channel);

                if (nickList.isEmpty()) {
                    enqueueMessageForSending("@" + author + ", channel - " + channel + " is empty");
                } else {
                    enqueueMessageForSending("@" + author + ", users in '" + channel + "' channel: " + nickList);
                }
            } else {
                // parse nicks from current channel
                String userNames = this.currentChannelUsers.toString();
                enqueueMessageForSending("@" + author + "\\n```Text \\n Users online: " + userNames + "\\n ```");
            }
        }
    }

    private List<String> getNicksFromChannel(String channel) {
        BotFacade listBot = new BotFacade(null); // no db connection for this one is needed
        listBot.isMainThread = false;
        listBot.setChannel(channel);
        listBot.joinDelay = 1000;

        int length = 8;
        boolean useLetters = true;
        boolean useNumbers = true;
        String generatedNick = RandomStringUtils.random(length, useLetters, useNumbers);
        listBot.setNick(generatedNick);

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

    public void shareMessages() {
        if (!outgoingMessageQueue.isEmpty()) {
            sendChatMessage(outgoingMessageQueue.poll());
        }
    }

    public synchronized void websocketFrameDispatcher() {
        try {
            if (this.hcConnection != null) {
                ReadDto readDto = this.hcConnection.read();
                byte[] bytes = readDto.bytes;
                int nrOfBytes = readDto.nrOfBytesRead;

                if (nrOfBytes != -1) {
                    boolean isWebSocketPing = (0xff & bytes[0]) == 0b10001001;
                    boolean isWebSocketText = (0xff & bytes[0]) == 0b10000001;
                    boolean isExtendedFrame = (0xff & bytes[1]) == 126;

                    if (isWebSocketText) {
                        OpCode opCode = isExtendedFrame ? TEXT_EXTENDED : TEXT;
                        WebSocketFrame webSocketFrame = getWebSocketFrame(bytes, opCode);
                        incomingFramesQueue.add(webSocketFrame);
                    }

                    /* Ponging */
                    if (isWebSocketPing && isMainThread) {
                        sendPong(hcConnection);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private WebSocketFrame getWebSocketFrame(byte[] bytes, OpCode opCode) {
        WebSocketFrame webSocketFrame = null;
        if (opCode == TEXT_EXTENDED) {
            webSocketFrame = new WebSocketExtendedFrameImpl(bytes);
        }
        if (opCode == TEXT) {
            webSocketFrame = new WebSocketStandardFrameImpl(bytes);
        }

        return webSocketFrame;
    }
}
