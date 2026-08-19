/**
 * Saturn's bounded LLM orchestration, tool contracts, runtime composition, and room automation.
 *
 * <p>{@link org.saturn.app.agent.routing.AgentRuntimeFactory} composes the runtime. Requests enter
 * as immutable {@link org.saturn.app.agent.api.AgentInvocation} values, pass through {@link
 * org.saturn.app.service.AgentService}, and are routed by {@link
 * org.saturn.app.agent.api.AgentRouter}. The router serializes shared conversation sessions,
 * assembles provider context, enforces response policies, executes contextual tools, validates the
 * final response, and persists conversation memory plus reusable model-data evidence. Results that
 * delivered room actions do not cross turns.
 *
 * <p>The main public extension contracts are {@link org.saturn.app.agent.api.AgentTool}, {@link
 * org.saturn.app.agent.api.AgentToolDescriptor}, {@link org.saturn.app.agent.api.AgentRouter}, and
 * {@link org.saturn.app.agent.api.AgentMemoryStore}. Concrete tools live in {@code
 * org.saturn.app.agent.tool}; provider transport lives in {@code
 * org.saturn.app.agent.llm.provider.openai}; LLM contracts live in {@code
 * org.saturn.app.agent.llm}; H2 adapters live in {@code org.saturn.app.agent.persistence};
 * generated-SQL policy lives in {@code org.saturn.app.agent.sql}; and autonomous moderation lives
 * in {@code org.saturn.app.agent.moderation}.
 *
 * <p>Internal orchestration collaborators are package-private by design. See {@code
 * AGENTIC_ARCHITECTURE.md} for the request lifecycle, ordering guarantees, extension procedures,
 * tests, and troubleshooting guidance.
 */
package org.saturn.app.agent;
