package org.saturn.app.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.agent.llm.LlmClient;
import org.saturn.app.agent.llm.LlmException;
import org.saturn.app.agent.llm.LlmMessage;
import org.saturn.app.agent.llm.LlmRequest;
import org.saturn.app.agent.llm.LlmResponse;

/**
 * Applies bounded recovery policies to invalid model responses.
 *
 * <p>Each policy either returns an acceptable response or raises a routing error. This keeps the
 * router focused on turn orchestration instead of correction prompt details.
 */
@Slf4j
final class AgentResponseCorrector {
  private static final AgentPromptCatalog PROMPTS = new AgentPromptCatalog();
  private static final String FAILURE_PLACEHOLDER_CORRECTION =
      PROMPTS.text("router-failure-placeholder-correction.txt").strip();
  private static final String INTERNAL_EVIDENCE_CORRECTION =
      PROMPTS.text("router-internal-evidence-correction.txt").strip();
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

  LlmResponse correctUnverifiedActionClaim(
      LlmResponse response,
      List<LlmMessage> messages,
      List<com.google.gson.JsonObject> definitions,
      String correlationId)
      throws LlmException {
    if (!response.toolCalls().isEmpty() || !containsUnverifiedActionClaim(response.content())) {
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
      messages.add(LlmMessage.user(UNVERIFIED_ACTION_FINAL_CORRECTION));
      corrected = client.complete(new LlmRequest(messages, definitions));
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
        client.complete(LlmRequest.withoutPromptCache(correctionMessages, List.of()));
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
        client.complete(LlmRequest.withoutPromptCache(correctionMessages, List.of()));
    if (!corrected.toolCalls().isEmpty() || containsInternalToolEvidence(corrected)) {
      throw new AgentRoutingException("Agent repeated internal tool evidence after correction");
    }
    return corrected;
  }

  static boolean isFailurePlaceholder(LlmResponse response) {
    if (!response.toolCalls().isEmpty() || response.content() == null) {
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
