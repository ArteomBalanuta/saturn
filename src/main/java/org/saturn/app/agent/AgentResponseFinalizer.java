package org.saturn.app.agent;

import java.util.List;
import java.util.Optional;
import org.saturn.app.agent.llm.LlmException;
import org.saturn.app.agent.llm.LlmMessage;
import org.saturn.app.agent.llm.LlmResponse;

/** Owns final model-response correction, validation, sanitization, and reply-mode decisions. */
final class AgentResponseFinalizer {
  private final AgentResponseCorrector responseCorrector;
  private final AgentFreshDataCoordinator freshDataCoordinator;
  private final AgentParticipationConfig participationConfig;
  private final int maxOutputChars;
  private final AgentResponseSanitizer responseSanitizer = new AgentResponseSanitizer();

  AgentResponseFinalizer(
      AgentResponseCorrector responseCorrector,
      AgentFreshDataCoordinator freshDataCoordinator,
      AgentParticipationConfig participationConfig,
      int maxOutputChars) {
    this.responseCorrector = responseCorrector;
    this.freshDataCoordinator = freshDataCoordinator;
    this.participationConfig = participationConfig;
    this.maxOutputChars = maxOutputChars;
  }

  Result prepare(
      AgentInvocation invocation,
      LlmResponse response,
      List<LlmMessage> messages,
      Optional<String> requiredFreshTool,
      List<AgentToolResult> successfulToolResults,
      String correlationId)
      throws LlmException, AgentRoutingException {
    if (response == null) {
      throw new AgentRoutingException("Agent returned no response");
    }
    response = responseCorrector.correctFailurePlaceholder(response, messages, correlationId);
    response = responseCorrector.correctInternalEvidenceLeak(response, messages, correlationId);
    freshDataCoordinator.validateFinal(requiredFreshTool, response, successfulToolResults);
    String sanitizedContent = responseSanitizer.sanitize(response.content());
    if (invocation.mode() == AgentInvocationMode.MODERATION) {
      return Result.silent();
    }
    if (sanitizedContent.strip().equals(participationConfig.noReplyMarker())) {
      if (!invocation.mode().requiresReply()) {
        return Result.silent();
      }
      throw new AgentRoutingException("Agent declined a required response");
    }
    String content =
        AgentTextBounds.truncate(removeNoReplyMarker(sanitizedContent), maxOutputChars);
    if (content.isBlank()) {
      throw new AgentRoutingException("Agent returned an empty response");
    }
    return new Result(content, true);
  }

  private String removeNoReplyMarker(String content) {
    String marker = participationConfig.noReplyMarker();
    if (!content.contains(marker)) {
      return content;
    }
    return trimControlWhitespace(content.replace(marker, ""));
  }

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

  record Result(String content, boolean shouldReply) {
    static Result silent() {
      return new Result("", false);
    }
  }
}
