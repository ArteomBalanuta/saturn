package org.saturn.app.agent.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import org.saturn.app.agent.AgentConfig;

public final class OpenAiCompatibleClient implements LlmClient {
  private final AgentConfig config;
  private final Gson gson;
  private final HttpClient httpClient;

  public OpenAiCompatibleClient(AgentConfig config) {
    this(config, new Gson(), HttpClient.newBuilder().connectTimeout(config.timeout()).build());
  }

  OpenAiCompatibleClient(AgentConfig config, Gson gson, HttpClient httpClient) {
    this.config = config;
    this.gson = gson;
    this.httpClient = httpClient;
  }

  @Override
  public LlmResponse complete(LlmRequest request) throws LlmException {
    JsonObject payload = toJson(request);
    for (int attempt = 0; ; attempt++) {
      try {
        HttpResponse<String> response =
            httpClient.send(buildRequest(payload), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 == 2) {
          return parse(response.body());
        }
        if (!isTransient(response.statusCode()) || attempt >= config.maxRetries()) {
          throw new LlmException("LLM endpoint returned HTTP " + response.statusCode());
        }
      } catch (HttpTimeoutException exception) {
        throw new LlmException(
            "LLM endpoint timed out after " + config.timeout().toMillis() + " ms", exception);
      } catch (IOException exception) {
        if (attempt >= config.maxRetries()) {
          throw new LlmException("LLM endpoint request failed", exception);
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new LlmException("LLM endpoint request interrupted", exception);
      }
      backoff(attempt);
    }
  }

  private HttpRequest buildRequest(JsonObject payload) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(completionUri())
            .timeout(config.timeout())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)));
    if (!config.apiKey().isBlank()) {
      builder.header("Authorization", "Bearer " + config.apiKey());
    }
    return builder.build();
  }

  private JsonObject toJson(LlmRequest request) {
    JsonObject payload = new JsonObject();
    config.model().ifPresent(model -> payload.addProperty("model", model));
    payload.addProperty("stream", false);
    payload.addProperty("max_tokens", config.maxCompletionTokens());
    JsonObject templateArguments = new JsonObject();
    templateArguments.addProperty("enable_thinking", config.thinkingEnabled());
    payload.add("chat_template_kwargs", templateArguments);
    JsonArray messages = new JsonArray();
    request.messages().forEach(message -> messages.add(toJson(message)));
    payload.add("messages", messages);
    if (!request.tools().isEmpty()) {
      JsonArray tools = new JsonArray();
      request.tools().forEach(tools::add);
      payload.add("tools", tools);
      payload.addProperty("tool_choice", "auto");
    }
    return payload;
  }

  private JsonObject toJson(LlmMessage message) {
    JsonObject json = new JsonObject();
    json.addProperty("role", message.role());
    if (message.content() == null) {
      json.add("content", com.google.gson.JsonNull.INSTANCE);
    } else {
      json.addProperty("content", message.content());
    }
    if (!message.toolCalls().isEmpty()) {
      JsonArray calls = new JsonArray();
      message.toolCalls().forEach(call -> calls.add(toJson(call)));
      json.add("tool_calls", calls);
    }
    if (message.toolCallId() != null) {
      json.addProperty("tool_call_id", message.toolCallId());
    }
    return json;
  }

  private JsonObject toJson(LlmToolCall call) {
    JsonObject function = new JsonObject();
    function.addProperty("name", call.name());
    function.addProperty("arguments", call.arguments());
    JsonObject json = new JsonObject();
    json.addProperty("id", call.id());
    json.addProperty("type", "function");
    json.add("function", function);
    return json;
  }

  private LlmResponse parse(String body) throws LlmException {
    try {
      JsonObject root = gson.fromJson(body, JsonObject.class);
      JsonObject choice = root.getAsJsonArray("choices").get(0).getAsJsonObject();
      JsonObject message = choice.getAsJsonObject("message");
      JsonElement content = message.get("content");
      String text = content == null || content.isJsonNull() ? "" : content.getAsString();
      List<LlmToolCall> calls = new ArrayList<>();
      if (message.has("tool_calls")) {
        for (JsonElement element : message.getAsJsonArray("tool_calls")) {
          JsonObject call = element.getAsJsonObject();
          JsonObject function = call.getAsJsonObject("function");
          calls.add(
              new LlmToolCall(
                  call.get("id").getAsString(),
                  function.get("name").getAsString(),
                  function.get("arguments").getAsString()));
        }
      }
      JsonElement finishReason = choice.get("finish_reason");
      return new LlmResponse(
          text,
          calls,
          finishReason == null || finishReason.isJsonNull() ? "" : finishReason.getAsString());
    } catch (JsonParseException
        | IllegalStateException
        | NullPointerException
        | IndexOutOfBoundsException exception) {
      throw new LlmException("Malformed OpenAI-compatible response", exception);
    }
  }

  private URI completionUri() {
    return URI.create(config.endpoint() + "/v1/chat/completions");
  }

  private static boolean isTransient(int statusCode) {
    return statusCode == 429 || statusCode >= 500;
  }

  private void backoff(int attempt) throws LlmException {
    long multiplier = 1L << Math.min(attempt, 20);
    long millis;
    try {
      millis = Math.multiplyExact(config.retryBackoff().toMillis(), multiplier);
    } catch (ArithmeticException exception) {
      millis = Long.MAX_VALUE;
    }
    millis = Math.min(millis, Math.max(1L, config.timeout().toMillis()));
    try {
      Thread.sleep(millis);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new LlmException("LLM retry interrupted", exception);
    }
  }
}
