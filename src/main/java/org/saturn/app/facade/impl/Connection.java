package org.saturn.app.facade.impl;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.ThreadContext;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.saturn.app.facade.EngineType;
import org.saturn.app.listener.Listener;

@Slf4j
public class Connection {
  private final WebSocketClient client;
  private boolean isError;

  public Connection(
      String address,
      List<Listener> listeners,
      org.saturn.app.model.dto.Proxy proxy,
      EngineImpl engine)
      throws URISyntaxException {
    URI uri = new URI(address);
    client =
        new WebSocketClient(uri) {
          @Override
          public void onOpen(ServerHandshake serverHandshake) {
            if (engine.engineType.equals(EngineType.REPLICA)) {
              log.warn("HC Connection threadId: {}", Thread.currentThread().threadId());
              if (ThreadContext.get("instanceType") != null) {
                log.warn(
                    "instanceType is not null for REPLICA: {}, threadId: {}",
                    engine.channel,
                    Thread.currentThread().threadId());
              } else {
                ThreadContext.put("instanceType", "REPLICA:" + engine.channel);
                log.warn(
                    "set instanceType for REPLICA: {}, threadId: {}",
                    engine.channel,
                    Thread.currentThread().threadId());
              }
            } else {
              if (ThreadContext.get("instanceType") != null) {
                log.warn(
                    "instanceType is not null for HOST: {}, threadId: {}",
                    engine.channel,
                    Thread.currentThread().threadId());
              } else {
                ThreadContext.put("instanceType", "HOST:" + engine.channel);
                log.warn(
                    "set instanceType for HOST: {}, threadId: {}",
                    engine.channel,
                    Thread.currentThread().threadId());
              }
            }

            client.sendPing();
            log.debug("Handshake Status: {}", serverHandshake.getHttpStatus());
            listeners.stream()
                .filter(listener -> "connectionListener".equals(listener.getListenerName()))
                .forEach(listener -> listener.notify("connected"));
          }

          @Override
          public void onMessage(String s) {
            listeners.stream()
                .filter(listener -> "incomingMessageListener".equals(listener.getListenerName()))
                .forEach(listener -> listener.notify(s));
          }

          @Override
          public void onClose(int i, String s, boolean b) {
            log.warn("Server closed the connection: {} {}", i, s);
          }

          @Override
          public void onError(Exception e) {
            log.error("Exception:", e);
            isError = true;
            throw new RuntimeException(e);
          }
        };

    if (proxy != null) {
      Proxy p =
          new Proxy(
              Proxy.Type.SOCKS,
              new InetSocketAddress(proxy.getIp(), Integer.parseInt(proxy.getPort())));

      // Create a trust manager that does not validate certificate chains
      TrustManager[] trustAllCerts =
          new TrustManager[] {
            new X509TrustManager() {
              public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
              }

              public void checkClientTrusted(X509Certificate[] certs, String authType) {}

              public void checkServerTrusted(X509Certificate[] certs, String authType) {}
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

  public void write(String message) {
    client.send(message);
  }

  public void close() throws InterruptedException {
    client.close();
  }

  public void start() throws InterruptedException {
    client.connectBlocking();
  }

  public void startNonBlocking() throws InterruptedException {
    client.connect();
  }

  public boolean isConnected() {
    return (client.isOpen() && !client.isClosing() && !client.isFlushAndClose()) && !isError;
  }
}
