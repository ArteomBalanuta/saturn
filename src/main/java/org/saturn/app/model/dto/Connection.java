package org.saturn.app.model.dto;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.saturn.app.service.Listener;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public class Connection {
    private final WebSocketClient client;

    public Connection(String address, List<Listener> listeners) throws URISyntaxException {
        URI uri = new URI(address);
        client = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                client.sendPing();
                System.out.println("Handshake Status: " + serverHandshake.getHttpStatus());
                listeners.stream().filter(listener -> "connectionListener".equals(listener.getListenerName()))
                        .forEach(listener -> listener.notify("connected"));
            }
            
            @Override
            public void onMessage(String s) {
                System.out.println("Message: " + s);
                listeners.stream().filter(listener -> "incomingMessageListener".equals(listener.getListenerName()))
                        .forEach(listener -> listener.notify(s));
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
}
