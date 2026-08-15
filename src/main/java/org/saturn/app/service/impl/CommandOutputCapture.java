package org.saturn.app.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Captures output emitted synchronously by one command without consuming its delivery queue. */
public final class CommandOutputCapture {
  private static final ThreadLocal<Output> CURRENT = new ThreadLocal<>();

  private CommandOutputCapture() {}

  public static <T> Captured<T> capture(Supplier<T> operation) {
    Output previous = CURRENT.get();
    Output current = new Output();
    CURRENT.set(current);
    try {
      T value = operation.get();
      return new Captured<>(value, List.copyOf(current.chatMessages), List.copyOf(current.rawMessages));
    } finally {
      CURRENT.set(previous);
    }
  }

  static void recordChat(String message) {
    Output current = CURRENT.get();
    if (current != null) {
      current.chatMessages.add(message);
    }
  }

  static void recordRaw(String message) {
    Output current = CURRENT.get();
    if (current != null) {
      current.rawMessages.add(message);
    }
  }

  public record Captured<T>(T value, List<String> chatMessages, List<String> rawMessages) {}

  private static final class Output {
    private final List<String> chatMessages = new ArrayList<>();
    private final List<String> rawMessages = new ArrayList<>();
  }
}
