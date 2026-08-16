package org.saturn.app.agent;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.agent.llm.LlmClient;
import org.saturn.app.agent.llm.LlmException;
import org.saturn.app.agent.llm.LlmMessage;
import org.saturn.app.agent.llm.LlmRequest;
import org.saturn.app.agent.llm.LlmResponse;

@Slf4j
/**
 * Coordinates one bounded LLM tool-calling session for a Saturn invocation.
 *
 * <p>Calls sharing a memory key are serialized through striped locks so memory, tool observations,
 * and room replies retain session order. Tool execution state is request-local in {@link
 * AgentToolExecutor}; this router is safe to call concurrently for distinct sessions.
 */
public final class DefaultAgentRouter implements AgentRouter {
  private static final AgentPromptCatalog PROMPTS = new AgentPromptCatalog();
  private static final String FINALIZE_PROMPT = PROMPTS.text("router-finalize.txt").strip();

  private final AgentConfig config;
  private final LlmClient client;
  private final AgentToolRegistry registry;
  private final AgentMemoryStore memory;
  private final AgentParticipationConfig participationConfig;
  private final AgentConversationContextProvider conversationContextProvider;
  private final AgentSystemPrompt systemPrompt;
  private final AgentResponseCorrector responseCorrector;
  private final AgentCommandChannelPolicy commandChannelPolicy;
  private final AgentFreshDataPolicy freshDataPolicy = new AgentFreshDataPolicy();
  private final AgentFreshDataCoordinator freshDataCoordinator;
  private final AgentTurnMemory turnMemory;
  private final AgentResponseFinalizer responseFinalizer;
  private final AgentRequestAssembler requestAssembler;
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
    this.responseCorrector = new AgentResponseCorrector(client);
    this.commandChannelPolicy = new AgentCommandChannelPolicy(client);
    this.freshDataCoordinator = new AgentFreshDataCoordinator(client, freshDataPolicy);
    this.turnMemory = new AgentTurnMemory(memory, config);
    this.responseFinalizer =
        new AgentResponseFinalizer(
            responseCorrector, freshDataCoordinator, participationConfig, config.maxOutputChars());
    this.requestAssembler = new AgentRequestAssembler(config, registry, systemPrompt);
  }

  /**
   * Routes an invocation while preserving order for its shared conversation session.
   *
   * @param invocation immutable request metadata and user prompt
   * @return a reply or intentional silent result
   * @throws AgentRoutingException for size-limit, provider, or bounded-loop failures
   */
  @Override
  public AgentResult route(AgentInvocation invocation) throws AgentRoutingException {
    if (AgentTextBounds.codePointCount(invocation.prompt()) > config.maxPromptChars()) {
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
    AgentPreparedRequest prepared =
        requestAssembler.assemble(invocation, history, recentRoomContext);
    Optional<String> requiredFreshTool = prepared.requiredFreshTool();
    Optional<String> requiredFreshNick = prepared.requiredFreshNick();
    String contextualizedPrompt = prepared.contextualizedPrompt();
    List<LlmMessage> messages = new ArrayList<>(prepared.messages());
    List<JsonObject> definitions = prepared.definitions();
    AgentCommandProseGuard commandProseGuard = AgentCommandProseGuard.from(definitions);
    AgentToolResultCoordinator toolResultCoordinator =
        new AgentToolResultCoordinator(freshDataPolicy, commandProseGuard);
    Set<String> allowedTools =
        invocation.mode() == AgentInvocationMode.MODERATION ? Set.of("run_command") : Set.of();
    AgentToolExecutor toolExecutor = new AgentToolExecutor(registry, config, allowedTools);
    AgentTurnState turnState = new AgentTurnState(AgentExecutionLimits.from(config));
    try (toolExecutor) {
      LlmResponse response =
          requiredFreshTool.isPresent()
              ? client.complete(new LlmRequest(messages, definitions))
              : responseCorrector.completeInitialRequest(
                  messages, definitions, history, invocation.prompt(), correlationId);
      while (true) {
        if (!turnState.advanceStep()) {
          throw new AgentRoutingException("Agent execution step limit reached");
        }
        if ("length".equalsIgnoreCase(response.finishReason()) && response.toolCalls().isEmpty()) {
          throw new AgentRoutingException("Agent response was truncated before completion");
        }
        AgentFreshDataCoordinator.Result freshDataResult =
            freshDataCoordinator.process(
                response,
                messages,
                definitions,
                history,
                requiredFreshTool,
                requiredFreshNick,
                context,
                toolExecutor,
                turnState,
                correlationId,
                this::modelVisibleToolResult,
                DefaultAgentRouter::definitionFor);
        response = freshDataResult.response();
        if (freshDataResult.restartLoop()) {
          continue;
        }
        if (requiredFreshTool.isEmpty()
            || turnState.hasSuccessfulTool(requiredFreshTool.orElseThrow())) {
          if (!turnState.unverifiedActionChecked()
              && (!turnState.hasSuccessfulCommands()
                  || commandProseGuard.findCommand(response.content()).isEmpty())) {
            response =
                responseCorrector.correctUnverifiedActionClaim(
                    response, messages, definitions, correlationId);
            turnState.markUnverifiedActionChecked();
          }
          AgentCommandChannelPolicy.Result guarded =
              commandChannelPolicy.enforce(
                  response,
                  messages,
                  definitions,
                  commandProseGuard,
                  turnState,
                  invocation.prompt(),
                  correlationId);
          response = guarded.response();
          if (guarded.correctionUsed()) {
            turnState.markCommandCorrectionUsed();
          }
        }

        if (response.toolCalls().isEmpty()) {
          break;
        }
        if (!turnState.toolsEnabled()) {
          throw new AgentRoutingException("Agent returned a tool call after tools were disabled");
        }
        if (!turnState.reserveToolCalls(response.toolCalls().size())) {
          turnState.disableTools();
          response = finalizeResponse(messages);
          continue;
        }

        messages.add(LlmMessage.assistant(response.content(), response.toolCalls()));
        List<AgentToolResult> toolResults = toolExecutor.executeAll(context, response.toolCalls());
        toolResultCoordinator.record(
            context,
            response.toolCalls(),
            toolResults,
            requiredFreshTool,
            requiredFreshNick,
            turnState,
            messages,
            this::modelVisibleToolResult,
            correlationId);
        turnState.resetUnverifiedActionCheck();
        response = client.complete(new LlmRequest(messages, definitions));
      }

      AgentResponseFinalizer.Result finalResponse =
          responseFinalizer.prepare(
              invocation,
              response,
              messages,
              requiredFreshTool,
              turnState.successfulToolResults(),
              correlationId);
      if (!finalResponse.shouldReply()) {
        return AgentResult.silent(correlationId);
      }
      String content = finalResponse.content();
      turnMemory.append(context, contextualizedPrompt, content, correlationId);
      turnMemory.appendToolEvidence(context, turnState.successfulToolResults(), correlationId);
      return AgentResult.reply(correlationId, content);
    } catch (LlmException exception) {
      throw new AgentRoutingException(
          "Agent provider failed: " + exception.getMessage(), exception);
    }
  }

  private String modelVisibleToolResult(
      AgentContext context, org.saturn.app.agent.llm.LlmToolCall call, AgentToolResult result) {
    if (result.isError()) {
      return result.envelopeJson();
    }
    return registry
        .find(context, call.name())
        .map(tool -> tool.descriptor(context).resultMode())
        .filter(mode -> mode == ToolResultMode.ROOM_DELIVERY)
        .map(
            mode ->
                ToolResponseEnvelope.success(PROMPTS.text("router-room-delivery.txt").strip())
                    .toJson())
        .orElse(result.envelopeJson());
  }

  private static List<JsonObject> definitionFor(List<JsonObject> definitions, String toolName)
      throws AgentRoutingException {
    List<JsonObject> matches =
        definitions.stream()
            .filter(
                definition ->
                    AgentToolDefinitionJson.functionName(definition)
                        .filter(toolName::equals)
                        .isPresent())
            .map(JsonObject::deepCopy)
            .toList();
    if (matches.size() != 1) {
      throw new AgentRoutingException("Required fresh-data tool is not exposed: " + toolName);
    }
    return matches;
  }

  private LlmResponse finalizeResponse(List<LlmMessage> messages) throws LlmException {
    List<LlmMessage> finalMessages = new ArrayList<>(messages);
    finalMessages.add(LlmMessage.user(FINALIZE_PROMPT));
    return client.complete(new LlmRequest(finalMessages, List.of()));
  }

  private List<LlmMessage> loadMemory(AgentContext context, String correlationId)
      throws AgentRoutingException {
    return turnMemory.load(context, correlationId);
  }

  private String loadConversationContext(AgentInvocation invocation, String correlationId) {
    if (invocation.context().whisper()) {
      return "";
    }
    try {
      return conversationContextProvider.load(
          invocation.context(), invocation.context().nick(), invocation.currentMessageText());
    } catch (RuntimeException exception) {
      log.warn(
          "Agent room context load failed, correlationId={}: {}",
          correlationId,
          exception.getMessage());
      log.debug("Agent room context load failure, correlationId={}", correlationId, exception);
      return "";
    }
  }

  private static ReentrantLock[] sessionLocks() {
    ReentrantLock[] locks = new ReentrantLock[64];
    Arrays.setAll(locks, ignored -> new ReentrantLock(true));
    return locks;
  }
}
