package org.saturn.app.util;

import org.apache.commons.text.StringEscapeUtils;

public final class JsonPayloads {
  private JsonPayloads() {}

  public static String command(String cmd) {
    return "{ \"cmd\": \"%s\"}".formatted(escape(cmd));
  }

  public static String command(String cmd, String key, String value) {
    return "{ \"cmd\": \"%s\", \"%s\": \"%s\"}".formatted(escape(cmd), escape(key), escape(value));
  }

  public static String command(
      String cmd, String firstKey, String firstValue, String secondKey, String secondValue) {
    return "{ \"cmd\": \"%s\", \"%s\": \"%s\", \"%s\":\"%s\"}"
        .formatted(
            escape(cmd),
            escape(firstKey),
            escape(firstValue),
            escape(secondKey),
            escape(secondValue));
  }

  private static String escape(String value) {
    return StringEscapeUtils.escapeJson(value);
  }
}
