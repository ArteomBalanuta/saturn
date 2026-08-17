package org.saturn.app.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.agent.llm.LlmClient;
import org.saturn.app.agent.llm.LlmException;
import org.saturn.app.agent.llm.LlmMessage;
import org.saturn.app.agent.llm.LlmRequest;
import org.saturn.app.agent.llm.LlmResponse;

/** Enforces structured tool calls when a model renders a Saturn command as visible prose. */
@Slf4j
final class AgentCommandChannelPolicy implements AgentTurnPolicy {
  private static final AgentPromptCatalog PROMPTS = new AgentPromptCatalog();
  private static final String RESPOND_WITHOUT_COMMAND = "respond_without_command";
  private static final String TOOL_CORRECTION = PROMPTS.text("router-command-tool-correction.txt");
  private static final String OUTPUT_CORRECTION =
      PROMPTS.text("router-command-output-correction.txt");
  private static final String NOT_EXECUTED_CORRECTION =
      PROMPTS.text("router-command-not-executed-correction.txt");
  private final LlmClient client;

  AgentCommandChannelPolicy(LlmClient client) {
    this.client = client;
  }

  @Override
  public AgentTurnPolicyResult apply(AgentTurnPolicyInput input)
      throws LlmException, AgentRoutingException {
    Result result =
        enforce(
            input.response(),
            input.messages(),
            input.definitions(),
            input.commandProseGuard(),
            input.turnState(),
            input.prompt(),
            input.correlationId());
    return new AgentTurnPolicyResult(result.response(), result.correctionUsed());
  }

  Result enforce(
      LlmResponse response,
      List<LlmMessage> messages,
      List<JsonObject> definitions,
      AgentCommandProseGuard guard,
      AgentTurnState state,
      String prompt,
      String correlationId)
      throws LlmException, AgentRoutingException {
    Optional<String> command =
        response.toolCalls().isEmpty() ? guard.findCommand(response.content()) : Optional.empty();
    if (command.isEmpty()) return new Result(response, state.commandCorrectionUsed());
    String name = command.orElseThrow();
    if (state.commandCorrectionUsed() && !state.hasSuccessfulCommand(name))
      throw new AgentRoutingException("Agent emitted a Saturn command as prose after correction");
    boolean succeeded = state.hasSuccessfulCommand(name);
    boolean failed = state.failedCommands().contains(name);
    boolean requireTool = state.toolsEnabled() && !succeeded && !failed;
    log.warn("Blocked agent command prose, correlationId={}, command={}", correlationId, name);
    messages.add(LlmMessage.assistant(response.content(), List.of()));
    messages.add(
        LlmMessage.user(
            (requireTool
                    ? TOOL_CORRECTION.formatted(name, name, prompt)
                    : succeeded ? OUTPUT_CORRECTION : NOT_EXECUTED_CORRECTION)
                .strip()));
    LlmResponse corrected =
        AgentResponseCorrector.requireResponse(
            client.complete(
                new LlmRequest(
                    messages, requireTool ? correctionDefinitions(definitions) : List.of())));
    if (requireTool) corrected = resolve(corrected, guard, name);
    if (!requireTool
        && (!corrected.toolCalls().isEmpty() || guard.findCommand(corrected.content()).isPresent()))
      throw new AgentRoutingException("Agent repeated a Saturn command after correction");
    return new Result(corrected, true);
  }

  private static LlmResponse resolve(
      LlmResponse response, AgentCommandProseGuard guard, String command)
      throws AgentRoutingException {
    if (response.toolCalls().size() != 1) throw invalid();
    var call = response.toolCalls().getFirst();
    if (guard.matches(call, command)) return response;
    if (!RESPOND_WITHOUT_COMMAND.equals(call.name())) throw invalid();
    String content = responseWithoutCommand(call.arguments());
    if (content.isBlank() || guard.findCommand(content).isPresent())
      throw new AgentRoutingException("Agent returned an invalid non-command correction");
    return new LlmResponse(content, List.of(), "stop");
  }

  private static AgentRoutingException invalid() {
    return new AgentRoutingException(
        "Agent did not return exactly one required Saturn tool call or non-command correction");
  }

  private static List<JsonObject> correctionDefinitions(List<JsonObject> definitions) {
    List<JsonObject> values = new ArrayList<>();
    definitions.stream()
        .filter(AgentCommandChannelPolicy::isRunCommand)
        .map(JsonObject::deepCopy)
        .forEach(values::add);
    values.add(responseWithoutCommandDefinition());
    return List.copyOf(values);
  }

  private static boolean isRunCommand(JsonObject definition) {
    return AgentToolDefinitionJson.functionName(definition)
        .filter("run_command"::equals)
        .isPresent();
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
      if (!parsed.keySet().equals(Set.of("response"))) throw invalidResponse();
      JsonElement response = parsed.get("response");
      if (response == null
          || !response.isJsonPrimitive()
          || !response.getAsJsonPrimitive().isString()) throw invalidResponse();
      return response.getAsString().strip();
    } catch (JsonParseException | IllegalStateException | NullPointerException exception) {
      throw new AgentRoutingException(
          "Agent returned an invalid non-command correction", exception);
    }
  }

  private static AgentRoutingException invalidResponse() {
    return new AgentRoutingException("Agent returned an invalid non-command correction");
  }

  record Result(LlmResponse response, boolean correctionUsed) {}
}
