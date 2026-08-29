package org.saturn.app.agent.api;

import com.google.gson.JsonObject;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.saturn.app.agent.tool.contract.AgentToolSchemas;

/**
 * Immutable provider-facing contract for one contextual agent tool.
 *
 * <p>The constructor validates metadata and deep-copies schemas. {@code isIdempotent} is an
 * execution property, not a permission: only read-only, idempotent tools without prerequisites may
 * be included in a concurrent batch.
 */
public record AgentToolDescriptor(
    String name,
    String label,
    String description,
    String category,
    ToolAccess access,
    ToolEffect effect,
    ToolResultMode resultMode,
    JsonObject parameters,
    List<String> whenToUse,
    List<String> whenNotToUse,
    List<ToolExample> examples,
    Set<String> requiredCapabilities,
    Set<String> requiredSuccessfulTools,
    boolean isIdempotent,
    Duration timeout,
    JsonObject resultSchema,
    Set<String> resourceReads,
    Set<String> resourceWrites) {
  /**
   * Constructs this value after validating and defensively retaining its supplied inputs.
   *
   * @param name the name input; null handling follows the validation performed by this declaration
   * @param label the label input; null handling follows the validation performed by this
   *     declaration
   * @param description the description input; null handling follows the validation performed by
   *     this declaration
   * @param category the category input; null handling follows the validation performed by this
   *     declaration
   * @param access the access input; null handling follows the validation performed by this
   *     declaration
   * @param effect the effect input; null handling follows the validation performed by this
   *     declaration
   * @param resultMode the resultMode input; null handling follows the validation performed by this
   *     declaration
   * @param parameters the parameters input; null handling follows the validation performed by this
   *     declaration
   * @param whenToUse the whenToUse input; null handling follows the validation performed by this
   *     declaration
   * @param whenNotToUse the whenNotToUse input; null handling follows the validation performed by
   *     this declaration
   * @param examples the examples input; null handling follows the validation performed by this
   *     declaration
   * @param requiredCapabilities the requiredCapabilities input; null handling follows the
   *     validation performed by this declaration
   * @param requiredSuccessfulTools the requiredSuccessfulTools input; null handling follows the
   *     validation performed by this declaration
   * @param isIdempotent the isIdempotent input; null handling follows the validation performed by
   *     this declaration
   * @param timeout the timeout input; null handling follows the validation performed by this
   *     declaration
   * @param resultSchema the resultSchema input; null handling follows the validation performed by
   *     this declaration
   * @param resourceReads the resourceReads input; null handling follows the validation performed by
   *     this declaration
   * @param resourceWrites the resourceWrites input; null handling follows the validation performed
   *     by this declaration
   */
  public AgentToolDescriptor {
    name = required(name, "name");
    if (!name.matches("[a-z][a-z0-9_]{0,63}")) {
      throw new IllegalArgumentException("name must be a lowercase alphanumeric identifier");
    }
    label = required(label, "label");
    description = required(description, "description");
    category = required(category, "category");
    access = Objects.requireNonNull(access, "access");
    effect = Objects.requireNonNull(effect, "effect");
    resultMode = Objects.requireNonNull(resultMode, "resultMode");
    parameters = Objects.requireNonNull(parameters, "parameters").deepCopy();
    AgentToolSchemas.validateSchema(parameters);
    whenToUse = immutableList(whenToUse, "whenToUse");
    whenNotToUse = immutableList(whenNotToUse, "whenNotToUse");
    if (whenNotToUse.isEmpty()) {
      throw new IllegalArgumentException("whenNotToUse must declare a negative constraint");
    }
    examples = immutableList(examples, "examples");
    String descriptorName = name;
    if (examples.stream().anyMatch(example -> !descriptorName.equals(example.toolName()))) {
      throw new IllegalArgumentException("examples must reference this tool");
    }
    requiredCapabilities = immutableSet(requiredCapabilities, "requiredCapabilities");
    requiredSuccessfulTools = immutableSet(requiredSuccessfulTools, "requiredSuccessfulTools");
    timeout = Objects.requireNonNull(timeout, "timeout");
    if (timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must not be negative");
    }
    resultSchema = Objects.requireNonNull(resultSchema, "resultSchema").deepCopy();
    AgentToolSchemas.validateResultSchema(resultSchema);
    resourceReads = immutableResources(resourceReads, "resourceReads");
    resourceWrites = immutableResources(resourceWrites, "resourceWrites");
  }

  /**
   * Constructs this value after validating and defensively retaining its supplied inputs.
   *
   * @param name the name input; null handling follows the validation performed by this declaration
   * @param label the label input; null handling follows the validation performed by this
   *     declaration
   * @param description the description input; null handling follows the validation performed by
   *     this declaration
   * @param category the category input; null handling follows the validation performed by this
   *     declaration
   * @param access the access input; null handling follows the validation performed by this
   *     declaration
   * @param effect the effect input; null handling follows the validation performed by this
   *     declaration
   * @param resultMode the resultMode input; null handling follows the validation performed by this
   *     declaration
   * @param parameters the parameters input; null handling follows the validation performed by this
   *     declaration
   * @param whenToUse the whenToUse input; null handling follows the validation performed by this
   *     declaration
   * @param whenNotToUse the whenNotToUse input; null handling follows the validation performed by
   *     this declaration
   * @param examples the examples input; null handling follows the validation performed by this
   *     declaration
   * @param requiredCapabilities the requiredCapabilities input; null handling follows the
   *     validation performed by this declaration
   * @param requiredSuccessfulTools the requiredSuccessfulTools input; null handling follows the
   *     validation performed by this declaration
   * @param isIdempotent the isIdempotent input; null handling follows the validation performed by
   *     this declaration
   * @param timeout the timeout input; null handling follows the validation performed by this
   *     declaration
   * @param resultSchema the resultSchema input; null handling follows the validation performed by
   *     this declaration
   */
  public AgentToolDescriptor(
      String name,
      String label,
      String description,
      String category,
      ToolAccess access,
      ToolEffect effect,
      ToolResultMode resultMode,
      JsonObject parameters,
      List<String> whenToUse,
      List<String> whenNotToUse,
      List<ToolExample> examples,
      Set<String> requiredCapabilities,
      Set<String> requiredSuccessfulTools,
      boolean isIdempotent,
      Duration timeout,
      JsonObject resultSchema) {
    this(
        name,
        label,
        description,
        category,
        access,
        effect,
        resultMode,
        parameters,
        whenToUse,
        whenNotToUse,
        examples,
        requiredCapabilities,
        requiredSuccessfulTools,
        isIdempotent,
        timeout,
        resultSchema,
        Set.of(),
        Set.of());
  }

  /**
   * Constructs this value after validating and defensively retaining its supplied inputs.
   *
   * @param name the name input; null handling follows the validation performed by this declaration
   * @param label the label input; null handling follows the validation performed by this
   *     declaration
   * @param description the description input; null handling follows the validation performed by
   *     this declaration
   * @param category the category input; null handling follows the validation performed by this
   *     declaration
   * @param access the access input; null handling follows the validation performed by this
   *     declaration
   * @param effect the effect input; null handling follows the validation performed by this
   *     declaration
   * @param resultMode the resultMode input; null handling follows the validation performed by this
   *     declaration
   * @param parameters the parameters input; null handling follows the validation performed by this
   *     declaration
   * @param whenToUse the whenToUse input; null handling follows the validation performed by this
   *     declaration
   * @param whenNotToUse the whenNotToUse input; null handling follows the validation performed by
   *     this declaration
   * @param examples the examples input; null handling follows the validation performed by this
   *     declaration
   * @param requiredCapabilities the requiredCapabilities input; null handling follows the
   *     validation performed by this declaration
   * @param requiredSuccessfulTools the requiredSuccessfulTools input; null handling follows the
   *     validation performed by this declaration
   */
  public AgentToolDescriptor(
      String name,
      String label,
      String description,
      String category,
      ToolAccess access,
      ToolEffect effect,
      ToolResultMode resultMode,
      JsonObject parameters,
      List<String> whenToUse,
      List<String> whenNotToUse,
      List<ToolExample> examples,
      Set<String> requiredCapabilities,
      Set<String> requiredSuccessfulTools) {
    this(
        name,
        label,
        description,
        category,
        access,
        effect,
        resultMode,
        parameters,
        whenToUse,
        whenNotToUse,
        examples,
        requiredCapabilities,
        requiredSuccessfulTools,
        effect == ToolEffect.READ_ONLY,
        Duration.ZERO,
        anyResultSchema(),
        Set.of(),
        Set.of());
  }

  /** Returns whether this descriptor declares no Saturn-side effect. */
  public boolean isReadOnly() {
    return effect == ToolEffect.READ_ONLY;
  }

  @Override
  public JsonObject parameters() {
    return parameters.deepCopy();
  }

  /**
   * Documents the resultSchema operation and its boundary behavior.
   *
   * @return the computed result; empty or false indicates that no applicable value was available
   */
  @Override
  public JsonObject resultSchema() {
    return resultSchema.deepCopy();
  }

  private static JsonObject anyResultSchema() {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "any");
    return schema;
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }

  private static <T> List<T> immutableList(List<T> values, String field) {
    return List.copyOf(Objects.requireNonNull(values, field));
  }

  private static <T> Set<T> immutableSet(Set<T> values, String field) {
    return Set.copyOf(Objects.requireNonNull(values, field));
  }

  private static Set<String> immutableResources(Set<String> values, String field) {
    Set<String> copy = immutableSet(values, field);
    if (copy.stream().anyMatch(value -> value == null || value.isBlank())) {
      throw new IllegalArgumentException(field + " must contain only nonblank keys");
    }
    return copy;
  }
}
