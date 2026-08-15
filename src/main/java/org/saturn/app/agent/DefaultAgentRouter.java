package org.saturn.app.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
  private static final String RESPOND_WITHOUT_COMMAND = "respond_without_command";
  private static final String COMMAND_TOOL_CORRECTION =
      PROMPTS.text("router-command-tool-correction.txt");
  private static final String COMMAND_OUTPUT_CORRECTION =
      PROMPTS.text("router-command-output-correction.txt");
  private static final String COMMAND_NOT_EXECUTED_CORRECTION =
      PROMPTS.text("router-command-not-executed-correction.txt");
  private static final String FRESH_TOOL_CORRECTION =
      PROMPTS.text("router-fresh-tool-correction.txt");
  private static final String FRESH_SYNTHESIS_CORRECTION =
      PROMPTS.text("router-fresh-synthesis-correction.txt").strip();
  private static final String FAILURE_PLACEHOLDER_CORRECTION =
      PROMPTS.text("router-failure-placeholder-correction.txt").strip();
  private static final String STALE_RESPONSE_CORRECTION =
      PROMPTS.text("router-stale-response-correction.txt");
  private static final String UNVERIFIED_ACTION_CORRECTION =
      PROMPTS.text("router-unverified-action-correction.txt");

  private final AgentConfig config;
  private final LlmClient client;
  private final AgentToolRegistry registry;
  private final AgentMemoryStore memory;
  private final AgentParticipationConfig participationConfig;
  private final AgentConversationContextProvider conversationContextProvider;
  private final AgentSystemPrompt systemPrompt;
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
    AgentExecutionState executionState = new AgentExecutionState(AgentExecutionLimits.from(config));
    try (toolExecutor) {
      LlmResponse response =
          requiredFreshTool.isPresent()
              ? client.complete(new LlmRequest(messages, definitions))
              : completeInitialRequest(
                  messages, definitions, history, invocation.prompt(), correlationId);
      boolean correctionUsed = false;
      boolean freshnessCorrectionUsed = false;
      boolean freshSynthesisCorrectionUsed = false;
      boolean unverifiedActionChecked = false;
      Set<String> successfulCommands = new HashSet<>();
      Set<String> failedCommands = new HashSet<>();
      Set<String> successfulTools = new HashSet<>();
      List<AgentToolResult> successfulToolResults = new ArrayList<>();
      boolean toolsEnabled = true;
      while (true) {
        if (!executionState.advanceStep()) {
          throw new AgentRoutingException("Agent execution step limit reached");
        }
        if ("length".equalsIgnoreCase(response.finishReason()) && response.toolCalls().isEmpty()) {
          throw new AgentRoutingException("Agent response was truncated before completion");
        }
        Optional<String> missingFreshTool =
            requiredFreshTool.filter(tool -> !successfulTools.contains(tool));
        if (missingFreshTool.isPresent()) {
          String tool = missingFreshTool.orElseThrow();
          if ("user_message_history".equals(tool) && requiredFreshNick.isPresent()) {
            if (!executionState.reserveToolCalls(1)) {
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
            successfulTools.add(tool);
            successfulToolResults.add(freshResult);
            log.info(
                "Agent fresh data loaded by router, correlationId={}, tool={}, nick={}",
                correlationId,
                tool,
                requiredFreshNick.orElseThrow());
            response = client.complete(new LlmRequest(messages, definitions));
            continue;
          }
          if (!callsExactly(response, tool, requiredFreshNick)) {
            if (freshnessCorrectionUsed) {
              throw new AgentRoutingException("Agent did not call the required fresh-data tool");
            }
            if (!toolsEnabled) {
              throw new AgentRoutingException(
                  "Required fresh-data tool is unavailable after tool-call budget exhaustion");
            }
            log.warn("Agent fresh data required, correlationId={}, tool={}", correlationId, tool);
            messages.add(LlmMessage.assistant(response.content(), List.of()));
            messages.add(LlmMessage.user(FRESH_TOOL_CORRECTION.formatted(tool).strip()));
            response =
                requireFreshToolCall(
                    client.complete(new LlmRequest(messages, definitionFor(definitions, tool))),
                    tool,
                    requiredFreshNick);
            freshnessCorrectionUsed = true;
          }
        } else {
          if (requiredFreshTool.filter(successfulTools::contains).isPresent()
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
          if (requiredFreshTool.filter("user_message_history"::equals).isPresent()
              && !isFailurePlaceholder(response)
              && !satisfiesFreshProfileContract(
                  response, requiredFreshNick, successfulToolResults)) {
            if (freshSynthesisCorrectionUsed) {
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
            freshSynthesisCorrectionUsed = true;
            if (!satisfiesFreshProfileContract(
                response, requiredFreshNick, successfulToolResults)) {
              throw new AgentRoutingException(
                  "Agent did not produce a complete fresh history synthesis");
            }
          }
          if (!unverifiedActionChecked
              && (successfulCommands.isEmpty()
                  || commandProseGuard.findCommand(response.content()).isEmpty())) {
            response = correctUnverifiedActionClaim(response, messages, definitions, correlationId);
            unverifiedActionChecked = true;
          }
          GuardedResponse guarded =
              enforceCommandChannel(
                  response,
                  messages,
                  definitions,
                  commandProseGuard,
                  correctionUsed,
                  successfulCommands,
                  failedCommands,
                  toolsEnabled,
                  invocation.prompt(),
                  correlationId);
          response = guarded.response();
          correctionUsed = guarded.correctionUsed();
        }

        if (response.toolCalls().isEmpty()) {
          break;
        }
        if (!toolsEnabled) {
          throw new AgentRoutingException("Agent returned a tool call after tools were disabled");
        }
        if (!executionState.reserveToolCalls(response.toolCalls().size())) {
          toolsEnabled = false;
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
              && !successfulTools.contains(call.name())) {
            throw new AgentRoutingException("Required fresh-data tool failed: " + call.name());
          }
          if (!result.isError()
              && matchesFreshTarget(call, requiredFreshNick)
              && successfulTools.add(call.name())) {
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
            successfulToolResults.add(result);
          }
          if (!result.isError() && "run_command".equals(call.name())) {
            commandProseGuard.executedCommand(call).ifPresent(successfulCommands::add);
            correctionUsed = false;
          } else if (result.isError() && "run_command".equals(call.name())) {
            commandProseGuard.executedCommand(call).ifPresent(failedCommands::add);
          }
          messages.add(LlmMessage.tool(call.id(), modelVisibleToolResult(context, call, result)));
        }
        unverifiedActionChecked = false;
        response = client.complete(new LlmRequest(messages, definitions));
      }

      response = correctFailurePlaceholder(response, messages, correlationId);
      if (requiredFreshTool.filter("user_message_history"::equals).isPresent()
          && !satisfiesFreshProfileContract(response, requiredFreshNick, successfulToolResults)) {
        throw new AgentRoutingException("Agent did not produce a complete fresh history synthesis");
      }
      String content =
          truncate(responseSanitizer.sanitize(response.content()), config.maxOutputChars());
      if (invocation.mode() == AgentInvocationMode.MODERATION) {
        return AgentResult.silent(correlationId);
      }
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
      persistToolEvidence(context, successfulToolResults, correlationId);
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

  private LlmResponse completeInitialRequest(
      List<LlmMessage> messages,
      List<JsonObject> definitions,
      List<LlmMessage> history,
      String currentPrompt,
      String correlationId)
      throws LlmException, AgentRoutingException {
    LlmResponse response = client.complete(new LlmRequest(messages, definitions));
    if (!isStaleDuplicate(response, history, currentPrompt)) {
      return response;
    }

    log.warn(
        "Agent returned a stale response; retrying without prompt cache, correlationId={}",
        correlationId);
    List<LlmMessage> retryMessages = new ArrayList<>(messages);
    retryMessages.add(LlmMessage.user(STALE_RESPONSE_CORRECTION.strip()));
    LlmResponse fresh = client.complete(LlmRequest.withoutPromptCache(retryMessages, definitions));
    if (isStaleDuplicate(fresh, history, currentPrompt)) {
      throw new AgentRoutingException("Agent returned a stale response after cache bypass");
    }
    return fresh;
  }

  private LlmResponse correctUnverifiedActionClaim(
      LlmResponse response,
      List<LlmMessage> messages,
      List<JsonObject> definitions,
      String correlationId)
      throws LlmException, AgentRoutingException {
    if (response.toolCalls().size() > 0 || !containsUnverifiedActionClaim(response.content())) {
      return response;
    }

    log.warn(
        "Agent narrated an unverified action; requesting a real tool call, correlationId={}",
        correlationId);
    messages.add(LlmMessage.assistant(response.content(), List.of()));
    messages.add(LlmMessage.user(UNVERIFIED_ACTION_CORRECTION.strip()));
    LlmResponse corrected = client.complete(new LlmRequest(messages, definitions));
    if (corrected.toolCalls().isEmpty() && containsUnverifiedActionClaim(corrected.content())) {
      log.warn(
          "Agent repeated an unverified action; requesting one final tool-only correction, correlationId={}",
          correlationId);
      messages.add(LlmMessage.assistant(corrected.content(), List.of()));
      messages.add(
          LlmMessage.user(
              "Final correction: do not describe or promise the action. Return the matching Saturn tool call now, or answer without claiming that any live lookup or command was performed."));
      corrected = client.complete(new LlmRequest(messages, definitions));
      if (corrected.toolCalls().isEmpty() && containsUnverifiedActionClaim(corrected.content())) {
        throw new AgentRoutingException("Agent repeated an unverified action claim");
      }
    }
    return corrected;
  }

  private static boolean containsUnverifiedActionClaim(String content) {
    if (content == null || content.isBlank()) {
      return false;
    }
    String normalized = content.toLowerCase(java.util.Locale.ROOT);
    return normalized.contains("[fetch")
        || normalized.contains("[exec")
        || normalized.contains("i will fetch")
        || normalized.contains("i will execute")
        || normalized.contains("i will query")
        || normalized.contains("i will search")
        || normalized.contains("i will check")
        || normalized.contains("i fetched")
        || normalized.contains("i executed")
        || normalized.contains("i queried")
        || normalized.contains("i searched")
        || normalized.contains("i checked");
  }

  private static boolean isStaleDuplicate(
      LlmResponse response, List<LlmMessage> history, String currentPrompt) {
    if (!response.toolCalls().isEmpty()
        || response.content() == null
        || response.content().isBlank()) {
      return false;
    }

    String previousUser = latestContent(history, "user").orElse(null);
    String previousAssistant = latestContent(history, "assistant").orElse(null);
    return previousUser != null
        && previousAssistant != null
        && !userAuthoredBody(currentPrompt).equals(userAuthoredBody(previousUser))
        && response.content().strip().equals(previousAssistant.strip());
  }

  private static String userAuthoredBody(String prompt) {
    int separator = prompt == null ? -1 : prompt.lastIndexOf("\n");
    return separator < 0 ? String.valueOf(prompt) : prompt.substring(separator + 1);
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

  private GuardedResponse enforceCommandChannel(
      LlmResponse response,
      List<LlmMessage> messages,
      List<JsonObject> definitions,
      AgentCommandProseGuard guard,
      boolean correctionUsed,
      Set<String> successfulCommands,
      Set<String> failedCommands,
      boolean toolsEnabled,
      String currentPrompt,
      String correlationId)
      throws LlmException, AgentRoutingException {
    Optional<String> command =
        response.toolCalls().isEmpty() ? guard.findCommand(response.content()) : Optional.empty();
    if (command.isEmpty()) {
      return new GuardedResponse(response, correctionUsed);
    }
    if (correctionUsed && !successfulCommands.contains(command.get())) {
      throw new AgentRoutingException("Agent emitted a Saturn command as prose after correction");
    }

    log.warn(
        "Blocked agent command prose, correlationId={}, command={}", correlationId, command.get());
    messages.add(LlmMessage.assistant(response.content(), List.of()));
    boolean commandAlreadySucceeded = successfulCommands.contains(command.get());
    boolean commandAlreadyFailed = failedCommands.contains(command.get());
    boolean requireToolCall = toolsEnabled && !commandAlreadySucceeded && !commandAlreadyFailed;
    messages.add(
        LlmMessage.user(
            requireToolCall
                ? COMMAND_TOOL_CORRECTION
                    .formatted(command.get(), command.get(), currentPrompt)
                    .strip()
                : commandAlreadySucceeded
                    ? COMMAND_OUTPUT_CORRECTION.strip()
                    : COMMAND_NOT_EXECUTED_CORRECTION.strip()));
    LlmResponse corrected =
        client.complete(
            new LlmRequest(
                messages, requireToolCall ? commandCorrectionDefinitions(definitions) : List.of()));

    if (requireToolCall) {
      corrected = resolveCommandCorrection(corrected, guard, command.get());
    }
    if (!requireToolCall
        && (!corrected.toolCalls().isEmpty()
            || guard.findCommand(corrected.content()).isPresent())) {
      throw new AgentRoutingException("Agent repeated a Saturn command after correction");
    }
    return new GuardedResponse(corrected, true);
  }

  private LlmResponse resolveCommandCorrection(
      LlmResponse corrected, AgentCommandProseGuard guard, String command)
      throws AgentRoutingException {
    if (corrected.toolCalls().size() != 1) {
      throw new AgentRoutingException(
          "Agent did not return exactly one required Saturn tool call or non-command correction");
    }

    var call = corrected.toolCalls().getFirst();
    if (guard.matches(call, command)) {
      return corrected;
    }
    if (!RESPOND_WITHOUT_COMMAND.equals(call.name())) {
      throw new AgentRoutingException(
          "Agent did not return exactly one required Saturn tool call or non-command correction");
    }

    String content = responseWithoutCommand(call.arguments());
    if (content.isBlank() || guard.findCommand(content).isPresent()) {
      throw new AgentRoutingException("Agent returned an invalid non-command correction");
    }
    return new LlmResponse(content, List.of(), "stop");
  }

  private static List<JsonObject> commandCorrectionDefinitions(List<JsonObject> definitions) {
    List<JsonObject> correctionDefinitions = new ArrayList<>();
    definitions.stream()
        .filter(DefaultAgentRouter::isRunCommandDefinition)
        .map(JsonObject::deepCopy)
        .forEach(correctionDefinitions::add);
    correctionDefinitions.add(responseWithoutCommandDefinition());
    return List.copyOf(correctionDefinitions);
  }

  private static boolean isRunCommandDefinition(JsonObject definition) {
    JsonObject function = definition.getAsJsonObject("function");
    return function != null
        && function.has("name")
        && "run_command".equals(function.get("name").getAsString());
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

  private static LlmResponse requireFreshToolCall(
      LlmResponse response, String toolName, Optional<String> expectedNick)
      throws AgentRoutingException {
    if (!callsExactly(response, toolName, expectedNick)) {
      throw new AgentRoutingException(
          "Agent did not call exactly the required fresh-data tool: " + toolName);
    }
    return response;
  }

  private static boolean callsExactly(
      LlmResponse response, String toolName, Optional<String> expectedNick) {
    return response.toolCalls().size() == 1
        && toolName.equals(response.toolCalls().getFirst().name())
        && matchesFreshTarget(response.toolCalls().getFirst(), expectedNick);
  }

  private static boolean matchesFreshTarget(LlmToolCall call, Optional<String> expectedNick) {
    if (expectedNick.isEmpty() || !"user_message_history".equals(call.name())) {
      return true;
    }
    try {
      JsonObject arguments = JsonParser.parseString(call.arguments()).getAsJsonObject();
      JsonElement nick = arguments.get("nick");
      return nick != null
          && nick.isJsonPrimitive()
          && expectedNick.get().equalsIgnoreCase(nick.getAsString().trim());
    } catch (JsonParseException | IllegalStateException exception) {
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

  private static boolean satisfiesFreshProfileContract(
      LlmResponse response,
      Optional<String> expectedNick,
      List<AgentToolResult> successfulToolResults) {
    Optional<AgentToolResult> historyResult =
        successfulToolResults.stream()
            .filter(result -> "user_message_history".equals(result.toolName()))
            .reduce((first, second) -> second);
    if (historyResult.isEmpty() || response.content() == null || response.content().isBlank()) {
      return false;
    }
    // The evidence is supplied to the model as a tool result. Requiring users' exact
    // timestamps and row counts in a natural-language answer made valid summaries fail.
    return true;
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

  private LlmResponse correctFailurePlaceholder(
      LlmResponse response, List<LlmMessage> messages, String correlationId)
      throws LlmException, AgentRoutingException {
    if (!isFailurePlaceholder(response)) {
      return response;
    }

    log.warn(
        "Agent returned a failure placeholder; requesting grounded synthesis, correlationId={}",
        correlationId);
    List<LlmMessage> correctionMessages = new ArrayList<>(messages);
    correctionMessages.add(LlmMessage.assistant(response.content(), List.of()));
    correctionMessages.add(LlmMessage.user(FAILURE_PLACEHOLDER_CORRECTION));
    LlmResponse corrected =
        client.complete(LlmRequest.withoutPromptCache(correctionMessages, List.of()));
    if (!corrected.toolCalls().isEmpty() || isFailurePlaceholder(corrected)) {
      throw new AgentRoutingException("Agent repeated a failure placeholder after correction");
    }
    return corrected;
  }

  private static boolean isFailurePlaceholder(LlmResponse response) {
    if (!response.toolCalls().isEmpty() || response.content() == null) {
      return false;
    }
    String normalized = response.content().strip().toLowerCase(Locale.ROOT);
    return normalized.equals("the agent could not answer that request.")
        || normalized.equals("the agent could not answer that request")
        || normalized.equals("i could not answer that request.")
        || normalized.equals("i could not answer that request");
  }

  private static boolean isNamedToolDefinition(JsonObject definition, String toolName) {
    JsonObject function = definition.getAsJsonObject("function");
    return function != null
        && function.has("name")
        && toolName.equals(function.get("name").getAsString());
  }

  private static JsonObject responseWithoutCommandDefinition() {
    JsonObject response = new JsonObject();
    response.addProperty("type", "string");

    JsonObject properties = new JsonObject();
    properties.add("response", response);

    var required = new com.google.gson.JsonArray();
    required.add("response");

    JsonObject parameters = new JsonObject();
    parameters.addProperty("type", "object");
    parameters.add("properties", properties);
    parameters.add("required", required);
    parameters.addProperty("additionalProperties", false);

    JsonObject function = new JsonObject();
    function.addProperty("name", RESPOND_WITHOUT_COMMAND);
    function.addProperty("description", PROMPTS.text("router-non-command-correction.txt").strip());
    function.add("parameters", parameters);

    JsonObject definition = new JsonObject();
    definition.addProperty("type", "function");
    definition.add("function", function);
    return definition;
  }

  private static String responseWithoutCommand(String arguments) throws AgentRoutingException {
    try {
      JsonObject parsed = JsonParser.parseString(arguments).getAsJsonObject();
      if (!parsed.keySet().equals(Set.of("response"))) {
        throw new AgentRoutingException("Agent returned an invalid non-command correction");
      }
      JsonElement response = parsed.get("response");
      if (response == null
          || !response.isJsonPrimitive()
          || !response.getAsJsonPrimitive().isString()) {
        throw new AgentRoutingException("Agent returned an invalid non-command correction");
      }
      return response.getAsString().strip();
    } catch (JsonParseException | IllegalStateException | NullPointerException exception) {
      throw new AgentRoutingException(
          "Agent returned an invalid non-command correction", exception);
    }
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

  private record GuardedResponse(LlmResponse response, boolean correctionUsed) {}
}
