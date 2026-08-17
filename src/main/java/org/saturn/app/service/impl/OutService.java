package org.saturn.app.service.impl;

import com.google.gson.JsonObject;
import java.util.concurrent.BlockingQueue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

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
    message = formatAddressedMessage(author, message, isWhisper);

    /* TODO: remove all the manual escaping and use `StringEscapeUtils.escapeJava(message)` */
    queue.add(message);
    CommandOutputCapture.recordChat(message);
    return message;
  }

  public String enqueueMessageForSending(String message) {
    message = normalizeForChatPayload(message);
    queue.add(message);
    CommandOutputCapture.recordChat(message);
    return message;
  }

  public void enqueueAgentMessage(
      String author, String message, boolean isWhisper, String customId) {
    message = formatAddressedMessage(author, message, isWhisper);
    if (rawMessages == null) {
      queue.add(message);
      CommandOutputCapture.recordChat(message);
      return;
    }
    JsonObject payload = new JsonObject();
    payload.addProperty("cmd", "chat");
    payload.addProperty("text", message);
    payload.addProperty("customId", customId);
    enqueueRawMessageForSending(payload.toString());
  }

  public void updateAgentMessage(String mode, String message, String customId) {
    if (rawMessages == null) {
      queue.add(normalizeForChatPayload(message));
      CommandOutputCapture.recordChat(message);
      return;
    }
    JsonObject payload = new JsonObject();
    payload.addProperty("cmd", "updateMessage");
    payload.addProperty("mode", mode);
    payload.addProperty("text", normalizeForChatPayload(message));
    payload.addProperty("customId", customId);
    enqueueRawMessageForSending(payload.toString());
  }

  public void enqueueRawMessageForSending(String message) {
    log.debug("raw payload sent: {}", message);
    rawMessages.add(message);
    CommandOutputCapture.recordRaw(message);
  }

  static String normalizeForChatPayload(String message) {
    if (message == null) {
      return null;
    }

    return message.replace("\r\n", "\n").replace("\r", "\n").replace("\\n", "\n");
  }

  private static String formatAddressedMessage(String author, String message, boolean isWhisper) {
    message = normalizeForChatPayload(message);
    if (isWhisper) {
      return StringUtils.prependIfMissingIgnoreCase(message, "/whisper @%s ".formatted(author));
    }
    return "@%s %s".formatted(author, message);
  }
}
