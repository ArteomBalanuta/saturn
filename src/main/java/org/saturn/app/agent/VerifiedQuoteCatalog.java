package org.saturn.app.agent;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Exact, locally verified quotation triples used at the response-delivery boundary. */
final class VerifiedQuoteCatalog {
  private static final String RESOURCE = "/agent/verified-quotes.json";
  private final List<Entry> entries;

  VerifiedQuoteCatalog() {
    this.entries = load();
  }

  Optional<Entry> find(String line) {
    if (line == null) {
      return Optional.empty();
    }
    String normalized = line.strip();
    return entries.stream().filter(entry -> entry.line().equals(normalized)).findFirst();
  }

  Entry fallback() {
    return entries.getFirst();
  }

  String promptEntries() {
    return entries.stream()
        .map(entry -> entry.id() + ": " + entry.line())
        .reduce((left, right) -> left + "\n" + right)
        .orElseThrow();
  }

  private static List<Entry> load() {
    try (InputStream stream = VerifiedQuoteCatalog.class.getResourceAsStream(RESOURCE)) {
      if (stream == null) {
        throw new IllegalStateException("Missing verified quote catalog: " + RESOURCE);
      }
      try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        List<Entry> loaded = new Gson().fromJson(reader, new TypeToken<List<Entry>>() {}.getType());
        validate(loaded);
        return List.copyOf(loaded);
      }
    } catch (IOException | RuntimeException exception) {
      if (exception instanceof IllegalStateException) {
        throw (IllegalStateException) exception;
      }
      throw new IllegalStateException("Cannot load verified quote catalog: " + RESOURCE, exception);
    }
  }

  private static void validate(List<Entry> entries) {
    if (entries == null || entries.isEmpty()) {
      throw new IllegalStateException("Verified quote catalog must not be empty");
    }
    Set<String> ids = new HashSet<>();
    Set<String> lines = new HashSet<>();
    for (Entry entry : entries) {
      if (entry == null
          || isBlank(entry.id())
          || isBlank(entry.quote())
          || isBlank(entry.book())
          || isBlank(entry.author())
          || isBlank(entry.reference())) {
        throw new IllegalStateException("Verified quote catalog contains an incomplete entry");
      }
      if (!ids.add(entry.id()) || !lines.add(entry.line())) {
        throw new IllegalStateException("Verified quote catalog contains duplicate entries");
      }
      if (!AgentResponseCorrector.isQuoteOnly(entry.line())) {
        throw new IllegalStateException("Verified quote catalog contains an invalid quote line");
      }
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  record Entry(String id, String quote, String book, String author, String reference) {
    Entry {
      Objects.requireNonNull(id);
      Objects.requireNonNull(quote);
      Objects.requireNonNull(book);
      Objects.requireNonNull(author);
      Objects.requireNonNull(reference);
    }

    String line() {
      return "\"" + quote + "\" — " + book + ", " + author;
    }
  }
}
