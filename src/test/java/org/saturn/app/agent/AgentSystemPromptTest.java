package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moandjiezana.toml.Toml;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentSystemPromptTest {
  @Test
  void rendersVaelenCreatorAndDatabaseToolPlaybook() {
    AgentParticipationConfig config = AgentParticipationConfig.from(new Toml());
    AgentSystemPrompt prompt = new AgentSystemPrompt(config);
    AgentContext context =
        new AgentContext(
            "lounge",
            "merc",
            "595754",
            "creator-hash",
            false,
            List.of("merc", "alice"),
            Set.of(AgentCapability.DYNAMIC_SQL));
    AgentInvocation invocation =
        new AgentInvocation("request-1", context, "who is sun?", AgentInvocationMode.MENTION);

    String rendered = prompt.render(invocation, "correlation-1", "{\"rows\":[{\"name\":\"sun\"}]}");

    assertTrue(rendered.contains("Vaelen"));
    assertTrue(rendered.contains("595754"));
    assertTrue(rendered.contains("creator\":true"));
    assertTrue(rendered.contains("user_message_history"));
    assertTrue(rendered.contains("database_schema"));
    assertTrue(rendered.contains("database_sql"));
    assertTrue(rendered.contains("MENTION"));
    assertTrue(rendered.contains("\"name\":\"sun\""));
  }

  @Test
  void prioritizesAuthorizedExecutionOverPersonaAndDialogue() {
    AgentSystemPrompt prompt = new AgentSystemPrompt(AgentParticipationConfig.from(new Toml()));
    AgentContext context =
        new AgentContext(
            "programming", "mer", "595754", "creator-hash", false, List.of("mer", "buu"));
    AgentInvocation invocation =
        new AgentInvocation("request-3", context, "fetch the weather", AgentInvocationMode.MENTION);

    String rendered =
        prompt.render(
            invocation,
            "correlation-3",
            "{\"rows\":[{\"name\":\"buu\",\"message\":\"Charlotte he meant\"}]}");

    assertTrue(rendered.contains("Execute the user's authorized request"));
    assertTrue(rendered.contains("This duty outranks persona"));
    assertTrue(rendered.contains("resolve references from shared history"));
    assertTrue(rendered.contains("The newest user message is authoritative"));
    assertTrue(rendered.contains("For definition requests, answer the exact term"));
    assertTrue(rendered.contains("call the matching tool immediately"));
    assertTrue(rendered.contains("Do not ask for confirmation"));
    assertTrue(rendered.contains("Do not mock, lecture, philosophize"));
    assertTrue(rendered.contains("never repeat a question already answered"));
    assertTrue(rendered.contains("For current weather or time, call run_command"));
    assertTrue(rendered.contains("Never print, quote, or fence a Saturn command"));
    assertTrue(rendered.contains("Conditional or future requests are not immediate commands"));
    assertTrue(rendered.contains("Never claim a watcher, rule, or scheduled action exists"));
    assertTrue(
        rendered.indexOf("Execute the user's authorized request") < rendered.indexOf("PERSONA"));
    assertFalse(rendered.contains("punch back immediately"));
    assertFalse(rendered.contains("philosophical roasts"));
    assertFalse(rendered.contains("playfully condescending"));
  }

  @Test
  void instructsAmbientTurnsToUseConfiguredNoReplyMarker() {
    Toml root = new Toml().read("[agent]\nnoReplyMarker = \"<quiet>\"");
    AgentSystemPrompt prompt = new AgentSystemPrompt(AgentParticipationConfig.from(root));
    AgentContext context =
        new AgentContext("lounge", "alice", "trip-a", "hash-a", false, List.of("alice"));
    AgentInvocation invocation =
        new AgentInvocation("request-2", context, "ordinary chat", AgentInvocationMode.AMBIENT);

    String rendered = prompt.render(invocation, "correlation-2", "");

    assertTrue(rendered.contains("<quiet>"));
    assertTrue(rendered.contains("AMBIENT"));
    assertTrue(rendered.contains("may remain silent"));
    assertTrue(rendered.contains("Never announce or narrate silence"));
  }
}
