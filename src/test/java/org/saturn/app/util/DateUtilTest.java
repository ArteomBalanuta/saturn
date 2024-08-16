package org.saturn.app.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DateUtilTest {
  @Test
  public void tsToSec8601() {
    Long actual = DateUtil.tsToSec8601("2023-08-27T14:5", "UTC");

    assertEquals(1693145100, actual);
  }

  @Test
  public void formatRfc1123() {
    ZonedDateTime utc = ZonedDateTime.of(LocalDate.EPOCH, LocalTime.MIDNIGHT, ZoneId.of("UTC"));
    long epochSecond = utc.toEpochSecond();
    String actual = DateUtil.formatRfc1123(epochSecond, TimeUnit.SECONDS, "UTC");

    assertEquals("Thu, 1 Jan 1970 00:00:00 GMT", actual);
  }
}
