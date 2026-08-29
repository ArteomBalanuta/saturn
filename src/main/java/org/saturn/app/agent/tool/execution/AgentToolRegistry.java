package org.saturn.app.agent.tool.execution;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.saturn.app.agent.api.AgentCapability;
import org.saturn.app.agent.api.AgentContext;
import org.saturn.app.agent.api.AgentTool;
import org.saturn.app.agent.api.AgentToolDescriptor;
import org.saturn.app.agent.tool.contract.AgentToolDefinitionFactory;

/**
 * Builds the immutable set of tools available to the agent runtime.
 *
 * <p>Registration occurs during startup. After {@link #freeze()}, concurrent lookups and provider
 * definition generation are read-only unless explicit dynamic mode is enabled.
 */
public final class AgentToolRegistry {
  private final Map<String, AgentTool> tools = new LinkedHashMap<>();
  private final AgentToolDefinitionFactory definitionFactory = new AgentToolDefinitionFactory();
  private final Map<DefinitionCacheKey, JsonArray> definitionCache = new ConcurrentHashMap<>();
  private long generation;
  private boolean frozen;
  private boolean dynamicMode;

  /** Registers a uniquely named tool before the registry is frozen. */
  public synchronized AgentToolRegistry register(AgentTool tool) {
    ensureMutable();
    AgentTool candidate = validated(tool);
    if (tools.containsKey(candidate.name())) {
      throw new IllegalArgumentException("Duplicate agent tool: " + candidate.name());
    }
    tools.put(candidate.name(), candidate);
    generation++;
    return this;
  }

  /** Returns the current monotonic registry generation. */
  public synchronized long generation() {
    return generation;
  }

  /** Returns an immutable, ordered read snapshot of the current registry. */
  public synchronized Snapshot snapshot() {
    return new Snapshot(generation, tools);
  }

  /** Prevents further startup registration and returns this registry for startup wiring. */
  public synchronized AgentToolRegistry freeze() {
    frozen = true;
    return this;
  }

  /** Explicitly permits the narrow dynamic mutation API after startup freeze. */
  public synchronized AgentToolRegistry enableDynamicMode() {
    dynamicMode = true;
    return this;
  }

  /** Adds or atomically replaces a tool in explicit dynamic mode. */
  public synchronized AgentToolRegistry replace(AgentTool tool) {
    ensureDynamic();
    AgentTool candidate = validated(tool);
    tools.put(candidate.name(), candidate);
    generation++;
    return this;
  }

  /** Removes a tool in explicit dynamic mode; missing names are an idempotent no-op. */
  public synchronized AgentToolRegistry deregister(String name) {
    ensureDynamic();
    String validatedName = validatedName(name);
    if (tools.remove(validatedName) != null) {
      generation++;
    }
    return this;
  }

  /** Resolves a tool only when it is available to the supplied caller context. */
  public Optional<AgentTool> find(AgentContext context, String name) {
    AgentTool tool;
    synchronized (this) {
      tool = tools.get(name);
    }
    return Optional.ofNullable(tool).filter(candidate -> isAvailable(candidate, context));
  }

  /** Returns provider definitions for every tool visible to the supplied caller context. */
  public JsonArray definitions(AgentContext context) {
    Objects.requireNonNull(context, "context");
    Snapshot current = snapshot();
    String fingerprint = contextFingerprint(context);
    if (fingerprint == null) {
      return buildDefinitions(current.tools(), context);
    }
    DefinitionCacheKey key = new DefinitionCacheKey(current.generation(), fingerprint);
    JsonArray cached = definitionCache.get(key);
    if (cached != null) {
      return cached.deepCopy();
    }
    JsonArray built = buildDefinitions(current.tools(), context);
    JsonArray published = built.deepCopy();
    definitionCache.putIfAbsent(key, published);
    return built.deepCopy();
  }

  /**
   * Implements the {@code buildDefinitions} operation for this agent component.
   *
   * @param current input argument used by this operation
   * @param context input argument used by this operation
   * @return the operation result
   */
  private JsonArray buildDefinitions(Map<String, AgentTool> current, AgentContext context) {
    JsonArray definitions = new JsonArray();
    current.values().stream()
        .filter(tool -> isAvailable(tool, context))
        .forEach(tool -> definitions.add(definition(tool, context)));
    return definitions;
  }

  /** Implements the {@code ensureMutable} operation for this agent component. */
  private synchronized void ensureMutable() {
    if (frozen) {
      throw new IllegalStateException("Agent tool registry is frozen");
    }
  }

  /** Implements the {@code ensureDynamic} operation for this agent component. */
  private synchronized void ensureDynamic() {
    if (!dynamicMode) {
      throw new IllegalStateException("Agent tool registry dynamic mode is disabled");
    }
  }

  /**
   * Implements the {@code validated} operation for this agent component.
   *
   * @param tool input argument used by this operation
   * @return the operation result
   */
  private AgentTool validated(AgentTool tool) {
    AgentTool candidate = Objects.requireNonNull(tool, "tool");
    validatedName(candidate.name());
    return candidate;
  }

  /**
   * Implements the {@code validatedName} operation for this agent component.
   *
   * @param name input argument used by this operation
   * @return the operation result
   */
  private String validatedName(String name) {
    Objects.requireNonNull(name, "name");
    if (!name.matches("[a-z][a-z0-9_]{0,63}")) {
      throw new IllegalArgumentException(
          "Agent tool name must be a lowercase alphanumeric identifier");
    }
    return name;
  }

  /**
   * Implements the {@code isAvailable} operation for this agent component.
   *
   * @param tool input argument used by this operation
   * @param context input argument used by this operation
   * @return the operation result
   */
  private boolean isAvailable(AgentTool tool, AgentContext context) {
    try {
      return tool.isAvailableTo(context);
    } catch (RuntimeException exception) {
      return false;
    }
  }

  /**
   * Implements the {@code definition} operation for this agent component.
   *
   * @param tool input argument used by this operation
   * @param context input argument used by this operation
   * @return the operation result
   */
  private JsonObject definition(AgentTool tool, AgentContext context) {
    AgentToolDescriptor descriptor = tool.descriptor(context);
    if (!tool.name().equals(descriptor.name())) {
      throw new IllegalStateException(
          "Tool descriptor name does not match registered tool: " + tool.name());
    }
    return definitionFactory.create(descriptor);
  }

  /**
   * Implements the {@code contextFingerprint} operation for this agent component.
   *
   * @param context input argument used by this operation
   * @return the operation result
   */
  private String contextFingerprint(AgentContext context) {
    // trip/hash may be secrets and may affect arbitrary tools; avoid unsafe reuse for them.
    if (context.trip() != null || context.hash() != null) {
      return null;
    }
    List<String> users = List.copyOf(context.roomUsers());
    List<String> capabilities =
        context.capabilities().stream().map(AgentCapability::name).sorted().toList();
    return List.of(
            context.room(),
            context.nick(),
            Boolean.toString(context.whisper()),
            users.toString(),
            capabilities.toString(),
            String.valueOf(context.moderationTarget()))
        .toString();
  }

  /**
   * Defines the record {@code DefinitionCacheKey} in the Saturn agent runtime.
   *
   * <p>This type is part of the source-compatible agent boundary; validation and failure behavior
   * are retained by its implementation.
   */
  /**
   * Implements the {@code DefinitionCacheKey} operation for this agent component.
   *
   * @param generation input argument used by this operation
   * @param contextFingerprint input argument used by this operation
   */
  private record DefinitionCacheKey(long generation, String contextFingerprint) {}

  /** Immutable ordered registry snapshot. */
  public record Snapshot(long generation, Map<String, AgentTool> tools) {
    public Snapshot {
      tools = Collections.unmodifiableMap(new LinkedHashMap<>(tools));
    }
  }
}
