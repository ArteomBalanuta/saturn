package org.saturn.app.agent;

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
