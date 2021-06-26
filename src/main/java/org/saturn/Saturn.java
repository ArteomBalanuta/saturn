package org.saturn;

import com.google.gson.Gson;
import org.saturn.app.connection.Connection;
import org.saturn.app.model.WebSocketFrame;
import org.saturn.app.model.impl.ChatMessage;
import org.saturn.app.model.impl.ReadDto;
import org.saturn.app.model.impl.WebSocketExtendedFrameImpl;
import org.saturn.app.model.impl.WebSocketStandardFrameImpl;
import org.saturn.app.util.OpCode;

import java.io.IOException;
import java.util.concurrent.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.Executors.newScheduledThreadPool;
import static org.saturn.app.model.WebSocketFrame.maskingKey;
import static org.saturn.app.util.Constants.*;
import static org.saturn.app.util.OpCode.TEXT;
import static org.saturn.app.util.OpCode.TEXT_EXTENDED;
import static org.saturn.app.util.Util.getCmdFromJson;

public class Saturn {
    public static Gson gson = new Gson();

    public static final BlockingQueue<WebSocketFrame> incomingFramesQueue = new ArrayBlockingQueue<>(1024);
    public static final BlockingQueue<ChatMessage> incomingMessageQueue = new ArrayBlockingQueue<>(1024);

    public static final BlockingQueue<String> outgoingMessageQueue = new ArrayBlockingQueue<>(1024);

    private static final ExecutorService appExecutor = Executors.newFixedThreadPool(THREAD_NUMBER);
    private static final ScheduledExecutorService executorScheduler = newScheduledThreadPool(THREAD_NUMBER);

    private static Runnable websocketDispatcherRunnable;
    private static Runnable messageDispatcherRunnable;
    private static Runnable messageProcessorRunnable;
    private static Runnable messageSenderRunnable;

    private static volatile Connection connection;

    public static void main(String[] args) {
        setUpConnectionToHackChat();
        setUpAppThreads();

        sendUpgradeRequest();
        sleep(2000);
        sendJoinMessage("forge", "JavaBot");

//            sendChatMessage("!weather Chisinau");

    }

    private static void sleep(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void setUpConnectionToHackChat() {
        String uri = "hack.chat";
        int port = 443;
        connection = new Connection(uri, port);
    }

    private static void sendUpgradeRequest() {
        try {
            connection.write(UPGRADE_REQUEST.getBytes(UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*TODO: make sure join payload size always fits standard frame */
    private static void sendJoinMessage(String channel, String nick) {
        String joinPayload = String.format(JOIN_JSON, channel, nick);
        WebSocketFrame joinFrame = new WebSocketStandardFrameImpl(joinPayload);

        try {
            connection.write(joinFrame.getWebSocketWriteTextBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendChatMessage(String message) {
        String chatPayload = String.format(CHAT_JSON, message);
        WebSocketFrame chatFrame = new WebSocketStandardFrameImpl(chatPayload);

        if (chatPayload.length() > STANDARD_FRAME_MAX_TEXT_PAYLOAD_SIZE) {
            chatFrame = new WebSocketExtendedFrameImpl(chatPayload);
        }

        try {
            connection.write(chatFrame.getWebSocketWriteTextBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendPong() {
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

    private static void setUpAppThreads() {
        websocketDispatcherRunnable = () -> executorScheduler.scheduleWithFixedDelay(Saturn::websocketFrameDispatcher, 0, 50, TimeUnit.MILLISECONDS);

        messageDispatcherRunnable = () -> executorScheduler.scheduleWithFixedDelay(Saturn::messageDispatcher, 0, 50, TimeUnit.MILLISECONDS);

        messageProcessorRunnable = () -> executorScheduler.scheduleWithFixedDelay(Saturn::messageProcessor, 0, 50, TimeUnit.MILLISECONDS);

        messageSenderRunnable = () -> executorScheduler.scheduleWithFixedDelay(Saturn::shareMesssages, 0, 50, TimeUnit.MILLISECONDS);

        appExecutor.submit(websocketDispatcherRunnable);
        appExecutor.submit(messageDispatcherRunnable);
        appExecutor.submit(messageProcessorRunnable);
        appExecutor.submit(messageSenderRunnable);

        websocketDispatcherRunnable.run();
        messageDispatcherRunnable.run();
        messageProcessorRunnable.run();
    }

    /* Only `chat` event is processed */
    private static void messageDispatcher() {
        if (!incomingFramesQueue.isEmpty()) {
            WebSocketFrame frame = incomingFramesQueue.poll();

            String jsonText = new String(frame.getWebSocketReadTextBytes());
            switch (getCmdFromJson(jsonText)) {
                case "join": {
                    break;
                }
                case "onlineSet": {
                    break;
                }
                case "chat": {
                    ChatMessage message = gson.fromJson(jsonText, ChatMessage.class);
                    if (message.getNick().equals("JavaBot")) {
                        break;
                    }
                    incomingMessageQueue.add(message);

                    System.out.println(String.format("%-5d ", jsonText.length()) +
                            message.getTime() + " " +
                            String.format("%-6s ", message.getTrip()) + " " +
                            String.format("%-15s ", message.getNick()) + ":" + " " +
                            message.getText());
                    break;
                }
                default:
                    System.out.printf("Text payload: %s \n", jsonText);
            }
        }
    }

    public static void messageProcessor() {
        if (!incomingMessageQueue.isEmpty()) {
            ChatMessage message = incomingMessageQueue.poll();
            switch (message.getTrip()) {
                case "8Wotmg": {
                    if (message.getText().toLowerCase().trim().equals("!fish")) {
                        outgoingMessageQueue.add("BeEp bEep, bloop bl0op " + message.getNick() + "!");
                    }
                    break;
                }
                case "KEt9j9": {
                    if (message.getText().toLowerCase().trim().equals("!fish")) {
                        sendChatMessage("BeEp bEep, bloop bl0op " + message.getNick() + "!");
                    }
                    break;
                }
                case "Cryyyy": {
                    if (message.getText().toLowerCase().trim().equals("!fish")) {
                        sendChatMessage("BeEp bEep, bloop bl0op " + message.getNick() + "!");
                    }
                    break;
                }
            }
        }
    }

    public static void shareMesssages() {
        if (!outgoingMessageQueue.isEmpty()) {
            sendChatMessage(outgoingMessageQueue.poll());
        }
    }


    public static void websocketFrameDispatcher() {
        ReadDto readDto = Connection.read();
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
            if (isWebSocketPing) {
                sendPong();
            }
        }
    }

    private static WebSocketFrame getWebSocketFrame(byte[] bytes, OpCode opCode) {
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
