package org.saturn.app.facade;

import com.google.gson.Gson;
import org.apache.commons.configuration2.Configuration;
import org.saturn.app.model.WebSocketFrame;
import org.saturn.app.model.impl.ChatMessage;
import org.saturn.app.model.impl.Connection;
import org.saturn.app.model.impl.ReadDto;
import org.saturn.app.model.impl.User;
import org.saturn.app.model.impl.WebSocketExtendedFrameImpl;
import org.saturn.app.model.impl.WebSocketStandardFrameImpl;
import org.saturn.app.util.OpCode;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.Executors.newScheduledThreadPool;
import static org.saturn.app.model.WebSocketFrame.maskingKey;
import static org.saturn.app.util.Constants.CHAT_JSON;
import static org.saturn.app.util.Constants.STANDARD_FRAME_MAX_TEXT_PAYLOAD_SIZE;
import static org.saturn.app.util.Constants.THREAD_NUMBER;
import static org.saturn.app.util.OpCode.TEXT;
import static org.saturn.app.util.OpCode.TEXT_EXTENDED;
import static org.saturn.app.util.Util.getTimestampNow;

public abstract class Base {
    protected final Gson gson = new Gson();
    
    protected Connection hcConnection;
    
    public String prefix;
    public String channel;
    public String nick;
    public String trip;
    public String userTrips;
    public String adminTrips;
    
    public boolean isMainThread;
    public int joinDelay;
    public volatile long lastPingTimestamp = getTimestampNow();
    
    protected final BlockingQueue<WebSocketFrame> incomingFramesQueue = new ArrayBlockingQueue<>(256);
    protected final BlockingQueue<ChatMessage> incomingChatMessageQueue = new ArrayBlockingQueue<>(256);
    protected final BlockingQueue<String> outgoingMessageQueue = new ArrayBlockingQueue<>(256);
    
    protected CopyOnWriteArrayList<String> incomingSetOnlineMessageQueue = new CopyOnWriteArrayList<>();
    protected CopyOnWriteArrayList<User> currentChannelUsers = new CopyOnWriteArrayList<>();
    
    protected ExecutorService appExecutor = Executors.newFixedThreadPool(THREAD_NUMBER);
    protected ScheduledExecutorService executorScheduler = newScheduledThreadPool(THREAD_NUMBER);
    
    public Base(java.sql.Connection dbConnection, Configuration config) {
        if (config != null) {
            this.prefix = config.getString("cmdPrefix");
            this.channel = config.getString("channel");
            this.nick = config.getString("nick");
            this.trip = config.getString("trip");
            this.userTrips = config.getString("userTrips");
            this.adminTrips = config.getString("adminTrips");
        }
        
        initExecutors();
    }
    
    void initExecutors(){
        appExecutor = Executors.newFixedThreadPool(THREAD_NUMBER);
        executorScheduler = newScheduledThreadPool(THREAD_NUMBER);
    }
    
    
    public void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
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
                        lastPingTimestamp = getTimestampNow();
                        sendPong(hcConnection);
                    }
                }
            } else {
                System.out.println("wtf");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void sendPong(Connection connection) {
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
    
    
    public void sendChatMessage(String message) {
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
    
    public List<String> getIncomingSetOnlineMessageQueue() {
        return incomingSetOnlineMessageQueue;
    }
    
}
