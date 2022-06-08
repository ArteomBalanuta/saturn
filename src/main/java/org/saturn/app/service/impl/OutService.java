package org.saturn.app.service.impl;

import java.util.concurrent.BlockingQueue;

import static org.saturn.app.util.Util.getTimestampNow;

public class OutService {
    protected BlockingQueue<String> queue;
    
    public OutService(BlockingQueue<String> queue) {
        this.queue = queue;
    }
    
    public void enqueueMessageForSending(String message) {
        System.out.println(getTimestampNow() + " sent: " + message);
        queue.add(message);
    }
}
