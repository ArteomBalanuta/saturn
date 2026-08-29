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

  /**
   * Constructs this value after validating and defensively retaining its supplied inputs.
   *
   * @param responseCorrector the responseCorrector input; null handling follows the validation
   *     performed by this declaration
   * @param freshDataFinalValidator the freshDataFinalValidator input; null handling follows the
   *     validation performed by this declaration
   * @param participationConfig the participationConfig input; null handling follows the validation
   *     performed by this declaration
   * @param maxOutputChars the maxOutputChars input; null handling follows the validation performed
   *     by this declaration
   */
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

  /**
   * Prepares a final response after correction, freshness validation, evidence handling, and output
   * bounding.
   *
   * @param invocation the invocation input; null handling follows the validation performed by this
   *     declaration
   * @param response the response input; null handling follows the validation performed by this
   *     declaration
   * @param messages the messages input; null handling follows the validation performed by this
   *     declaration
   * @param requiredFreshTool the requiredFreshTool input; null handling follows the validation
   *     performed by this declaration
   * @param successfulToolResults the successfulToolResults input; null handling follows the
   *     validation performed by this declaration
   * @param correlationId the correlationId input; null handling follows the validation performed by
   *     this declaration
   * @return the computed result; empty or false indicates that no applicable value was available
   */
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

  /**
   * Prepares a final response after correction, freshness validation, evidence handling, and output
   * bounding.
   *
   * @param invocation the invocation input; null handling follows the validation performed by this
   *     declaration
   * @param response the response input; null handling follows the validation performed by this
   *     declaration
   * @param messages the messages input; null handling follows the validation performed by this
   *     declaration
   * @param requiredFreshTool the requiredFreshTool input; null handling follows the validation
   *     performed by this declaration
   * @param successfulToolResults the successfulToolResults input; null handling follows the
   *     validation performed by this declaration
   * @param correlationId the correlationId input; null handling follows the validation performed by
   *     this declaration
   * @param quoteOnlyRequired the quoteOnlyRequired input; null handling follows the validation
   *     performed by this declaration
   * @return the computed result; empty or false indicates that no applicable value was available
   */
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

  /**
   * Prepares a final response after correction, freshness validation, evidence handling, and output
   * bounding.
   *
   * @param invocation the invocation input; null handling follows the validation performed by this
   *     declaration
   * @param response the response input; null handling follows the validation performed by this
   *     declaration
   * @param messages the messages input; null handling follows the validation performed by this
   *     declaration
   * @param requiredFreshTool the requiredFreshTool input; null handling follows the validation
   *     performed by this declaration
   * @param successfulToolResults the successfulToolResults input; null handling follows the
   *     validation performed by this declaration
   * @param correlationId the correlationId input; null handling follows the validation performed by
   *     this declaration
   * @param finalKind the finalKind input; null handling follows the validation performed by this
   *     declaration
   * @param toolEvidence the toolEvidence input; null handling follows the validation performed by
   *     this declaration
   * @return the computed result; empty or false indicates that no applicable value was available
   */
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

  /**
   * Implements the {@code removeNoReplyMarker} operation for this agent component.
   *
   * @param content input argument used by this operation
   * @return the operation result
   */
  private String removeNoReplyMarker(String content) {
    String marker = participationConfig.noReplyMarker();
    if (!content.contains(marker)) {
      return content;
    }
    return trimControlWhitespace(content.replace(marker, ""));
  }

  /**
   * Implements the {@code trimControlWhitespace} operation for this agent component.
   *
   * @param content input argument used by this operation
   * @return the operation result
   */
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

  /**
   * Implements the {@code isControlWhitespace} operation for this agent component.
   *
   * @param character input argument used by this operation
   * @return the operation result
   */
  private boolean isControlWhitespace(char character) {
    return character == ' ' || character == '\t' || character == '\n' || character == '\r';
  }

  /** Carries the result value used by the enclosing agent component. */
  record Result(String content, boolean shouldReply) {
    /**
     * Implements the {@code silent} operation for this agent component.
     *
     * @return the operation result
     */
    static Result silent() {
      return new Result("", false);
    }
  }
}
