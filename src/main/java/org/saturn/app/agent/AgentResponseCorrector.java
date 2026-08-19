package org.saturn.app.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.agent.llm.LlmClient;
import org.saturn.app.agent.llm.LlmException;
import org.saturn.app.agent.llm.LlmMessage;
import org.saturn.app.agent.llm.LlmRequest;
import org.saturn.app.agent.llm.LlmResponse;
import org.saturn.app.agent.llm.UnsupportedResponseFormatException;

/**
 * Applies bounded recovery policies to invalid model responses.
 *
 * <p>Each policy either returns an acceptable response or raises a routing error. This keeps the
 * router focused on turn orchestration instead of correction prompt details.
 */
@Slf4j
final class AgentResponseCorrector {
  private static final AgentPromptCatalog PROMPTS = new AgentPromptCatalog();
  private static final VerifiedQuoteCatalog VERIFIED_QUOTES = new VerifiedQuoteCatalog();
  private static final String FAILURE_PLACEHOLDER_CORRECTION =
      PROMPTS.text("router-failure-placeholder-correction.txt").strip();
  private static final String INTERNAL_EVIDENCE_CORRECTION =
      PROMPTS.text("router-internal-evidence-correction.txt").strip();
  private static final String QUOTE_ONLY_CORRECTION = quoteOnlyCorrectionPrompt();
  private static final JsonObject QUOTE_ONLY_RESPONSE_FORMAT = quoteOnlyResponseFormat();
  private static final String UNVERIFIED_ACTION_CORRECTION =
      PROMPTS.text("router-unverified-action-correction.txt");
  private static final String UNVERIFIED_ACTION_FINAL_CORRECTION =
      PROMPTS.text("router-unverified-action-final-correction.txt");
  private static final String UNAVAILABLE_ACTION_RESPONSE =
      PROMPTS.text("router-unavailable-action-response.txt").strip();
  private static final String STALE_RESPONSE_CORRECTION =
      PROMPTS.text("router-stale-response-correction.txt");

  private final LlmClient client;

  AgentResponseCorrector(LlmClient client) {
    this.client = client;
  }

  /** Completes the initial request and retries one detected stale cached response. */
  LlmResponse completeInitialRequest(
      List<LlmMessage> messages,
      List<com.google.gson.JsonObject> definitions,
      List<LlmMessage> history,
      String currentPrompt,
      String correlationId)
      throws LlmException, AgentRoutingException {
    LlmResponse response = requireResponse(client.complete(new LlmRequest(messages, definitions)));
    if (!isStaleDuplicate(response, history, currentPrompt)) {
      return response;
    }

    log.warn(
        "Agent returned a stale response; retrying without prompt cache, correlationId={}",
        correlationId);
    List<LlmMessage> retryMessages =
        isolatedCorrectionMessages(messages, STALE_RESPONSE_CORRECTION);
    LlmResponse fresh =
        requireResponse(client.complete(LlmRequest.withoutPromptCache(retryMessages, definitions)));
    if (isStaleDuplicate(fresh, history, currentPrompt)) {
      throw new AgentRoutingException("Agent returned a stale response after cache bypass");
    }
    return fresh;
  }

  LlmResponse correctQuoteOnly(
      LlmResponse response, List<LlmMessage> messages, String correlationId)
      throws LlmException, AgentRoutingException {
    boolean structurallyQuote = isQuoteOnly(response.content());
    java.util.Optional<VerifiedQuoteCatalog.Entry> verified =
        VERIFIED_QUOTES.find(response.content());
    if (verified.isPresent()) {
      return verifiedResponse(response, verified.get());
    }
    log.warn(
        "Agent returned an unverified quote; requesting an exact catalog entry, correlationId={}",
        correlationId);
    List<LlmMessage> correctionMessages =
        isolatedCorrectionMessages(messages, QUOTE_ONLY_CORRECTION);
    LlmResponse corrected;
    try {
      corrected =
          requireResponse(
              client.complete(
                  new LlmRequest(correctionMessages, List.of(), true, QUOTE_ONLY_RESPONSE_FORMAT)));
      corrected = parseStructuredQuote(corrected);
    } catch (UnsupportedResponseFormatException exception) {
      log.warn(
          "Structured quote correction unsupported; falling back to textual correction, correlationId={}",
          correlationId);
      corrected =
          requireResponse(
              client.complete(LlmRequest.withoutPromptCache(correctionMessages, List.of())));
    } catch (AgentRoutingException exception) {
      logInvalidQuoteCorrection(correlationId, response.content());
      if (structurallyQuote) {
        return fallbackQuote();
      }
      throw exception;
    }
    verified = VERIFIED_QUOTES.find(corrected.content());
    if (verified.isPresent()) {
      return verifiedResponse(corrected, verified.get());
    }
    logInvalidQuoteCorrection(correlationId, corrected.content());
    if (!isQuoteOnly(corrected.content())) {
      throw new AgentRoutingException("Agent returned a non-quote prose response after correction");
    }
    return fallbackQuote();
  }

  private static String quoteOnlyCorrectionPrompt() {
    return PROMPTS.text("router-quote-only-correction.txt").strip()
        + "\\n\\nVerified catalog entries (return one exact line; do not edit it):\\n"
        + VERIFIED_QUOTES.promptEntries();
  }

  private static LlmResponse verifiedResponse(
      LlmResponse response, VerifiedQuoteCatalog.Entry entry) {
    return new LlmResponse(entry.line(), response.toolCalls(), response.finishReason());
  }

  private static LlmResponse fallbackQuote() {
    return new LlmResponse(VERIFIED_QUOTES.fallback().line(), List.of(), "stop");
  }

  private static LlmResponse parseStructuredQuote(LlmResponse response)
      throws AgentRoutingException {
    if (isQuoteOnly(response.content())) {
      return response;
    }
    try {
      JsonElement parsed = JsonParser.parseString(response.content());
      if (!parsed.isJsonObject()) {
        throw new IllegalStateException("not an object");
      }
      JsonObject object = parsed.getAsJsonObject();
      if (object.size() != 1 || !object.has("line")) {
        throw new IllegalStateException("unexpected fields");
      }
      JsonElement line = object.get("line");
      if (!line.isJsonPrimitive()
          || !line.getAsJsonPrimitive().isString()
          || line.getAsString().isBlank()) {
        throw new IllegalStateException("invalid line");
      }
      return new LlmResponse(line.getAsString(), response.toolCalls(), response.finishReason());
    } catch (RuntimeException exception) {
      throw new AgentRoutingException("Agent returned malformed structured quote correction");
    }
  }

  private static JsonObject quoteOnlyResponseFormat() {
    JsonObject line = new JsonObject();
    line.addProperty("type", "string");
    line.addProperty("minLength", 1);
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    JsonObject properties = new JsonObject();
    properties.add("line", line);
    schema.add("properties", properties);
    schema.add("required", new com.google.gson.JsonArray());
    schema.getAsJsonArray("required").add("line");
    schema.addProperty("additionalProperties", false);
    JsonObject jsonSchema = new JsonObject();
    jsonSchema.addProperty("name", "quote_only_response");
    jsonSchema.addProperty("strict", true);
    jsonSchema.add("schema", schema);
    JsonObject format = new JsonObject();
    format.addProperty("type", "json_schema");
    format.add("json_schema", jsonSchema);
    return format;
  }

  private static void logInvalidQuoteCorrection(String correlationId, String content) {
    log.warn(
        "Quote correction failed validation, correlationId={}, responseMode=quote-only, contentLength={}, contentHash={}",
        correlationId,
        content == null ? 0 : content.length(),
        shortHash(content));
  }

  private static String shortHash(String content) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(String.valueOf(content).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (int index = 0; index < 8; index++) {
        result.append(String.format("%02x", digest[index]));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      return "unavailable";
    }
  }

  static boolean isQuoteOnly(String content) {
    if (content == null) {
      return false;
    }
    String normalized = content.strip();
    return normalized.matches("\\\"[^\\r\\n\\\"]+\\\" — [^\\r\\n]+, [^\\r\\n]+")
        && !normalized.contains("\\n")
        && !normalized.contains("\\r");
  }

  private static List<LlmMessage> isolatedCorrectionMessages(
      List<LlmMessage> messages, String contract) throws AgentRoutingException {
    LlmMessage newestUserMessage =
        messages.stream()
            .filter(message -> "user".equals(message.role()))
            .reduce((first, second) -> second)
            .orElse(null);
    if (newestUserMessage == null) {
      return List.of(LlmMessage.system(contract.strip()));
    }
    return List.of(
        LlmMessage.system(contract.strip()), LlmMessage.user(newestUserMessage.content()));
  }

  LlmResponse correctUnverifiedActionClaim(
      LlmResponse response,
      List<LlmMessage> messages,
      List<com.google.gson.JsonObject> definitions,
      String correlationId)
      throws LlmException, AgentRoutingException {
    if (!response.toolCalls().isEmpty() || !containsUnverifiedActionClaim(response.content())) {
      return response;
    }

    log.warn(
        "Agent narrated an unverified action; requesting a real tool call, correlationId={}",
        correlationId);
    messages.add(LlmMessage.assistant(response.content(), List.of()));
    messages.add(LlmMessage.user(UNVERIFIED_ACTION_CORRECTION.strip()));
    LlmResponse corrected = requireResponse(client.complete(new LlmRequest(messages, definitions)));
    if (corrected.toolCalls().isEmpty() && containsUnverifiedActionClaim(corrected.content())) {
      log.warn(
          "Agent repeated an unverified action; requesting one final tool-only correction, correlationId={}",
          correlationId);
      messages.add(LlmMessage.assistant(corrected.content(), List.of()));
      messages.add(LlmMessage.user(UNVERIFIED_ACTION_FINAL_CORRECTION));
      corrected = requireResponse(client.complete(new LlmRequest(messages, definitions)));
      if (corrected.toolCalls().isEmpty() && containsUnverifiedActionClaim(corrected.content())) {
        log.warn(
            "Agent repeated an unverified action after corrections; returning capability limitation, correlationId={}",
            correlationId);
        return new LlmResponse(UNAVAILABLE_ACTION_RESPONSE, List.of(), "stop");
      }
    }
    return corrected;
  }

  LlmResponse correctFailurePlaceholder(
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
        requireResponse(
            client.complete(LlmRequest.withoutPromptCache(correctionMessages, List.of())));
    if (!corrected.toolCalls().isEmpty() || isFailurePlaceholder(corrected)) {
      throw new AgentRoutingException("Agent repeated a failure placeholder after correction");
    }
    return corrected;
  }

  LlmResponse correctInternalEvidenceLeak(
      LlmResponse response, List<LlmMessage> messages, String correlationId)
      throws LlmException, AgentRoutingException {
    if (!containsInternalToolEvidence(response)) {
      return response;
    }

    log.warn(
        "Agent exposed internal tool evidence; requesting a user-facing answer, correlationId={}",
        correlationId);
    List<LlmMessage> correctionMessages = new ArrayList<>(messages);
    correctionMessages.add(LlmMessage.assistant(response.content(), List.of()));
    correctionMessages.add(LlmMessage.user(INTERNAL_EVIDENCE_CORRECTION));
    LlmResponse corrected =
        requireResponse(
            client.complete(LlmRequest.withoutPromptCache(correctionMessages, List.of())));
    if (!corrected.toolCalls().isEmpty() || containsInternalToolEvidence(corrected)) {
      throw new AgentRoutingException("Agent repeated internal tool evidence after correction");
    }
    return corrected;
  }

  static LlmResponse requireResponse(LlmResponse response) throws AgentRoutingException {
    if (response == null) {
      throw new AgentRoutingException("Agent returned no response");
    }
    return response;
  }

  static boolean isFailurePlaceholder(LlmResponse response) {
    if (response == null || !response.toolCalls().isEmpty() || response.content() == null) {
      return false;
    }
    String normalized = response.content().strip().toLowerCase(Locale.ROOT);
    return normalized.equals("the agent could not answer that request.")
        || normalized.equals("the agent could not answer that request")
        || normalized.equals("i could not answer that request.")
        || normalized.equals("i could not answer that request");
  }

  private static boolean containsInternalToolEvidence(LlmResponse response) {
    return response.toolCalls().isEmpty()
        && response.content() != null
        && response.content().contains("[Internal tool evidence from ");
  }

  private static boolean isStaleDuplicate(
      LlmResponse response, List<LlmMessage> history, String currentPrompt) {
    if (!response.toolCalls().isEmpty()
        || response.content() == null
        || response.content().isBlank()) {
      return false;
    }

    String previousUser = AgentMessageHistory.latestContent(history, "user").orElse(null);
    String previousAssistant =
        AgentMessageHistory.latestConversationAssistant(history).orElse(null);
    return previousUser != null
        && previousAssistant != null
        && response.content().strip().equals(previousAssistant.strip())
        && (!userAuthoredBody(currentPrompt).equals(userAuthoredBody(previousUser))
            || isGenericAcknowledgement(response.content()));
  }

  private static boolean isGenericAcknowledgement(String content) {
    String normalized = content.strip().toLowerCase(Locale.ROOT);
    return normalized.equals("i am ready")
        || normalized.equals("i am ready.")
        || normalized.equals("ready")
        || normalized.equals("ready.");
  }

  private static String userAuthoredBody(String prompt) {
    int separator = prompt == null ? -1 : prompt.lastIndexOf("\n");
    return separator < 0 ? String.valueOf(prompt) : prompt.substring(separator + 1);
  }

  private static boolean containsUnverifiedActionClaim(String content) {
    if (content == null || content.isBlank()) {
      return false;
    }
    String normalized = content.toLowerCase(Locale.ROOT);
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
}
