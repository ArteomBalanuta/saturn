package org.saturn.app.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.service.PingService;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Date;
import java.util.concurrent.BlockingQueue;

@Slf4j
public class PingServiceImpl extends OutService implements PingService {
   
    public PingServiceImpl(BlockingQueue<String> outgoingMessageQueue) {
        super(outgoingMessageQueue);
    }
    
    public void executePing(String author) {
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
            
            sc.close();
        } catch (IOException e) {
           log.info("Error: {}", e.getMessage());
           log.error("Stack trace: ", e);
        }

        log.info("response latency: {}", timeToRespond);
        enqueueMessageForSending(author, " response time: " + timeToRespond + " milliseconds", false);
    }
    
    
    
}
