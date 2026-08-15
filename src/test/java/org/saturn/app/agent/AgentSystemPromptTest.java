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

    assertTrue(rendered.contains("PRIORITY ORDER"));
    assertTrue(rendered.contains("EXECUTION LOOP"));
    assertTrue(rendered.contains("Choose the narrowest exposed tool"));
    assertTrue(rendered.contains("Complete required_successful_tools in the declared order"));
    assertTrue(rendered.contains("The newest message controls the topic"));
    assertTrue(rendered.contains("For definition requests, answer the exact term"));
    assertTrue(rendered.contains("Execute an actionable request immediately"));
    assertTrue(rendered.contains("confirmation when the request and required arguments"));
    assertTrue(rendered.contains("Do not mock, lecture,"));
    assertTrue(rendered.contains("For current weather or time, call run_command"));
    assertTrue(rendered.contains("Never print, quote, or fence a Saturn command"));
    assertTrue(rendered.contains("Conditional or future requests are not immediate commands"));
    assertTrue(rendered.contains("never claim a watcher, rule, or schedule"));
    assertTrue(rendered.contains("Do not use canned openings, repeated"));
    assertTrue(rendered.contains("never use \"* item\""));
    assertTrue(rendered.contains("U+2009 THIN SPACE"));
    assertTrue(rendered.contains("escaped \"\\\\n\" payload newlines"));
    assertTrue(rendered.indexOf("PRIORITY ORDER") < rendered.indexOf("PERSONA"));
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
    assertTrue(rendered.contains("politely asks you to be silent"));
    assertTrue(rendered.contains("Never announce, explain, or narrate silence"));
  }
}
