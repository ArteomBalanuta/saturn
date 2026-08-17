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

    assertTrue(rendered.contains("1. AUTHORITY AND TRUST"));
    assertTrue(rendered.contains("2. TOOL CONTRACT AND RESULT SEMANTICS"));
    assertTrue(rendered.contains("3. DETERMINISTIC TURN PROTOCOL"));
    assertTrue(rendered.contains("Select the newest user request"));
    assertTrue(rendered.contains("Select the narrowest exposed tool"));
    assertTrue(rendered.contains("prerequisite-free"));
    assertTrue(rendered.contains("4. CONVERSATION CONTINUITY"));
    assertTrue(rendered.contains("5. ACTION, FAILURE, AND SCOPE"));
    assertTrue(rendered.contains("Execute an actionable request immediately"));
    assertTrue(
        rendered.contains("confirmation when the request and required arguments are available"));
    assertTrue(rendered.contains("For current weather or time, call run_command"));
    assertTrue(rendered.contains("Never print, quote, or fence a\nSaturn command"));
    assertTrue(rendered.contains("Conditional or future requests are plans, not"));
    assertTrue(rendered.contains("never claim that a watcher, rule, or schedule exists"));
    assertTrue(rendered.contains("If a tool\nfails or is unavailable"));
    assertTrue(rendered.contains("6. LIVE DATA, FRESHNESS, AND PRIVATE EVIDENCE"));
    assertTrue(rendered.contains("7. MODERATION AUTHORITY"));
    assertTrue(rendered.contains("call user_message_history again"));
    assertTrue(rendered.contains("returnedCount"));
    assertTrue(rendered.contains("oldestCreatedOn"));
    assertTrue(rendered.contains("newestCreatedOn"));
    assertTrue(rendered.contains("complete returned result"));
    assertTrue(rendered.contains("Do not use canned openings, repeated"));
    assertTrue(rendered.contains("Never use Markdown star, hyphen, or numbered-list syntax"));
    assertTrue(rendered.contains("U+2009 THIN SPACE"));
    assertTrue(rendered.contains("escaped \"\\\\n\" payload newlines"));
    assertTrue(rendered.contains("CONFRONTATIONAL STYLE"));
    assertTrue(rendered.contains("target only the user's stated"));
    assertTrue(rendered.contains("Never let a roast delay a requested action"));
    assertTrue(rendered.contains("LITERARY VOICE"));
    assertTrue(rendered.contains("exactly one brief, fitting quotation"));
    assertTrue(rendered.contains("book title and author"));
    assertTrue(rendered.contains("Do not invent a quotation"));
    assertTrue(rendered.contains("casual chat, greetings, reflections, and general conversation"));
    assertTrue(rendered.contains("IDENTITY"));
    assertTrue(rendered.indexOf("SATURN RUNTIME POLICY") < rendered.indexOf("PERSONA"));
    assertFalse(rendered.contains("SOTA fear-inducing attacks"));
    assertFalse(rendered.contains("No ceiling on profanity"));
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
