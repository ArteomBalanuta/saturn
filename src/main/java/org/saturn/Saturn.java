package org.saturn;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.saturn.app.connection.Connection;
import org.saturn.app.model.WebSocketFrame;
import org.saturn.app.model.impl.*;
import org.saturn.app.service.ExternalService;
import org.saturn.app.service.NoteService;
import org.saturn.app.service.impl.NoteServiceImpl;
import org.saturn.app.util.OpCode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.Executors.newScheduledThreadPool;
import static org.saturn.app.model.WebSocketFrame.maskingKey;
import static org.saturn.app.util.Constants.*;
import static org.saturn.app.util.OpCode.TEXT;
import static org.saturn.app.util.OpCode.TEXT_EXTENDED;
import static org.saturn.app.util.Util.getCmdFromJson;

public class Saturn {
    // use the DI lib!
    private final ExternalService externalServicesService = new ExternalService();
    public boolean isMainThread;
    public int joinDelay;

    public final Gson gson = new Gson();

    public final BlockingQueue<WebSocketFrame> incomingFramesQueue = new ArrayBlockingQueue<>(256);
    public final BlockingQueue<ChatMessage> incomingChatMessageQueue = new ArrayBlockingQueue<>(256);
    public volatile BlockingQueue<String> outgoingMessageQueue = new ArrayBlockingQueue<>(256);

    public volatile List<String> incomingSetOnlineMessageQueue = new ArrayList<>();

    public volatile List<User> currentChannelUsers = new ArrayList<>();

    private final ExecutorService appExecutor = Executors.newFixedThreadPool(THREAD_NUMBER);
    private final ScheduledExecutorService executorScheduler = newScheduledThreadPool(THREAD_NUMBER);

    private Runnable websocketDispatcherRunnable;
    private Runnable pipeLineRunnable;

    private Connection connection;

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

    public void start() {
        setUpConnectionToHackChat();
        setUpAppThreads();

        sendUpgradeRequest();
        sleep();
        sendJoinMessage(channel, nick);
    }

    public void stop() {
        /* make sure */
        this.executorScheduler.shutdownNow();
        this.appExecutor.shutdownNow();
        try {
            boolean awaitTermination = this.appExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        this.connection.close();
        this.connection = null;
    }

    public static void main(String[] args) {
        Saturn saturn = new Saturn();
        saturn.setChannel("programming");
        saturn.setNick("JavaBot#256c392");
        saturn.isMainThread = true;
        saturn.joinDelay = 1000;

        saturn.start();
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
        connection = new Connection(uri, port, this.isMainThread);
    }

    private void sendUpgradeRequest() {
        try {
            connection.write(UPGRADE_REQUEST.getBytes(UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*TODO: make sure join payload size always fits standard frame */
    private void sendJoinMessage(String channel, String nick) {
        String joinPayload = String.format(JOIN_JSON, channel, nick);
        WebSocketFrame joinFrame = new WebSocketStandardFrameImpl(joinPayload);

        try {
            connection.write(joinFrame.getWebSocketWriteTextBytes());
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
            connection.write(chatFrame.getWebSocketWriteTextBytes());
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
//        System.out.println("messageDispatcher() triggered");
        if (!incomingFramesQueue.isEmpty()) {
            WebSocketFrame frame = incomingFramesQueue.poll();
            String jsonText = new String(frame.getWebSocketReadTextBytes());
            System.out.println("GOT: " + jsonText);
            switch (getCmdFromJson(jsonText)) {
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
                        JsonElement element = new JsonParser().parse(jsonText);
                        JsonElement listingElement = element.getAsJsonObject().get("users");
                        User[] users = gson.fromJson(listingElement, User[].class);

                        currentChannelUsers.addAll(Arrays.asList(users));
                        break;
                    } else {
                        incomingSetOnlineMessageQueue.add(jsonText);
                        break;
                    }
                }
                case "chat": {
                    ChatMessage message = gson.fromJson(jsonText, ChatMessage.class);
                    if (message.getNick().equals(this.nick)) {
                        break;
                    } else {
                        incomingChatMessageQueue.add(message);

                        System.out.println(String.format("%-5d ", jsonText.length()) +
                                message.getTime() + " " +
                                String.format("%-6s ", message.getTrip()) + " " +
                                String.format("%-15s ", message.getNick()) + ":" + " " +
                                message.getText());
                        break;
                    }
                }
                default:
                    System.out.printf("Text payload: %s \n", jsonText);
                    break;
            }
        }
    }

    public void messageProcessor() {
        if (!incomingChatMessageQueue.isEmpty()) {
            ChatMessage message = incomingChatMessageQueue.poll();

            String author = message.getNick();
            String trip = message.getTrip();
            String cmd = message.getText().toLowerCase().trim();
            if (cmd.equals("'help")) {
                String helpResponse = "```" +
                        "Text \\n Welcome and have fun ;) \\n \\n" +
                        "Supported commands: \\n" +
                        "'help          - prints menu with supported commands. \\n" +
                        "'drRudi        - free medical consultation from Dr Rudi. \\n" +
                        "'babakiueria   - strong Australian native name. \\n" +
                        "'scp           - details on random SCP. \\n" +
                        "'SOLID         - solid. \\n" +
                        "'Rust          - prints Rust's doc page. \\n" +
                        "\\n" +
                        "'note $note    - keeps the note. \\n" +
                        "'notes         - prints saved notes. \\n" +
                        "'notes purge   - removes saved notes. \\n" +
                        " \\n" +
                        "'fish          - prints 'bloop bloop'. \\n" +
                        "'list $channel - prints active users in the specified channel with delay of 3 seconds.\\n                 " +
                        "If channel is not set prints users in current channel \\n" +
                        "```";
                outgoingMessageQueue.add(helpResponse);
            }

            if (cmd.equals("'fish")) {
                outgoingMessageQueue.add("@" + author + " Bloop bloop!");
            }

            if (cmd.contains("'list")) {
                executeListCommand(cmd, author);
            }

            if (cmd.equals("'babakiueria")) {
                outgoingMessageQueue.add("@" + author + " https://www.youtube.com/watch?v=NqcFg4z6EYY");
            }

            if (cmd.equals("'drrudi")) {
                outgoingMessageQueue.add("@" + author + " https://www.youtube.com/watch?v=uPaZWM4bxrM");
            }

            if (cmd.equals("'rust")) {
                outgoingMessageQueue.add("@" + author + " https://doc.rust-lang.org/book/title-page.html");
            }

            if (cmd.equals("'solid")) {
                String solid = "```Text \\n" +
                        "S - single responsibility principle \\n" +
                        "O - open-close principle \\n" +
                        "L - liskov substitution principle \\n" +
                        "I - interface segregation principle \\n" +
                        "D - dependency inversion principle \\n" +
                        "``` \\n";
                outgoingMessageQueue.add(solid + " @" + author);
            }

            if (cmd.equals("'scp")) {
                //http://www.scpwiki.com/scp-XXX
                int randomScpId = RandomUtils.nextInt(1, 5500);

                // consider async flow
                String scpDescription = externalServicesService.getSCPDescription(randomScpId);

                outgoingMessageQueue.add("```Text \\n" + scpDescription.trim() + " \\n```\\n " + "@" + author);
                // 50 - 5500
            }
            if (cmd.contains("'note ")) {
                String[] args = cmd.split(" ");
                StringBuilder note = new StringBuilder();
                for(int i=1; i<args.length; i++) {
                    note.append(" ").append(args[i]);
                }

                NoteService ns = new NoteServiceImpl();
                ns.save(trip, note.toString());
            }

            if (cmd.equals("'notes")) {
                NoteService ns = new NoteServiceImpl();
                List<String> notes = ns.getNotesByTrip(trip);
                outgoingMessageQueue.add("@" + author + "'s notes: \\n ```Text \\n" + notes.toString() + "\\n```");
            }

            if (cmd.equals("'notes purge")) {
                NoteService ns = new NoteServiceImpl();
                ns.clearNotesByTrip(trip);
                outgoingMessageQueue.add("@" + author + "'s notes are gone");
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

        if (list.equals("'list")) {
            if (channel != null && !channel.equals(this.channel)) {
                List<String> nickList = getNicksFromChannel(channel);

                if (nickList.isEmpty()) {
                    outgoingMessageQueue.add("@" + author + ", channel - " + channel + " is empty");
                } else {
                    outgoingMessageQueue.add("@" + author + ", users in '" + channel + "' channel: " + nickList);
                }
            } else {
                // parse nicks from current channel
                String userNames = this.currentChannelUsers.toString();
                outgoingMessageQueue.add("@" + author + "\\n```Text \\n Users online: " + userNames + "\\n ```");
            }
        }
    }

    private List<String> getNicksFromChannel(String channel) {
        Saturn listBot = new Saturn();
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
//        System.out.println("shareMessages() triggered");
        if (!outgoingMessageQueue.isEmpty()) {
            sendChatMessage(outgoingMessageQueue.poll());
        }
    }

    public synchronized void websocketFrameDispatcher() {
        try {
            if (this.connection != null) {
                ReadDto readDto = this.connection.read();
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
                        sendPong(connection);
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
