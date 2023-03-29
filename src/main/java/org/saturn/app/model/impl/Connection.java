package org.saturn.app.model.impl;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;

public class Connection {
    private final String uri;
    private boolean isConnected;
    
    private final WebSocketClient client;
    
    public Connection(String address, BlockingQueue<String> incomingStringQueue) throws URISyntaxException {
        this.uri = address;
        
        URI uri = new URI(address);
        client = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                client.sendPing();
                System.out.println("Handshake Status: " + serverHandshake.getHttpStatus());
                isConnected = true;
            }
            
            @Override
            public void onMessage(String s) {
                System.out.println("Message: " + s);
                incomingStringQueue.add(s);
            }
            
            @Override
            public void onClose(int i, String s, boolean b) {
                System.out.println("server closed the connection: " + i + " " + s);
            }
            
            @Override
            public void onError(Exception e) {
                System.out.println("Error");
                e.printStackTrace();
            }
        };
        
        client.connect();
    }
    
    public void write(String message)  {
        client.send(message);
    }
    
    public void close() {
        client.close();
    }
    
    public boolean isConnected() {
        return isConnected;
    }
}
