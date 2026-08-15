package org.saturn.app.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.agent.llm.LlmClient;
import org.saturn.app.agent.llm.LlmException;
import org.saturn.app.agent.llm.LlmMessage;
import org.saturn.app.agent.llm.LlmRequest;
import org.saturn.app.agent.llm.LlmResponse;

@Slf4j
public final class DefaultAgentRouter implements AgentRouter {
  private static final String FINALIZE_PROMPT =
      "Answer the user's request using the tool results already provided. Do not call tools.";

  private final AgentConfig config;
  private final LlmClient client;
  private final AgentToolRegistry registry;
  private final AgentMemoryStore memory;
  private final AgentParticipationConfig participationConfig;
  private final AgentConversationContextProvider conversationContextProvider;
  private final AgentSystemPrompt systemPrompt;
  private final ReentrantLock[] sessionLocks = sessionLocks();

  public DefaultAgentRouter(
      AgentConfig config, LlmClient client, AgentToolRegistry registry, AgentMemoryStore memory) {
    this(
        config,
        client,
        registry,
        memory,
        AgentParticipationConfig.from(null),
        AgentConversationContextProvider.none());
  }

  public DefaultAgentRouter(
      AgentConfig config,
      LlmClient client,
      AgentToolRegistry registry,
      AgentMemoryStore memory,
      AgentParticipationConfig participationConfig,
      AgentConversationContextProvider conversationContextProvider) {
    this.config = config;
    this.client = client;
    this.registry = registry;
    this.memory = memory;
    this.participationConfig = participationConfig;
    this.conversationContextProvider = conversationContextProvider;
    this.systemPrompt = new AgentSystemPrompt(participationConfig);
  }

  @Override
  public AgentResult route(AgentInvocation invocation) throws AgentRoutingException {
    if (codePointCount(invocation.prompt()) > config.maxPromptChars()) {
      throw new AgentRoutingException("Prompt exceeds configured limit");
    }

    ReentrantLock sessionLock =
        sessionLocks[
            Math.floorMod(invocation.context().memoryKey().hashCode(), sessionLocks.length)];
    sessionLock.lock();
    try {
      return routeInSession(invocation);
    } finally {
      sessionLock.unlock();
    }
  }

  private AgentResult routeInSession(AgentInvocation invocation) throws AgentRoutingException {
    String correlationId = invocation.requestId();
    AgentContext context = invocation.context();
    List<LlmMessage> history = loadMemory(context, correlationId);
    String recentRoomContext = loadConversationContext(invocation, correlationId);
    String contextualizedPrompt = contextualizePrompt(context, invocation.prompt());
    List<LlmMessage> messages = new ArrayList<>();
    messages.add(
        LlmMessage.system(systemPrompt.render(invocation, correlationId, recentRoomContext)));
    messages.addAll(history);
    messages.add(LlmMessage.user(contextualizedPrompt));

    List<JsonObject> definitions = definitions(context);
    AgentToolExecutor toolExecutor = new AgentToolExecutor(registry, config);
    try {
      LlmResponse response = client.complete(new LlmRequest(messages, definitions));
      int totalCalls = 0;
      while (!response.toolCalls().isEmpty()) {
        if (totalCalls + response.toolCalls().size() > config.maxToolCalls()) {
          response = finalizeResponse(messages);
          break;
        }

        totalCalls += response.toolCalls().size();
        messages.add(LlmMessage.assistant(response.content(), response.toolCalls()));
        boolean allErrors = true;
        for (var call : response.toolCalls()) {
          AgentToolResult result = toolExecutor.execute(context, call);
          log.info(
              "Agent tool completed, correlationId={}, tool={}, outcome={}",
              correlationId,
              call.name(),
              result.isError() ? "error" : "success");
          allErrors &= result.isError();
          messages.add(LlmMessage.tool(call.id(), result.content()));
        }
        if (allErrors) {
          response = finalizeResponse(messages);
          break;
        }
        response = client.complete(new LlmRequest(messages, definitions));
      }

      String content = truncate(response.content(), config.maxOutputChars());
      if (content.isBlank()) {
        throw new AgentRoutingException("Agent returned an empty response");
      }
      if (content.strip().equals(participationConfig.noReplyMarker())) {
        if (!invocation.mode().requiresReply()) {
          return AgentResult.silent(correlationId);
        }
        throw new AgentRoutingException("Agent declined a required response");
      }
      persist(context, contextualizedPrompt, content, correlationId);
      return AgentResult.reply(correlationId, content);
    } catch (LlmException exception) {
      throw new AgentRoutingException(
          "Agent provider failed: " + exception.getMessage(), exception);
    }
  }

  private LlmResponse finalizeResponse(List<LlmMessage> messages) throws LlmException {
    List<LlmMessage> finalMessages = new ArrayList<>(messages);
    finalMessages.add(LlmMessage.user(FINALIZE_PROMPT));
    return client.complete(new LlmRequest(finalMessages, List.of()));
  }

  private List<JsonObject> definitions(AgentContext context) {
    List<JsonObject> result = new ArrayList<>();
    for (JsonElement definition : registry.definitions(context)) {
      result.add(definition.getAsJsonObject());
    }
    return List.copyOf(result);
  }

  private static String contextualizePrompt(AgentContext context, String prompt) {
    String visibility = context.whisper() ? "Private Saturn whisper" : "Public Saturn message";
    return "%s from @%s in #%s:%n%s".formatted(visibility, context.nick(), context.room(), prompt);
  }

  private void persist(AgentContext context, String user, String assistant, String correlationId) {
    try {
      memory.append(context, user, assistant, config);
    } catch (RuntimeException exception) {
      log.warn(
          "Agent memory append failed, correlationId={}: {}",
          correlationId,
          exception.getMessage());
      log.debug("Agent memory append failure, correlationId={}", correlationId, exception);
    }
  }

  private List<LlmMessage> loadMemory(AgentContext context, String correlationId) {
    try {
      return memory.load(context, config);
    } catch (RuntimeException exception) {
      log.warn(
          "Agent memory load failed, correlationId={}: {}", correlationId, exception.getMessage());
      log.debug("Agent memory load failure, correlationId={}", correlationId, exception);
      return List.of();
    }
  }

  private String loadConversationContext(AgentInvocation invocation, String correlationId) {
    if (invocation.mode() == AgentInvocationMode.DIRECT) {
      return "";
    }
    try {
      return conversationContextProvider.load(invocation.context());
    } catch (RuntimeException exception) {
      log.warn(
          "Agent room context load failed, correlationId={}: {}",
          correlationId,
          exception.getMessage());
      log.debug("Agent room context load failure, correlationId={}", correlationId, exception);
      return "";
    }
  }

  private static String truncate(String content, int maxChars) {
    if (codePointCount(content) <= maxChars) {
      return content;
    }
    return content.substring(0, content.offsetByCodePoints(0, maxChars));
  }

  private static int codePointCount(String content) {
    return content.codePointCount(0, content.length());
  }

  private static ReentrantLock[] sessionLocks() {
    ReentrantLock[] locks = new ReentrantLock[64];
    Arrays.setAll(locks, ignored -> new ReentrantLock(true));
    return locks;
  }
}
