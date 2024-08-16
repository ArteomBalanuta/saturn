package org.saturn.app.service.impl;

import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.BlockingQueue;

import static org.saturn.app.util.DateUtil.getTimestampNow;

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

    public String enqueueMessageForSending(String author, String message, boolean isWhisper) {
        if (isWhisper) {
            if (!StringUtils.isBlank(author)) {
                message = StringUtils.prependIfMissingIgnoreCase(message, "/whisper @" + author + " ");
            } else {
                /* should not happen */
            }
        } else {
            if (!StringUtils.isBlank(author)) {
                message = "@" + author + " " + message;
            }
        }
        queue.add(message);
        System.out.println(getTimestampNow() + " sent: " + message);
        return message;
    }

    public void enqueueRawMessageForSending(String message) {
        System.out.println(getTimestampNow() + " raw sent: " + message);
        rawMessages.add(message);
    }
}
