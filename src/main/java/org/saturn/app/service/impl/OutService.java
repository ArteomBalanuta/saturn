package org.saturn.app.service.impl;

import java.util.concurrent.BlockingQueue;

import static org.saturn.app.util.Util.getTimestampNow;

public class OutService {
    protected BlockingQueue<String> queue;
    protected BlockingQueue<String> rawMessages;
    public OutService(BlockingQueue<String> queue) {
        this.queue = queue;
    }

    public OutService(BlockingQueue<String> queue, BlockingQueue<String> raw) {
        this.queue = queue;
        this.rawMessages = raw;
    }
    
    public void enqueueMessageForSending(String message) {
        System.out.println(getTimestampNow() + " sent: " + message);
        queue.add(message);
    }

    public void enqueueRawMessageForSending(String message) {
        System.out.println(getTimestampNow() + " raw sent: " + message);
        rawMessages.add(message);
    }
}
