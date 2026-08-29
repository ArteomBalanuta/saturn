package org.saturn.app.agent.routing;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentConversationContextProvider;
import org.saturn.app.agent.api.AgentExecutionLimits;
import org.saturn.app.agent.api.AgentInvocation;
import org.saturn.app.agent.api.AgentInvocationMode;
import org.saturn.app.agent.api.AgentMemoryStore;
import org.saturn.app.agent.api.AgentParticipationConfig;
import org.saturn.app.agent.api.AgentResult;
import org.saturn.app.agent.api.AgentRouter;
import org.saturn.app.agent.api.AgentRoutingException;
import org.saturn.app.agent.api.AgentToolResult;
import org.saturn.app.agent.api.ToolResultMode;
import org.saturn.app.agent.config.AgentConfig;
import org.saturn.app.agent.llm.LlmClient;
import org.saturn.app.agent.llm.LlmException;
import org.saturn.app.agent.llm.LlmMessage;
import org.saturn.app.agent.llm.LlmRequest;
import org.saturn.app.agent.llm.LlmResponse;
import org.saturn.app.agent.room.AgentSessionLockManager;
import org.saturn.app.agent.tool.contract.AgentToolDefinitionJson;
import org.saturn.app.agent.tool.execution.AgentModelVisibleToolResultRenderer;
import org.saturn.app.agent.tool.execution.AgentToolBatchContext;
import org.saturn.app.agent.tool.execution.AgentToolBudgetPolicy;
import org.saturn.app.agent.tool.execution.AgentToolExecutionHooks;
import org.saturn.app.agent.tool.execution.AgentToolExecutor;
import org.saturn.app.agent.tool.execution.AgentToolRegistry;
import org.saturn.app.agent.tool.execution.AgentToolResultCoordinator;
import org.saturn.app.agent.turn.AgentFreshDataCoordinator;
import org.saturn.app.agent.turn.AgentFreshDataFinalValidator;
import org.saturn.app.agent.turn.AgentFreshDataPolicy;
import org.saturn.app.agent.turn.AgentFreshDataTurnPolicy;
import org.saturn.app.agent.turn.AgentTurnMemory;
import org.saturn.app.agent.turn.AgentTurnPolicyChain;
import org.saturn.app.agent.turn.AgentTurnPolicyInput;
import org.saturn.app.agent.turn.AgentTurnPolicyResult;
import org.saturn.app.agent.turn.AgentTurnState;
import org.saturn.app.agent.turn.AgentUnverifiedActionPolicy;

/**
 * Coordinates one bounded LLM tool-calling session for a Saturn invocation.
 *
 * <p>Calls sharing a memory key are serialized through striped locks so memory, tool observations,
 * and room replies retain session order. Tool execution state is request-local in {@link
 * AgentToolExecutor}; this router is safe to call concurrently for distinct sessions.
 */
@Slf4j
public final class DefaultAgentRouter implements AgentRouter {
  private static final AgentPromptCatalog PROMPTS = new AgentPromptCatalog();
  private static final String FINALIZE_PROMPT = PROMPTS.text("system/router-finalize.txt").strip();

  private final AgentConfig config;
  private final LlmClient client;
  private final AgentToolRegistry registry;
  private final AgentMemoryStore memory;
  private final AgentParticipationConfig participationConfig;
  private final AgentConversationContextProvider conversationContextProvider;
  private final AgentSystemPrompt systemPrompt;
  private final AgentResponseCorrector responseCorrector;
  private final AgentTurnPolicyChain turnPolicyChain;
  private final AgentToolBudgetPolicy toolBudgetPolicy = new AgentToolBudgetPolicy();
  private final AgentFreshDataPolicy freshDataPolicy = new AgentFreshDataPolicy();
  private final AgentFreshDataCoordinator freshDataCoordinator;
  private final AgentModelVisibleToolResultRenderer modelVisibleToolResultRenderer;
  private final AgentTurnMemory turnMemory;
  private final AgentResponseFinalizer responseFinalizer;
  private final AgentRequestAssembler requestAssembler;
  private final AgentRequestClassifier requestClassifier = new AgentRequestClassifier();
  private final AgentSessionLockManager sessionLockManager = new AgentSessionLockManager();
  private final AgentToolExecutionHooks executionHooks;

  /** Creates a router with default participation, context, and execution-hook policies. */
  public DefaultAgentRouter(
      AgentConfig config, LlmClient client, AgentToolRegistry registry, AgentMemoryStore memory) {
    this(
        config,
        client,
        registry,
        memory,
        AgentParticipationConfig.from(null),
        AgentConversationContextProvider.none(),
        AgentToolExecutionHooks.empty());
  }

  /** Creates a router with explicit participation and conversation-context policies. */
  public DefaultAgentRouter(
      AgentConfig config,
      LlmClient client,
      AgentToolRegistry registry,
      AgentMemoryStore memory,
      AgentParticipationConfig participationConfig,
      AgentConversationContextProvider conversationContextProvider) {
    this(
        config,
        client,
        registry,
        memory,
        participationConfig,
        conversationContextProvider,
        AgentToolExecutionHooks.empty());
  }

  /** Creates a router with all collaborators explicitly supplied. */
  public DefaultAgentRouter(
      AgentConfig config,
      LlmClient client,
      AgentToolRegistry registry,
      AgentMemoryStore memory,
      AgentParticipationConfig participationConfig,
      AgentConversationContextProvider conversationContextProvider,
      AgentToolExecutionHooks executionHooks) {
    this.config = config;
    this.client = client;
    this.registry = registry;
    this.memory = memory;
    this.participationConfig = participationConfig;
    this.conversationContextProvider = conversationContextProvider;
    this.executionHooks = executionHooks;
    this.systemPrompt = new AgentSystemPrompt(participationConfig);
    this.responseCorrector = new AgentResponseCorrector(client);
    this.turnPolicyChain =
        new AgentTurnPolicyChain(
            List.of(
                new AgentFreshDataTurnPolicy(),
                new AgentUnverifiedActionPolicy(responseCorrector),
                new AgentCommandChannelPolicy(client)));
    this.freshDataCoordinator = new AgentFreshDataCoordinator(client, freshDataPolicy);
    this.modelVisibleToolResultRenderer = new AgentModelVisibleToolResultRenderer(registry);
    this.turnMemory = new AgentTurnMemory(memory, config);
    this.responseFinalizer =
        new AgentResponseFinalizer(
            responseCorrector,
            new AgentFreshDataFinalValidator(freshDataPolicy),
            participationConfig,
            config.maxOutputChars());
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

    return sessionLockManager.withLock(
        invocation.context().memoryKey(), () -> routeInSession(invocation));
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
    AgentToolExecutor toolExecutor =
        new AgentToolExecutor(registry, config, allowedTools, executionHooks);
    AgentToolBatchContext batchContext =
        new AgentToolBatchContext(
            java.time.Instant.now().plus(config.timeout()),
            new org.saturn.app.agent.tool.execution.CancellationToken());
    AgentTurnState turnState = new AgentTurnState(AgentExecutionLimits.from(config));
    try (toolExecutor) {
      LlmResponse response =
          requiredFreshTool.isPresent()
              ? client.complete(
                  providerRequest(messages, definitions, requestAssembler.contextBudget()))
              : responseCorrector.completeInitialRequest(
                  messages, definitions, history, invocation.prompt(), correlationId);
      response = AgentResponseCorrector.requireResponse(response);
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
                modelVisibleToolResultRenderer::render,
                DefaultAgentRouter::definitionFor);
        response = freshDataResult.response();
        response = AgentResponseCorrector.requireResponse(response);
        if (freshDataResult.restartLoop()) {
          if (turnState.attemptedToolCount() > 0) {
            messages =
                replaceSystemMessageForProvider(
                    messages,
                    LlmMessage.system(
                        systemPrompt.render(
                            invocation,
                            correlationId,
                            AgentTextBounds.truncate(
                                recentRoomContext, requestAssembler.contextBudget()),
                            AgentRequestKind.TOOL_CALL,
                            turnState.toolEvidence(),
                            "FINAL")),
                    requestAssembler.contextBudget());
          }
          continue;
        }
        AgentTurnPolicyResult guarded =
            turnPolicyChain.apply(
                new AgentTurnPolicyInput(
                    response,
                    messages,
                    definitions,
                    commandProseGuard,
                    turnState,
                    invocation.prompt(),
                    correlationId,
                    requiredFreshTool));
        response = guarded.response();
        if (guarded.correctionUsed()) {
          turnState.markCommandCorrectionUsed();
        }

        if (response.toolCalls().isEmpty()) {
          break;
        }
        if (!turnState.toolsEnabled()) {
          throw new AgentRoutingException("Agent returned a tool call after tools were disabled");
        }
        AgentToolBudgetPolicy.Result budgetResult =
            toolBudgetPolicy.reserve(response.toolCalls().size(), turnState);
        if (budgetResult.finalizeWithoutTools()) {
          response = finalizeResponse(messages, requestAssembler.contextBudget());
          continue;
        }

        messages.add(LlmMessage.assistant(response.content(), response.toolCalls()));
        turnState.markToolAttempted(response.toolCalls().size());
        List<AgentToolResult> toolResults =
            toolExecutor.executeAll(context, response.toolCalls(), batchContext);
        toolResultCoordinator.record(
            context,
            response.toolCalls(),
            toolResults,
            requiredFreshTool,
            requiredFreshNick,
            turnState,
            messages,
            modelVisibleToolResultRenderer::render,
            correlationId);
        turnState.resetUnverifiedActionCheck();
        messages =
            replaceSystemMessageForProvider(
                messages,
                LlmMessage.system(
                    systemPrompt.render(
                        invocation,
                        correlationId,
                        AgentTextBounds.truncate(
                            recentRoomContext, requestAssembler.contextBudget()),
                        AgentRequestKind.TOOL_CALL,
                        turnState.toolEvidence(),
                        "FINAL")),
                requestAssembler.contextBudget());
        response =
            AgentResponseCorrector.requireResponse(
                client.complete(
                    providerRequest(messages, definitions, requestAssembler.contextBudget())));
      }

      AgentResponseFinalizer.Result finalResponse =
          responseFinalizer.prepare(
              invocation,
              response,
              messages,
              requiredFreshTool,
              turnState.successfulToolResults(),
              correlationId,
              requestClassifier.finalizeKind(prepared.requestKind(), turnState.toolEvidence()),
              turnState.toolEvidence());
      if (!finalResponse.shouldReply()) {
        return AgentResult.silent(correlationId);
      }
      String content = finalResponse.content();
      turnMemory.append(context, contextualizedPrompt, content, correlationId);
      turnMemory.appendToolEvidence(
          context,
          persistentToolEvidence(context, turnState.successfulToolResults()),
          correlationId);
      return AgentResult.reply(correlationId, content);
    } catch (LlmException exception) {
      throw new AgentRoutingException(
          "Agent provider failed: " + exception.getMessage(), exception);
    }
  }

  private static LlmRequest providerRequest(
      List<LlmMessage> messages, List<JsonObject> definitions, int budget) {
    AgentContextProjection projection = new AgentMessageProjector().project(messages, budget);
    return new LlmRequest(projection.messages(), definitions, projection);
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

  /**
   * Replaces the provider system message without exceeding the supplied message budget.
   *
   * @param messages the messages input; null handling follows the validation performed by this
   *     declaration
   * @param replacement the replacement input; null handling follows the validation performed by
   *     this declaration
   * @param budget the budget input; null handling follows the validation performed by this
   *     declaration
   * @return the computed result; empty or false indicates that no applicable value was available
   */
  static List<LlmMessage> replaceSystemMessageForProvider(
      List<LlmMessage> messages, LlmMessage replacement, int budget) {
    List<LlmMessage> providerCopy = new ArrayList<>(messages);
    providerCopy.set(0, replacement);
    AgentMessageProjector projector = new AgentMessageProjector();
    List<LlmMessage> projected =
        ("user".equals(providerCopy.getLast().role())
                ? projector.project(providerCopy, budget)
                : projector.projectAfterTool(providerCopy, budget))
            .messages();
    return new ArrayList<>(projected);
  }

  private List<AgentToolResult> persistentToolEvidence(
      AgentContext context, List<AgentToolResult> results) {
    return results.stream()
        .filter(
            result ->
                registry
                    .find(context, result.toolName())
                    .map(tool -> tool.descriptor(context).resultMode() == ToolResultMode.MODEL_DATA)
                    .orElse(false))
        .toList();
  }

  private LlmResponse finalizeResponse(List<LlmMessage> messages, int budget)
      throws LlmException, AgentRoutingException {
    List<LlmMessage> finalMessages = new ArrayList<>(messages);
    finalMessages.add(LlmMessage.user(FINALIZE_PROMPT));
    return AgentResponseCorrector.requireResponse(
        client.complete(providerRequest(finalMessages, List.of(), budget)));
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
}
