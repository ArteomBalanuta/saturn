package org.saturn.app.agent.routing;

import java.util.List;
import java.util.Optional;
import org.saturn.app.agent.api.AgentInvocation;
import org.saturn.app.agent.api.AgentInvocationMode;
import org.saturn.app.agent.api.AgentParticipationConfig;
import org.saturn.app.agent.api.AgentRoutingException;
import org.saturn.app.agent.api.AgentToolResult;
import org.saturn.app.agent.llm.LlmException;
import org.saturn.app.agent.llm.LlmMessage;
import org.saturn.app.agent.llm.LlmResponse;
import org.saturn.app.agent.turn.AgentFreshDataFinalValidator;
import org.saturn.app.agent.turn.AgentToolEvidence;

/** Owns final model-response correction, validation, sanitization, and reply-mode decisions. */
final class AgentResponseFinalizer {
  private final AgentResponseCorrector responseCorrector;
  private final AgentFreshDataFinalValidator freshDataFinalValidator;
  private final AgentParticipationConfig participationConfig;
  private final int maxOutputChars;
  private final AgentResponseSanitizer responseSanitizer = new AgentResponseSanitizer();

  AgentResponseFinalizer(
      AgentResponseCorrector responseCorrector,
      AgentFreshDataFinalValidator freshDataFinalValidator,
      AgentParticipationConfig participationConfig,
      int maxOutputChars) {
    this.responseCorrector = responseCorrector;
    this.freshDataFinalValidator = freshDataFinalValidator;
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
    return prepare(
        invocation,
        response,
        messages,
        requiredFreshTool,
        successfulToolResults,
        correlationId,
        invocation.mode() != AgentInvocationMode.MODERATION);
  }

  Result prepare(
      AgentInvocation invocation,
      LlmResponse response,
      List<LlmMessage> messages,
      Optional<String> requiredFreshTool,
      List<AgentToolResult> successfulToolResults,
      String correlationId,
      boolean quoteOnlyRequired)
      throws LlmException, AgentRoutingException {
    return prepare(
        invocation,
        response,
        messages,
        requiredFreshTool,
        successfulToolResults,
        correlationId,
        quoteOnlyRequired ? AgentRequestKind.TALK : AgentRequestKind.TOOL_CALL,
        quoteOnlyRequired
            ? AgentToolEvidence.none()
            : new AgentToolEvidence(
                !successfulToolResults.isEmpty(),
                successfulToolResults.size(),
                successfulToolResults.size(),
                0));
  }

  Result prepare(
      AgentInvocation invocation,
      LlmResponse response,
      List<LlmMessage> messages,
      Optional<String> requiredFreshTool,
      List<AgentToolResult> successfulToolResults,
      String correlationId,
      AgentRequestKind finalKind,
      AgentToolEvidence toolEvidence)
      throws LlmException, AgentRoutingException {
    boolean quoteOnlyRequired =
        !invocation.commandOriginated()
            && invocation.mode() != AgentInvocationMode.MODERATION
            && (finalKind == AgentRequestKind.TALK || finalKind == AgentRequestKind.UNCLASSIFIED)
            && !toolEvidence.attempted();
    if (response == null) {
      throw new AgentRoutingException("Agent returned no response");
    }
    response = responseCorrector.correctFailurePlaceholder(response, messages, correlationId);
    response = responseCorrector.correctInternalEvidenceLeak(response, messages, correlationId);
    freshDataFinalValidator.validate(requiredFreshTool, response, successfulToolResults);
    String sanitizedContent = responseSanitizer.sanitize(response.content());
    if (sanitizedContent.isBlank()) {
      throw new AgentRoutingException("Agent returned an empty response");
    }
    if (invocation.mode() == AgentInvocationMode.MODERATION) {
      return Result.silent();
    }
    if (sanitizedContent.strip().equals(participationConfig.noReplyMarker())
        && !invocation.mode().requiresReply()) {
      return Result.silent();
    }
    if (quoteOnlyRequired && response.toolCalls().isEmpty()) {
      response = responseCorrector.correctQuoteOnly(response, messages, correlationId);
    }
    sanitizedContent = responseSanitizer.sanitize(response.content());
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

  /** Carries the result value used by the enclosing agent component. */
  record Result(String content, boolean shouldReply) {
    static Result silent() {
      return new Result("", false);
    }
  }
}
