package org.saturn;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.RandomStringUtils;
import org.saturn.app.connection.Connection;
import org.saturn.app.model.WebSocketFrame;
import org.saturn.app.model.impl.ChatMessage;
import org.saturn.app.model.impl.ReadDto;
import org.saturn.app.model.impl.WebSocketExtendedFrameImpl;
import org.saturn.app.model.impl.WebSocketStandardFrameImpl;
import org.saturn.app.util.OpCode;

import java.io.IOException;
import java.util.ArrayList;
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
    public boolean isMainThread;
    public int joinDelay;

    public final Gson gson = new Gson();

    public final BlockingQueue<WebSocketFrame> incomingFramesQueue = new ArrayBlockingQueue<>(256);
    public final BlockingQueue<ChatMessage> incomingChatMessageQueue = new ArrayBlockingQueue<>(256);
    public volatile BlockingQueue<String> outgoingMessageQueue = new ArrayBlockingQueue<>(256);

    public volatile List<String> incomingSetOnlineMessageQueue = new ArrayList<>();

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
            switch (getCmdFromJson(jsonText)) {
                case "join": {
                    break;
                }
                case "onlineSet": {
                    if (this.isMainThread) {
                        break;
                    } else {
                        incomingSetOnlineMessageQueue.add(jsonText);
                        System.out.println("Added onlineSet: " + jsonText);
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
//        System.out.println("messageProcessor() triggered");
        if (!incomingChatMessageQueue.isEmpty()) {
            ChatMessage message = incomingChatMessageQueue.poll();

            String cmd = message.getText().toLowerCase().trim();
            if (cmd.equals("'help")) {
                String helpResponse = "```" +
                        "Text \\n Welcome and have fun ;) \\n \\n" +
                        "Supported commands: \\n" +
                        "'help - prints menu with supported commands. \\n" +
                        "'fish - prints 'bloop bloop'. \\n" +
                        "'list $channel_name - prints active users in the specified channel with delay of 3 seconds. \\n" +
                        "```";
                outgoingMessageQueue.add(helpResponse);
            }

            if (cmd.equals("'fish")) {
                outgoingMessageQueue.add("Bloop bloop!");
            }

            if (cmd.contains("'list ")) {
                String[] args = cmd.split(" ");
                String list = args[0];
                String channel = args[1];
                if (list.equals("'list") && channel != null && !channel.equals("") && !channel.equals(" ")) {
                    if (!channel.equals(this.channel)) {
                        List<String> nickList = getNicksFromChannel(channel);

                        if (nickList.isEmpty()) {
                            outgoingMessageQueue.add("Channel - " + channel + " is empty");
                        } else {
                            outgoingMessageQueue.add("Users in '" + channel + "' channel: " + nickList);
                        }
                    } else {
                        // parse nicks from current channel
                        outgoingMessageQueue.add("List for current channel is not implemented yet. Check yourself.");
                    }
                }
            }
        }
    }

    private static List<String> getNicksFromChannel(String channel) {
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
