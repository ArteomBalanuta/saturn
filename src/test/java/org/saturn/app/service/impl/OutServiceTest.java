package org.saturn.app.service.impl;

import static org.mockito.Mockito.mock;

import java.util.concurrent.BlockingQueue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/* ban pest - 2UvIfa CMDISSB
              XalBBq EntertainmentOne
*/

class OutServiceTest {
  BlockingQueue<String> queue = mock(BlockingQueue.class);
  BlockingQueue<String> raw = mock(BlockingQueue.class);
  OutService outService = new OutService(queue, raw);

  @Test
  void enqueueMessageForSending() {
    String expected = "/whisper @author test_text_123";
    String actual = outService.enqueueMessageForSending("author", "test_text_123", true);

    Assertions.assertEquals(expected, actual);
  }
}
