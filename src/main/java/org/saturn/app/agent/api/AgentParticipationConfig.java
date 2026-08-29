package org.saturn.app.agent.api;

import com.moandjiezana.toml.Toml;
import java.time.Duration;
import java.util.Objects;
import org.saturn.app.agent.config.AgentConfigValueReader;

/** Configures when an agent participates in room conversations. */
public record AgentParticipationConfig(
    String creatorTrip,
    boolean ambientEnabled,
    int ambientEveryMessages,
    Duration quietDuration,
    int contextMessageLimit,
    String noReplyMarker) {
  private static final String DEFAULT_CREATOR_TRIP = "595754";
  private static final String DEFAULT_NO_REPLY_MARKER = "[[SATURN_NO_REPLY]]";

  /**
   * Constructs this value after validating and defensively retaining its supplied inputs.
   *
   * @param creatorTrip the creatorTrip input; null handling follows the validation performed by
   *     this declaration
   * @param ambientEnabled the ambientEnabled input; null handling follows the validation performed
   *     by this declaration
   * @param ambientEveryMessages the ambientEveryMessages input; null handling follows the
   *     validation performed by this declaration
   * @param quietDuration the quietDuration input; null handling follows the validation performed by
   *     this declaration
   * @param contextMessageLimit the contextMessageLimit input; null handling follows the validation
   *     performed by this declaration
   * @param noReplyMarker the noReplyMarker input; null handling follows the validation performed by
   *     this declaration
   */
  public AgentParticipationConfig {
    creatorTrip = requireNotBlank(creatorTrip, "creatorTrip");
    Objects.requireNonNull(quietDuration, "quietDuration");
    noReplyMarker = requireNotBlank(noReplyMarker, "noReplyMarker");
    requirePositive(ambientEveryMessages, "ambientEveryMessages");
    requirePositive(quietDuration, "quietMinutes");
    requirePositive(contextMessageLimit, "contextMessageLimit");
  }

  /**
   * Implements the {@code from} operation for this agent component.
   *
   * @param root input argument used by this operation
   * @return the operation result
   */
  public static AgentParticipationConfig from(Toml root) {
    Toml table = root == null ? null : root.getTable("agent");
    return new AgentParticipationConfig(
        AgentConfigValueReader.readString(table, "creatorTrip", DEFAULT_CREATOR_TRIP),
        AgentConfigValueReader.readBoolean(table, "ambientEnabled", false),
        AgentConfigValueReader.toInt(
            AgentConfigValueReader.readLong(table, "ambientEveryMessages", 8),
            "ambientEveryMessages"),
        Duration.ofMinutes(AgentConfigValueReader.readLong(table, "quietMinutes", 15)),
        AgentConfigValueReader.toInt(
            AgentConfigValueReader.readLong(table, "contextMessageLimit", 60),
            "contextMessageLimit"),
        AgentConfigValueReader.readString(table, "noReplyMarker", DEFAULT_NO_REPLY_MARKER));
  }

  /**
   * Implements the {@code requireNotBlank} operation for this agent component.
   *
   * @param value input argument used by this operation
   * @param name input argument used by this operation
   * @return the operation result
   */
  private static String requireNotBlank(String value, String name) {
    Objects.requireNonNull(value, name);
    String normalized = value.trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("agent." + name + " must not be blank");
    }
    return normalized;
  }

  /**
   * Implements the {@code requirePositive} operation for this agent component.
   *
   * @param value input argument used by this operation
   * @param name input argument used by this operation
   */
  private static void requirePositive(long value, String name) {
    if (value <= 0) {
      throw new IllegalArgumentException("agent." + name + " must be positive");
    }
  }

  /**
   * Implements the {@code requirePositive} operation for this agent component.
   *
   * @param value input argument used by this operation
   * @param name input argument used by this operation
   */
  private static void requirePositive(Duration value, String name) {
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException("agent." + name + " must be positive");
    }
  }
}
