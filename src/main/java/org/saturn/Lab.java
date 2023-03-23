package org.saturn;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;

public class Lab {
    public static void main(String[] args) throws URISyntaxException {
        URI uri = new URI("wss://hack.chat/chat-ws");
        WebSocketClient client1 = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
//                client.sendPing();
                System.out.println("Handshake Status: " + serverHandshake.getHttpStatus());
//                isConnected = true;
            }
            
            @Override
            public void onMessage(String s) {
                System.out.println("Message: " + s);
//                incomingStringQueue.add(s);
            }
            
            @Override
            public void onClose(int i, String s, boolean b) {
                System.out.println("server closed the connection: " + i + " " + s );
            }
        
            @Override
            public void onError(Exception e) {
                System.out.println("Error");
                e.printStackTrace();
            }
   
        };
       
    }
}
