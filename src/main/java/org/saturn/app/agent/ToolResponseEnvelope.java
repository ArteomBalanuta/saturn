package org.saturn.app.agent;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;

/** Stable model-visible envelope for every successful or failed tool observation. */
public record ToolResponseEnvelope(String status, JsonElement data, Error error) {
  private static final Gson GSON = new Gson();

  public ToolResponseEnvelope {
    if (!"success".equals(status) && !"error".equals(status)) {
      throw new IllegalArgumentException("status must be success or error");
    }
    data = data == null ? JsonNull.INSTANCE : data.deepCopy();
    if ("success".equals(status) && error != null) {
      throw new IllegalArgumentException("successful envelope cannot contain an error");
    }
    if ("error".equals(status)) {
      if (error == null) {
        throw new IllegalArgumentException("error envelope must contain an error");
      }
      if (error.code() == null || error.code().isBlank()) {
        throw new IllegalArgumentException("error code must not be blank");
      }
      if (error.message() == null || error.message().isBlank()) {
        throw new IllegalArgumentException("error message must not be blank");
      }
    }
  }

  public static ToolResponseEnvelope success(String content) {
    return new ToolResponseEnvelope("success", parse(content), null);
  }

  public static ToolResponseEnvelope error(String code, String message) {
    return new ToolResponseEnvelope("error", JsonNull.INSTANCE, new Error(code, message));
  }

  public String toJson() {
    return GSON.toJson(this);
  }

  @Override
  public JsonElement data() {
    return data.deepCopy();
  }

  private static JsonElement parse(String content) {
    if (content == null) {
      return JsonNull.INSTANCE;
    }
    try {
      return JsonParser.parseString(content);
    } catch (RuntimeException ignored) {
      return new com.google.gson.JsonPrimitive(content);
    }
  }

  public record Error(String code, String message) {}
}
