package org.saturn.app.agent.routing;

import org.saturn.app.agent.api.AgentInvocationMode;

/** Trusted, request-local input used by the deterministic semantic classifier. */
public record AgentRequestInput(String text, AgentInvocationMode mode, boolean commandOriginated) {}
