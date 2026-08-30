package org.saturn.app.service.impl;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/* ban pest - 2UvIfa CMDISSB
              XalBBq EntertainmentOne
*/

class OutServiceTest {
  BlockingQueue<String> queue = new ArrayBlockingQueue<>(8);
  BlockingQueue<String> raw = new ArrayBlockingQueue<>(8);
  OutService outService = new OutService(queue, raw);

  @Test
  void enqueueMessageForSending() {
    String expected = "/whisper @author test_text_123";
    String actual = outService.enqueueMessageForSending("author", "test_text_123", true);

    Assertions.assertEquals(expected, actual);
  }

  @Test
  void enqueueMessageForSendingNormalizesLineBreaksToRealNewLines() {
    String actual =
        outService.enqueueMessageForSending("author", "line1\nline2\r\nline3\rline4", true);

    Assertions.assertEquals("/whisper @author line1\nline2\nline3\nline4", actual);
    Assertions.assertEquals(actual, queue.poll());
  }

  @Test
  void enqueueBareMessageNormalizesEscapedNewLines() {
    String actual = outService.enqueueMessageForSending("line1\\nline2");

    Assertions.assertEquals("line1\nline2", actual);
    Assertions.assertEquals(actual, queue.poll());
  }

  @Test
  void enqueueAgentMessageRejectsMissingAuthorAndBlankMessage() {
    Assertions.assertThrows(
        RuntimeException.class, () -> outService.enqueueAgentMessage(null, "answer", false, "id"));
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> outService.enqueueAgentMessage("author", " ", false, "id"));
  }
}
