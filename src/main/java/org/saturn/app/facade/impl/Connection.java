package org.saturn.app.facade.impl;

import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.saturn.app.listener.Listener;

import javax.net.ssl.*;
import java.net.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.List;

@Slf4j
public class Connection {
    private final WebSocketClient client;

    public Connection(String address, List<Listener> listeners, org.saturn.app.model.dto.Proxy proxy, EngineImpl engine) throws URISyntaxException {
        URI uri = new URI(address);
        client = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                client.sendPing();
                log.debug("Handshake Status: " + serverHandshake.getHttpStatus());
                listeners.stream().filter(listener -> "connectionListener".equals(listener.getListenerName()))
                        .forEach(listener -> listener.notify("connected"));
            }
            
            @Override
            public void onMessage(String s) {
                listeners.stream().filter(listener -> "incomingMessageListener".equals(listener.getListenerName()))
                        .forEach(listener -> listener.notify(s));
            }

            @Override
            public void onClose(int i, String s, boolean b) {
                log.warn("Server closed the connection: " + i + " " + s);
            }
            
            @Override
            public void onError(Exception e) {
                log.error("Exception:", e);
                throw new RuntimeException(e);
            }
        };

        if (proxy != null) {
            Proxy p = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(proxy.getIp(), Integer.parseInt(proxy.getPort())));

            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }
            };

            // Install the all-trusting trust manager
            try {
                SSLContext sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
                SSLSocketFactory sf = sslContext.getSocketFactory();

                client.setSocketFactory(sf);
                client.setProxy(p);
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    public void write(String message)  {
        client.send(message);
    }
    
    public void close() {
        client.close();
    }

    public void start() throws InterruptedException {
        client.connectBlocking();
    }

    public void startNonBlocking() throws InterruptedException {
        client.connect();
    }

    public boolean isConnected() {
        return client.isOpen();
    }
}
