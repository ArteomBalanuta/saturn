package org.saturn.app.agent.llm;

/** Represents a tool call requested by a language-model response. */
public record LlmToolCall(String id, String name, String arguments) {}
