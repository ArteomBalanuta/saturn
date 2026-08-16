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
import org.saturn.app.agent.llm.LlmToolCall;

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
  private static final String FRESH_TOOL_CORRECTION =
      PROMPTS.text("router-fresh-tool-correction.txt");
  private static final String FRESH_SYNTHESIS_CORRECTION =
      PROMPTS.text("router-fresh-synthesis-correction.txt").strip();

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
  private final AgentResponseSanitizer responseSanitizer = new AgentResponseSanitizer();
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
    AgentPreparedRequest prepared =
        requestAssembler.assemble(invocation, history, recentRoomContext);
    Optional<String> requiredFreshTool = prepared.requiredFreshTool();
    Optional<String> requiredFreshNick = prepared.requiredFreshNick();
    String contextualizedPrompt = prepared.contextualizedPrompt();
    List<LlmMessage> messages = new ArrayList<>(prepared.messages());
    List<JsonObject> definitions = prepared.definitions();
    AgentCommandProseGuard commandProseGuard = AgentCommandProseGuard.from(definitions);
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
        Optional<String> missingFreshTool =
            requiredFreshTool.filter(tool -> !turnState.successfulTools().contains(tool));
        if (missingFreshTool.isPresent()) {
          String tool = missingFreshTool.orElseThrow();
          if ("user_message_history".equals(tool) && requiredFreshNick.isPresent()) {
            if (!turnState.reserveToolCalls(1)) {
              throw new AgentRoutingException(
                  "Agent tool-call limit reached before loading fresh data");
            }
            JsonObject arguments = new JsonObject();
            arguments.addProperty("nick", requiredFreshNick.orElseThrow());
            LlmToolCall freshCall =
                new LlmToolCall("router-fresh-" + correlationId, tool, arguments.toString());
            AgentToolResult freshResult = toolExecutor.execute(context, freshCall);
            if (freshResult.isError()) {
              throw new AgentRoutingException("Required fresh-data tool failed: " + tool);
            }
            messages.add(LlmMessage.assistant(response.content(), List.of(freshCall)));
            messages.add(
                LlmMessage.tool(
                    freshCall.id(), modelVisibleToolResult(context, freshCall, freshResult)));
            turnState.recordSuccessfulTool(tool);
            turnState.recordSuccessfulToolResult(freshResult);
            log.info(
                "Agent fresh data loaded by router, correlationId={}, tool={}, nick={}",
                correlationId,
                tool,
                requiredFreshNick.orElseThrow());
            response = client.complete(new LlmRequest(messages, definitions));
            continue;
          }
          if (!isExactFreshToolCall(response, tool, requiredFreshNick)) {
            if (turnState.freshnessCorrectionUsed()) {
              throw new AgentRoutingException("Agent did not call the required fresh-data tool");
            }
            if (!turnState.toolsEnabled()) {
              throw new AgentRoutingException(
                  "Required fresh-data tool is unavailable after tool-call budget exhaustion");
            }
            log.warn("Agent fresh data required, correlationId={}, tool={}", correlationId, tool);
            messages.add(LlmMessage.assistant(response.content(), List.of()));
            messages.add(LlmMessage.user(FRESH_TOOL_CORRECTION.formatted(tool).strip()));
            response =
                freshDataPolicy.requireExactToolCall(
                    client.complete(new LlmRequest(messages, definitionFor(definitions, tool))),
                    tool,
                    requiredFreshNick);
            turnState.markFreshnessCorrectionUsed();
          }
        } else {
          if (requiredFreshTool.filter(turnState.successfulTools()::contains).isPresent()
              && repeatsPreviousAssistant(response, history)) {
            log.warn(
                "Agent reused a pre-lookup summary; requesting fresh synthesis, correlationId={}",
                correlationId);
            messages.add(LlmMessage.assistant(response.content(), List.of()));
            messages.add(LlmMessage.user(FRESH_SYNTHESIS_CORRECTION));
            response =
                requireFreshSynthesis(
                    client.complete(new LlmRequest(messages, List.of())), history);
          }
          if (freshDataPolicy.requiresHistorySynthesis(requiredFreshTool)
              && !AgentResponseCorrector.isFailurePlaceholder(response)
              && !freshDataPolicy.satisfiesProfileContract(
                  response, turnState.successfulToolResults())) {
            if (turnState.freshSynthesisCorrectionUsed()) {
              throw new AgentRoutingException(
                  "Agent did not produce a complete fresh history synthesis");
            }
            log.warn(
                "Agent fresh history synthesis omitted evidence metadata, correlationId={}",
                correlationId);
            messages.add(LlmMessage.assistant(response.content(), List.of()));
            messages.add(LlmMessage.user(FRESH_SYNTHESIS_CORRECTION));
            response =
                requireFreshSynthesis(
                    client.complete(new LlmRequest(messages, List.of())), history);
            turnState.markFreshSynthesisCorrectionUsed();
            if (!freshDataPolicy.satisfiesProfileContract(
                response, turnState.successfulToolResults())) {
              throw new AgentRoutingException(
                  "Agent did not produce a complete fresh history synthesis");
            }
          }
          if (!turnState.unverifiedActionChecked()
              && (turnState.successfulCommands().isEmpty()
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
        for (int index = 0; index < response.toolCalls().size(); index++) {
          var call = response.toolCalls().get(index);
          AgentToolResult result = toolResults.get(index);
          log.info(
              "Agent tool completed, correlationId={}, tool={}, outcome={}",
              correlationId,
              call.name(),
              result.isError() ? "error" : "success");
          if (result.isError()
              && requiredFreshTool.filter(call.name()::equals).isPresent()
              && !turnState.successfulTools().contains(call.name())) {
            throw new AgentRoutingException("Required fresh-data tool failed: " + call.name());
          }
          if (!result.isError()
              && freshDataPolicy.matchesTarget(call, requiredFreshNick)
              && turnState.recordSuccessfulTool(call.name())) {
            requiredFreshTool
                .filter(call.name()::equals)
                .ifPresent(
                    tool ->
                        log.info(
                            "Agent fresh data satisfied, correlationId={}, tool={}",
                            correlationId,
                            tool));
          }
          if (!result.isError()) {
            turnState.recordSuccessfulToolResult(result);
          }
          if (!result.isError() && "run_command".equals(call.name())) {
            commandProseGuard.executedCommand(call).ifPresent(turnState::recordSuccessfulCommand);
            turnState.clearCommandCorrection();
          } else if (result.isError() && "run_command".equals(call.name())) {
            commandProseGuard.executedCommand(call).ifPresent(turnState::recordFailedCommand);
          }
          messages.add(LlmMessage.tool(call.id(), modelVisibleToolResult(context, call, result)));
        }
        turnState.resetUnverifiedActionCheck();
        response = client.complete(new LlmRequest(messages, definitions));
      }

      response = responseCorrector.correctFailurePlaceholder(response, messages, correlationId);
      if (freshDataPolicy.requiresHistorySynthesis(requiredFreshTool)
          && !freshDataPolicy.satisfiesProfileContract(
              response, turnState.successfulToolResults())) {
        throw new AgentRoutingException("Agent did not produce a complete fresh history synthesis");
      }
      String sanitizedContent = responseSanitizer.sanitize(response.content());
      if (invocation.mode() == AgentInvocationMode.MODERATION) {
        return AgentResult.silent(correlationId);
      }
      if (sanitizedContent.strip().equals(participationConfig.noReplyMarker())) {
        if (!invocation.mode().requiresReply()) {
          return AgentResult.silent(correlationId);
        }
        throw new AgentRoutingException("Agent declined a required response");
      }
      String content = truncate(removeNoReplyMarker(sanitizedContent), config.maxOutputChars());
      if (content.isBlank()) {
        throw new AgentRoutingException("Agent returned an empty response");
      }
      persist(context, contextualizedPrompt, content, correlationId);
      persistToolEvidence(context, turnState.successfulToolResults(), correlationId);
      return AgentResult.reply(correlationId, content);
    } catch (LlmException exception) {
      throw new AgentRoutingException(
          "Agent provider failed: " + exception.getMessage(), exception);
    }
  }

  /** Removes the control-only silence token when a model improperly mixes it into visible prose. */
  private String removeNoReplyMarker(String content) {
    String marker = participationConfig.noReplyMarker();
    if (!content.contains(marker)) {
      return content;
    }
    return trimControlWhitespace(content.replace(marker, ""));
  }

  /** Trims only ASCII control whitespace so Saturn's U+2009 formatting spaces survive delivery. */
  private String trimControlWhitespace(String content) {
    int first = 0;
    int last = content.length();
    while (first < last && isControlWhitespace(content.charAt(first))) {
      first++;
    }
    while (last > first && isControlWhitespace(content.charAt(last - 1))) {
      last--;
    }
    return content.substring(first, last);
  }

  private boolean isControlWhitespace(char character) {
    return character == ' ' || character == '\t' || character == '\n' || character == '\r';
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

  private static Optional<String> latestContent(List<LlmMessage> messages, String role) {
    for (int index = messages.size() - 1; index >= 0; index--) {
      LlmMessage message = messages.get(index);
      if (role.equals(message.role())) {
        return Optional.ofNullable(message.content());
      }
    }
    return Optional.empty();
  }

  private static List<JsonObject> definitionFor(List<JsonObject> definitions, String toolName)
      throws AgentRoutingException {
    List<JsonObject> matches =
        definitions.stream()
            .filter(definition -> isNamedToolDefinition(definition, toolName))
            .map(JsonObject::deepCopy)
            .toList();
    if (matches.size() != 1) {
      throw new AgentRoutingException("Required fresh-data tool is not exposed: " + toolName);
    }
    return matches;
  }

  private boolean isExactFreshToolCall(
      LlmResponse response, String toolName, Optional<String> expectedNick) {
    try {
      freshDataPolicy.requireExactToolCall(response, toolName, expectedNick);
      return true;
    } catch (AgentRoutingException exception) {
      return false;
    }
  }

  private static LlmResponse requireFreshSynthesis(LlmResponse response, List<LlmMessage> history)
      throws AgentRoutingException {
    if (!response.toolCalls().isEmpty()) {
      throw new AgentRoutingException(
          "Agent returned a tool call instead of a fresh history synthesis");
    }
    if (repeatsPreviousAssistant(response, history)) {
      throw new AgentRoutingException(
          "Agent reused the previous answer after a fresh history lookup");
    }
    return response;
  }

  private static boolean repeatsPreviousAssistant(LlmResponse response, List<LlmMessage> history) {
    if (!response.toolCalls().isEmpty()
        || response.content() == null
        || response.content().isBlank()) {
      return false;
    }
    return latestContent(history, "assistant")
        .map(previous -> response.content().strip().equals(previous.strip()))
        .orElse(false);
  }

  private static boolean isNamedToolDefinition(JsonObject definition, String toolName) {
    JsonObject function = definition.getAsJsonObject("function");
    return function != null
        && function.has("name")
        && toolName.equals(function.get("name").getAsString());
  }

  private LlmResponse finalizeResponse(List<LlmMessage> messages) throws LlmException {
    List<LlmMessage> finalMessages = new ArrayList<>(messages);
    finalMessages.add(LlmMessage.user(FINALIZE_PROMPT));
    return client.complete(new LlmRequest(finalMessages, List.of()));
  }

  private void persist(AgentContext context, String user, String assistant, String correlationId)
      throws AgentRoutingException {
    try {
      memory.append(context, user, assistant, config);
    } catch (RuntimeException exception) {
      throw memoryPersistenceFailure(correlationId, exception);
    }
    log.info("Agent memory persisted, correlationId={}", correlationId);
  }

  private void persistToolEvidence(
      AgentContext context, List<AgentToolResult> results, String correlationId)
      throws AgentRoutingException {
    try {
      for (AgentToolResult result : results) {
        memory.appendToolEvidence(context, result.toolName(), result.content(), config);
      }
    } catch (RuntimeException exception) {
      throw memoryPersistenceFailure(correlationId, exception);
    }
  }

  private static AgentRoutingException memoryPersistenceFailure(
      String correlationId, RuntimeException exception) {
    log.warn(
        "Agent memory append failed, correlationId={}: {}", correlationId, exception.getMessage());
    log.debug("Agent memory append failure, correlationId={}", correlationId, exception);
    return new AgentRoutingException("Agent memory persistence failed", exception);
  }

  private List<LlmMessage> loadMemory(AgentContext context, String correlationId)
      throws AgentRoutingException {
    List<LlmMessage> loaded;
    try {
      loaded = memory.load(context, config);
    } catch (RuntimeException exception) {
      throw memoryLoadFailure(correlationId, exception);
    }
    List<LlmMessage> history = responseSanitizer.excludeLegacyPersonaTurns(loaded);
    log.info(
        "Agent memory loaded, correlationId={}, messages={}, legacyMessagesExcluded={}",
        correlationId,
        history.size(),
        loaded.size() - history.size());
    return history;
  }

  private static AgentRoutingException memoryLoadFailure(
      String correlationId, RuntimeException exception) {
    log.warn(
        "Agent memory load failed, correlationId={}: {}", correlationId, exception.getMessage());
    log.debug("Agent memory load failure, correlationId={}", correlationId, exception);
    return new AgentRoutingException("Agent memory load failed", exception);
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
