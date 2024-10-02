package org.saturn.app.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import java.util.concurrent.BlockingQueue;

@Slf4j
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
        if (StringUtils.isBlank(author)) {
            log.error("Author should not be blank!");
            throw new RuntimeException("Author should not be blank!");
        }
        if (isWhisper) {
            message = StringUtils.prependIfMissingIgnoreCase(message, "/whisper @" + author + " ");
        } else {
            message = "@" + author + " " + message;
        }

        /* TODO: remove all the manual escaping and use `StringEscapeUtils.escapeJava(message)` */
        queue.add(message);
        return message;
    }

    public String enqueueMessageForSending(String message) {
        queue.add(message);
        return message;
    }

    public void enqueueRawMessageForSending(String message) {
        log.debug("raw payload sent: {}", message);
        rawMessages.add(message);
    }
}
