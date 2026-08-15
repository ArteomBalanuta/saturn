package org.saturn.app.agent;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/** Loads agent-facing copy from classpath resources and keeps rendering out of orchestration code. */
public final class AgentPromptCatalog {
  private static final String ROOT = "/agent/";
  private final Gson gson;
  private final JsonObject toolCopy;

  public AgentPromptCatalog() {
    this(new Gson());
  }

  AgentPromptCatalog(Gson gson) {
    this.gson = Objects.requireNonNull(gson, "gson");
    this.toolCopy = loadJson("tool-copy.json");
  }

  public String text(String resource) {
    try (InputStream stream = resource(resource)) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw failure(resource, exception);
    }
  }

  public String formatted(String resource, Object... arguments) {
    return text(resource).stripTrailing().formatted(arguments);
  }

  public String toolDescription(String toolName) {
    return tool(toolName).get("description").getAsString();
  }

  public List<String> toolGuidance(String toolName, String field) {
    return tool(toolName).getAsJsonArray(field).asList().stream()
        .map(element -> element.getAsString())
        .toList();
  }

  public String toolExample(String toolName) {
    return tool(toolName).get("example").getAsString();
  }

  private JsonObject tool(String toolName) {
    if (!toolCopy.has(toolName) || !toolCopy.get(toolName).isJsonObject()) {
      throw new IllegalArgumentException("Missing agent tool copy: " + toolName);
    }
    return toolCopy.getAsJsonObject(toolName);
  }

  private JsonObject loadJson(String resource) {
    try (Reader reader = new InputStreamReader(resource(resource), StandardCharsets.UTF_8)) {
      return gson.fromJson(reader, JsonObject.class);
    } catch (IOException | RuntimeException exception) {
      throw failure(resource, exception);
    }
  }

  private InputStream resource(String resource) {
    InputStream stream = AgentPromptCatalog.class.getResourceAsStream(ROOT + resource);
    if (stream == null) {
      throw new IllegalStateException("Missing agent prompt resource: " + ROOT + resource);
    }
    return stream;
  }

  private IllegalStateException failure(String resource, Exception exception) {
    return new IllegalStateException("Cannot load agent prompt resource: " + ROOT + resource, exception);
  }
}
