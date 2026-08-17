package org.saturn.app.agent.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.AgentConfig;

class OpenAiCompatibleClientTest {
  private HttpServer server;
  private ExecutorService serverExecutor;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
    if (serverExecutor != null) {
      serverExecutor.close();
    }
  }

  @Test
  void mapsOpenAiRequestAndToolCallResponseWithoutRequiringModel() throws Exception {
    AtomicReference<String> body = new AtomicReference<>();
    AtomicReference<String> authorization = new AtomicReference<>();
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          respond(
              exchange,
              200,
              """
              {"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[
                {"id":"call-1","type":"function","function":{"name":"room_users","arguments":"{}"}}
              ]},"finish_reason":"tool_calls"}]}
              """);
        });
    server.start();

    OpenAiCompatibleClient client = new OpenAiCompatibleClient(config("key", 0));
    LlmResponse response =
        client.complete(new LlmRequest(List.of(LlmMessage.user("hello")), List.of()));

    var requestJson = JsonParser.parseString(body.get()).getAsJsonObject();
    assertFalse(requestJson.has("model"));
    assertEquals("Bearer key", authorization.get());
    assertEquals(768, requestJson.get("max_tokens").getAsInt());
    assertFalse(
        requestJson.getAsJsonObject("chat_template_kwargs").get("enable_thinking").getAsBoolean());
    assertEquals("room_users", response.toolCalls().getFirst().name());
    assertEquals("call-1", response.toolCalls().getFirst().id());
    assertTrue(response.content().isEmpty());
    assertFalse(requestJson.has("cache_prompt"));
  }

  @Test
  void disablesLlamaPromptCacheForFreshCompletionRequest() throws Exception {
    AtomicReference<String> body = new AtomicReference<>();
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          respond(
              exchange,
              200,
              "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"fresh\"},\"finish_reason\":\"stop\"}]}");
        });
    server.start();
    OpenAiCompatibleClient client = new OpenAiCompatibleClient(config("", 0));

    client.complete(
        LlmRequest.withoutPromptCache(List.of(LlmMessage.user("latest prompt")), List.of()));

    var requestJson = JsonParser.parseString(body.get()).getAsJsonObject();
    assertFalse(requestJson.get("cache_prompt").getAsBoolean());
  }

  @Test
  void retriesTransientFailureButDoesNotRetryClientError() throws Exception {
    AtomicInteger transientCalls = new AtomicInteger();
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          int call = transientCalls.incrementAndGet();
          if (call == 1) {
            respond(exchange, 503, "unavailable");
          } else {
            respond(
                exchange,
                200,
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}");
          }
        });
    server.start();

    OpenAiCompatibleClient retrying = new OpenAiCompatibleClient(config("", 1));
    assertEquals(
        "ok",
        retrying.complete(new LlmRequest(List.of(LlmMessage.user("hi")), List.of())).content());
    assertEquals(2, transientCalls.get());

    server.stop(0);
    AtomicInteger clientErrorCalls = new AtomicInteger();
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          clientErrorCalls.incrementAndGet();
          respond(exchange, 400, "bad request");
        });
    server.start();

    OpenAiCompatibleClient nonRetrying = new OpenAiCompatibleClient(config("", 3));
    assertThrows(
        LlmException.class,
        () -> nonRetrying.complete(new LlmRequest(List.of(LlmMessage.user("hi")), List.of())));
    assertEquals(1, clientErrorCalls.get());
  }

  @Test
  void capsRetryBackoffAtRequestTimeout() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          if (calls.incrementAndGet() == 1) {
            respond(exchange, 503, "unavailable");
          } else {
            respond(
                exchange,
                200,
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}");
          }
        });
    server.start();
    AgentConfig config =
        new AgentConfig(
            true,
            URI.create("http://localhost:" + server.getAddress().getPort()),
            Optional.empty(),
            "",
            Duration.ofMillis(100),
            2,
            4,
            2,
            2,
            8_000,
            8_000,
            10,
            Duration.ofHours(1),
            1,
            Duration.ofDays(1));
    OpenAiCompatibleClient client = new OpenAiCompatibleClient(config);

    LlmResponse response =
        assertTimeoutPreemptively(
            Duration.ofSeconds(1),
            () -> client.complete(new LlmRequest(List.of(LlmMessage.user("hi")), List.of())));

    assertEquals("ok", response.content());
    assertEquals(2, calls.get());
  }

  @Test
  void doesNotRetryTimedOutGeneration() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    server = HttpServer.create(new InetSocketAddress(0), 0);
    serverExecutor = Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(serverExecutor);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          calls.incrementAndGet();
          try {
            Thread.sleep(500);
            respond(
                exchange,
                200,
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"late\"}}]}");
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
          } catch (IOException ignored) {
            // The client is expected to close the exchange when its deadline expires.
          }
        });
    server.start();
    AgentConfig config =
        new AgentConfig(
            true,
            URI.create("http://localhost:" + server.getAddress().getPort()),
            Optional.empty(),
            "",
            Duration.ofMillis(100),
            2,
            4,
            2,
            2,
            8_000,
            8_000,
            10,
            Duration.ofHours(1),
            3,
            Duration.ofMillis(1));
    OpenAiCompatibleClient client = new OpenAiCompatibleClient(config);

    LlmException exception =
        assertThrows(
            LlmException.class,
            () -> client.complete(new LlmRequest(List.of(LlmMessage.user("hi")), List.of())));

    assertEquals(1, calls.get());
    assertTrue(exception.getCause() instanceof HttpTimeoutException);
    assertTrue(exception.getMessage().contains("timed out"));
  }

  @Test
  void rejectsMalformedSuccessfulResponse() throws Exception {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/v1/chat/completions", exchange -> respond(exchange, 200, "{\"choices\":[]}"));
    server.start();

    OpenAiCompatibleClient client = new OpenAiCompatibleClient(config("", 0));

    assertThrows(
        LlmException.class,
        () -> client.complete(new LlmRequest(List.of(LlmMessage.user("hi")), List.of())));
  }

  @Test
  void normalizesNullContentAndMissingFinishReason() throws Exception {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/v1/chat/completions",
        exchange -> respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":null}}]}"));
    server.start();
    OpenAiCompatibleClient client = new OpenAiCompatibleClient(config("", 0));

    LlmResponse response =
        client.complete(new LlmRequest(List.of(LlmMessage.user("hi")), List.of()));

    assertEquals("", response.content());
    assertEquals("", response.finishReason());
  }

  @Test
  void reportsExhaustedTransientHttpFailuresWithoutRetrying() throws Exception {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/v1/chat/completions", exchange -> respond(exchange, 503, "unavailable"));
    server.start();
    OpenAiCompatibleClient client = new OpenAiCompatibleClient(config("", 0));

    LlmException exception =
        assertThrows(
            LlmException.class,
            () -> client.complete(new LlmRequest(List.of(LlmMessage.user("hi")), List.of())));

    assertEquals("LLM endpoint returned HTTP 503", exception.getMessage());
    assertTrue(exception.getCause() == null);
  }

  @Test
  void retriesRateLimitResponsesBeforeParsingTheSuccessfulResponse() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          if (calls.incrementAndGet() == 1) {
            respond(exchange, 429, "rate limited");
          } else {
            respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");
          }
        });
    server.start();

    OpenAiCompatibleClient client = new OpenAiCompatibleClient(config("", 1));

    assertEquals(
        "ok", client.complete(new LlmRequest(List.of(LlmMessage.user("hi")), List.of())).content());
    assertEquals(2, calls.get());
  }

  @Test
  void translatesInterruptedTransportRequestsAndRestoresInterruptStatus() throws Exception {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/v1/chat/completions", exchange -> respond(exchange, 200, "{}"));
    server.start();
    OpenAiCompatibleClient client = new OpenAiCompatibleClient(config("", 0));
    Thread.currentThread().interrupt();

    try {
      LlmException exception =
          assertThrows(
              LlmException.class,
              () -> client.complete(new LlmRequest(List.of(LlmMessage.user("hi")), List.of())));
      assertEquals("LLM endpoint request interrupted", exception.getMessage());
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void serializesToolsAndToolMessagesWithOptionalFields() throws Exception {
    AtomicReference<String> body = new AtomicReference<>();
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");
        });
    server.start();
    OpenAiCompatibleClient client = new OpenAiCompatibleClient(config("", 0));
    LlmToolCall call = new LlmToolCall("call-1", "echo", "{}");
    JsonObject tool = new JsonObject();
    tool.addProperty("type", "function");
    tool.add("function", JsonParser.parseString("{\"name\":\"echo\"}"));

    client.complete(
        new LlmRequest(
            List.of(LlmMessage.assistant(null, List.of(call)), LlmMessage.tool("call-1", "result")),
            List.of(tool)));

    var request = JsonParser.parseString(body.get()).getAsJsonObject();
    assertTrue(request.has("tools"));
    assertEquals("auto", request.get("tool_choice").getAsString());
    assertEquals(
        "call-1",
        request
            .getAsJsonArray("messages")
            .get(0)
            .getAsJsonObject()
            .getAsJsonArray("tool_calls")
            .get(0)
            .getAsJsonObject()
            .get("id")
            .getAsString());
    assertEquals(
        "call-1",
        request
            .getAsJsonArray("messages")
            .get(1)
            .getAsJsonObject()
            .get("tool_call_id")
            .getAsString());
  }

  private AgentConfig config(String apiKey, int retries) {
    return new AgentConfig(
        true,
        URI.create("http://localhost:" + server.getAddress().getPort()),
        Optional.empty(),
        apiKey,
        Duration.ofSeconds(2),
        2,
        4,
        2,
        2,
        8_000,
        8_000,
        10,
        Duration.ofHours(1),
        retries,
        Duration.ofMillis(1));
  }

  private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
