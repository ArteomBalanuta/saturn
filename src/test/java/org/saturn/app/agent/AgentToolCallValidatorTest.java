package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.llm.LlmToolCall;

class AgentToolCallValidatorTest {
  @Test
  void rejectsUnknownAndDisallowedToolsBeforeParsingArguments() {
    AgentToolRegistry registry = new AgentToolRegistry().register(tool("echo")).freeze();
    AgentToolCallValidator validator = new AgentToolCallValidator(registry);
    AgentContext context = new AgentContext("room", "nick", null, null, false, List.of("nick"));

    AgentToolCallValidator.Result disallowed =
        validator.validate(context, new LlmToolCall("1", "echo", "not-json"), Set.of("other"));
    AgentToolCallValidator.Result unknown =
        validator.validate(context, new LlmToolCall("2", "missing", "{}"), Set.of());

    assertFalse(disallowed.isValid());
    assertEquals("TOOL_NOT_ALLOWED", disallowed.error().errorCode());
    assertFalse(unknown.isValid());
    assertEquals("UNKNOWN_TOOL", unknown.error().errorCode());
  }

  @Test
  void canonicalizesObjectArgumentOrderingForInvocationIdentity() {
    AgentToolRegistry registry = new AgentToolRegistry().register(tool("echo")).freeze();
    AgentToolCallValidator validator = new AgentToolCallValidator(registry);
    AgentContext context = new AgentContext("room", "nick", null, null, false, List.of("nick"));

    AgentToolCallValidator.Result first =
        validator.validate(context, new LlmToolCall("1", "echo", "{\"b\":2,\"a\":1}"), Set.of());
    AgentToolCallValidator.Result second =
        validator.validate(context, new LlmToolCall("2", "echo", "{\"a\":1,\"b\":2}"), Set.of());

    assertTrue(first.isValid());
    assertTrue(second.isValid());
    assertEquals(first.call().invocationKey(), second.call().invocationKey());
    assertEquals("echo|{\"a\":1,\"b\":2}", first.call().invocationKey());
  }

  @Test
  void rejectsMalformedJsonAndNonObjectArgumentsWithStableErrors() {
    AgentToolCallValidator validator =
        new AgentToolCallValidator(new AgentToolRegistry().register(tool("echo")).freeze());
    AgentContext context = new AgentContext("room", "nick", null, null, false, List.of("nick"));

    for (String arguments : List.of("{", "[]", "true", "42", "null")) {
      AgentToolCallValidator.Result result =
          validator.validate(
              context, new LlmToolCall("id-" + arguments, "echo", arguments), Set.of());

      assertFalse(result.isValid(), arguments);
      assertEquals("INVALID_ARGUMENTS", result.error().errorCode(), arguments);
    }
  }

  @Test
  void treatsBlankArgumentsAsAnEmptyObjectForZeroArgumentTools() {
    AgentToolValidatorFixture fixture = new AgentToolValidatorFixture();

    AgentToolCallValidator.Result result =
        fixture
            .validator()
            .validate(fixture.context(), new LlmToolCall("blank", "echo", "  \n  "), Set.of());

    assertTrue(result.isValid());
    assertEquals("echo|{}", result.call().invocationKey());
    assertTrue(result.call().arguments().isEmpty());
  }

  @Test
  void rejectsAResolvedToolWhoseDescriptorCannotBeTrusted() {
    AgentTool tool =
        new AgentTool() {
          @Override
          public String name() {
            return "echo";
          }

          @Override
          public AgentToolDescriptor descriptor(AgentContext context) {
            throw new IllegalStateException("descriptor unavailable");
          }

          @Override
          public AgentToolResult execute(AgentContext context, JsonObject arguments) {
            return AgentToolResult.success(name(), arguments);
          }
        };
    AgentToolCallValidator validator =
        new AgentToolCallValidator(new AgentToolRegistry().register(tool).freeze());
    AgentContext context = new AgentContext("room", "nick", null, null, false, List.of("nick"));

    AgentToolCallValidator.Result result =
        validator.validate(context, new LlmToolCall("1", "echo", "{}"), Set.of());

    assertFalse(result.isValid());
    assertEquals("INVALID_TOOL_CONTRACT", result.error().errorCode());
  }

  @Test
  void canonicalizesNestedObjectKeysWithoutReorderingArrays() {
    AgentToolCallValidator validator =
        new AgentToolCallValidator(new AgentToolRegistry().register(tool("echo")).freeze());
    AgentContext context = new AgentContext("room", "nick", null, null, false, List.of("nick"));

    AgentToolCallValidator.Result first =
        validator.validate(
            context,
            new LlmToolCall("1", "echo", "{\"items\":[{\"z\":2,\"a\":1}, {\"b\":true}]}"),
            Set.of());
    AgentToolCallValidator.Result second =
        validator.validate(
            context,
            new LlmToolCall("2", "echo", "{\"items\":[{\"a\":1,\"z\":2}, {\"b\":true}]}"),
            Set.of());

    assertEquals(first.call().invocationKey(), second.call().invocationKey());
    assertEquals("echo|{\"items\":[{\"a\":1,\"z\":2},{\"b\":true}]}", first.call().invocationKey());
  }

  private static AgentTool tool(String name) {
    return new AgentTool() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public AgentToolDescriptor descriptor(AgentContext context) {
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        parameters.add("properties", new JsonObject());
        parameters.addProperty("additionalProperties", true);
        return new AgentToolDescriptor(
            name,
            name,
            name,
            "test",
            ToolAccess.PUBLIC,
            ToolEffect.READ_ONLY,
            ToolResultMode.MODEL_DATA,
            parameters,
            List.of(),
            List.of("Do not use for unrelated work."),
            List.of(),
            Set.of(),
            Set.of());
      }

      @Override
      public AgentToolResult execute(AgentContext context, JsonObject arguments) {
        return AgentToolResult.success(name, arguments);
      }
    };
  }

  private static final class AgentToolValidatorFixture {
    private final AgentContext context =
        new AgentContext("room", "nick", null, null, false, List.of("nick"));
    private final AgentToolCallValidator validator =
        new AgentToolCallValidator(new AgentToolRegistry().register(tool("echo")).freeze());

    AgentContext context() {
      return context;
    }

    AgentToolCallValidator validator() {
      return validator;
    }
  }
}
