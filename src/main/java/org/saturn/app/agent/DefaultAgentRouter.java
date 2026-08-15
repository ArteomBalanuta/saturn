package org.saturn.app.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
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
public final class DefaultAgentRouter implements AgentRouter {
  private static final String FINALIZE_PROMPT =
      "Answer the user's request using the tool results already provided. Do not call tools.";
  private static final String RESPOND_WITHOUT_COMMAND = "respond_without_command";
  private static final String COMMAND_TOOL_CORRECTION =
      """
      Your previous response placed the Saturn %s command in Markdown. Return exactly one tool call.
      Use run_command with command %s only when the user requested that command to execute now. Use
      respond_without_command when it was a reference, report, example, conditional, or future
      action; provide a clean response that does not print a command or claim an action occurred. Do
      not call another tool, ask for confirmation, or answer outside the tool call.
      """;
  private static final String COMMAND_OUTPUT_CORRECTION =
      """
      The Saturn command already executed. Answer briefly from its tool result without printing,
      quoting, or fencing any Saturn command. Do not call another tool.
      """;
  private static final String COMMAND_NOT_EXECUTED_CORRECTION =
      """
      The Saturn command did not execute, and tools are unavailable for this turn. State that
      outcome briefly without printing, quoting, or fencing any Saturn command. Do not claim the
      action happened and do not call another tool.
      """;
  private static final String STALE_RESPONSE_CORRECTION =
      """
      Your previous completion duplicated an earlier assistant answer. Ignore that attempt and
      answer the newest user message above. Do not repeat an older answer unless the newest request
      explicitly asks for it.
      """;
  private static final String UNVERIFIED_ACTION_CORRECTION =
      """
      You narrated an action but did not call a tool. Re-evaluate the newest user request now. If it
      requires live Saturn data or a Saturn command, return the matching tool call immediately. If
      no exposed tool applies, answer honestly without claiming that you fetched, checked, queried,
      searched, or executed anything.
      """;

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
    AgentCommandProseGuard commandProseGuard = AgentCommandProseGuard.from(definitions);
    AgentToolExecutor toolExecutor = new AgentToolExecutor(registry, config);
    try {
      LlmResponse response =
          completeInitialRequest(
              messages, definitions, history, contextualizedPrompt, correlationId);
      response = correctUnverifiedActionClaim(response, messages, definitions, correlationId);
      int totalCalls = 0;
      boolean correctionUsed = false;
      Set<String> successfulCommands = new HashSet<>();
      boolean toolsEnabled = true;
      while (true) {
        GuardedResponse guarded =
            enforceCommandChannel(
                response,
                messages,
                definitions,
                commandProseGuard,
                correctionUsed,
                successfulCommands,
                toolsEnabled,
                correlationId);
        response = guarded.response();
        correctionUsed = guarded.correctionUsed();

        if (response.toolCalls().isEmpty()) {
          break;
        }
        if (!toolsEnabled) {
          throw new AgentRoutingException("Agent returned a tool call after tools were disabled");
        }
        if (totalCalls + response.toolCalls().size() > config.maxToolCalls()) {
          toolsEnabled = false;
          response = finalizeResponse(messages);
          continue;
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
          if (!result.isError() && "run_command".equals(call.name())) {
            commandProseGuard.executedCommand(call).ifPresent(successfulCommands::add);
          }
          messages.add(LlmMessage.tool(call.id(), modelVisibleToolResult(context, call, result)));
        }
        if (allErrors) {
          toolsEnabled = false;
          response = finalizeResponse(messages);
          continue;
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

  private String modelVisibleToolResult(
      AgentContext context, org.saturn.app.agent.llm.LlmToolCall call, AgentToolResult result) {
    if (result.isError()) {
      return result.content();
    }
    return registry
        .find(context, call.name())
        .map(tool -> tool.descriptor(context).resultMode())
        .filter(mode -> mode == ToolResultMode.ROOM_DELIVERY)
        .map(mode -> "Tool output was delivered to the room. Do not repeat or paraphrase it.")
        .orElse(result.content());
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
    LlmResponse fresh =
        client.complete(LlmRequest.withoutPromptCache(retryMessages, definitions));
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
    if (containsUnverifiedActionClaim(corrected.content())) {
      throw new AgentRoutingException("Agent repeated an unverified action claim");
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
    if (!response.toolCalls().isEmpty() || response.content() == null || response.content().isBlank()) {
      return false;
    }

    String previousUser = latestContent(history, "user").orElse(null);
    String previousAssistant = latestContent(history, "assistant").orElse(null);
    return previousUser != null
        && previousAssistant != null
        && !currentPrompt.equals(previousUser)
        && response.content().strip().equals(previousAssistant.strip());
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
      boolean toolsEnabled,
      String correlationId)
      throws LlmException, AgentRoutingException {
    Optional<String> command =
        response.toolCalls().isEmpty() ? guard.findCommand(response.content()) : Optional.empty();
    if (command.isEmpty()) {
      return new GuardedResponse(response, correctionUsed);
    }
    if (correctionUsed) {
      throw new AgentRoutingException("Agent emitted a Saturn command as prose after correction");
    }

    log.warn(
        "Blocked agent command prose, correlationId={}, command={}", correlationId, command.get());
    messages.add(LlmMessage.assistant(response.content(), List.of()));
    boolean commandAlreadySucceeded = successfulCommands.contains(command.get());
    boolean requireToolCall = toolsEnabled && !commandAlreadySucceeded;
    messages.add(
        LlmMessage.user(
            requireToolCall
                ? COMMAND_TOOL_CORRECTION.formatted(command.get(), command.get()).strip()
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
    function.addProperty(
        "description",
        "Return a clean response when a Markdown command was only referenced and must not run.");
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

  private void persist(AgentContext context, String user, String assistant, String correlationId)
      throws AgentRoutingException {
    try {
      memory.append(context, user, assistant, config);
      log.info("Agent memory persisted, correlationId={}", correlationId);
    } catch (RuntimeException exception) {
      log.warn(
          "Agent memory append failed, correlationId={}: {}",
          correlationId,
          exception.getMessage());
      log.debug("Agent memory append failure, correlationId={}", correlationId, exception);
      throw new AgentRoutingException("Agent memory persistence failed", exception);
    }
  }

  private List<LlmMessage> loadMemory(AgentContext context, String correlationId)
      throws AgentRoutingException {
    try {
      List<LlmMessage> history = memory.load(context, config);
      log.info("Agent memory loaded, correlationId={}, messages={}", correlationId, history.size());
      return history;
    } catch (RuntimeException exception) {
      log.warn(
          "Agent memory load failed, correlationId={}: {}", correlationId, exception.getMessage());
      log.debug("Agent memory load failure, correlationId={}", correlationId, exception);
      throw new AgentRoutingException("Agent memory load failed", exception);
    }
  }

  private String loadConversationContext(AgentInvocation invocation, String correlationId) {
    if (invocation.context().whisper()) {
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

  private record GuardedResponse(LlmResponse response, boolean correctionUsed) {}
}
