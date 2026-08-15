package org.saturn.app.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.saturn.app.agent.llm.LlmMessage;

class AgentFreshnessPolicyTest {
  @Test
  void requiresFreshHistoryForNamedUserAnalysis() {
    AgentFreshnessPolicy policy = new AgentFreshnessPolicy();

    assertEquals(
        Optional.of("user_message_history"),
        policy.requiredTool("tell me about jill user", List.of()));
    assertEquals(
        Optional.of("user_message_history"),
        policy.requiredTool("tell me about user merc", List.of()));
    assertEquals(
        Optional.of("user_message_history"),
        policy.requiredTool("who is nex", List.of(), List.of("nex")));
    assertEquals(
        Optional.of("user_message_history"),
        policy.requiredTool("what did sun say recently?", List.of(), List.of("sun")));
    assertEquals(
        Optional.of("user_message_history"),
        policy.requiredTool("show me the message history for @jetty", List.of()));
    assertEquals(
        Optional.of("user_message_history"),
        policy.requiredTool("show Jill's messages", List.of()));
    assertEquals(
        Optional.of("user_message_history"),
        policy.requiredTool("give me Jill's profile", List.of()));
    assertEquals(
        Optional.of("user_message_history"),
        policy.requiredTool("what has Jill written?", List.of(), List.of("jill")));
    assertEquals(
        Optional.of("user_message_history"),
        policy.requiredTool("tell me about nex", List.of(), List.of("alice", "nex")));
  }

  @Test
  void extractsQuotedNamedUserTargetsForRouterOwnedHistoryLookup() {
    AgentFreshnessPolicy policy = new AgentFreshnessPolicy();
    String prompt =
        "tell me about user named \"gvreahui\" - summarize their activity and recent messages";

    assertEquals(Optional.of("user_message_history"), policy.requiredTool(prompt, List.of()));
    assertEquals(Optional.of("gvreahui"), policy.requiredNick(prompt, List.of(), List.of()));
  }

  @Test
  void carriesFreshnessAcrossAnExplicitHistoryFollowUp() {
    AgentFreshnessPolicy policy = new AgentFreshnessPolicy();
    List<LlmMessage> history =
        List.of(
            LlmMessage.user(
                "Public Saturn message from @mer in #programming:\n" + "tell me about jill user"),
            LlmMessage.assistant("Old Jill summary", List.of()));

    assertEquals(
        Optional.of("user_message_history"), policy.requiredTool("check it again", history));
    assertEquals(
        Optional.of("user_message_history"), policy.requiredTool("check her elsewhere", history));
    assertEquals(Optional.of("user_message_history"), policy.requiredTool("do it @korin", history));
  }

  @Test
  void leavesGeneralAndCurrentPresenceQuestionsToNormalRouting() {
    AgentFreshnessPolicy policy = new AgentFreshnessPolicy();
    List<LlmMessage> history =
        List.of(
            LlmMessage.user(
                "Public Saturn message from @mer in #programming:\n" + "tell me about jill user"),
            LlmMessage.assistant("Old Jill summary", List.of()));

    assertTrue(policy.requiredTool("tell me about Java records", List.of()).isEmpty());
    assertTrue(policy.requiredTool("tell me about Java", List.of(), List.of("alice")).isEmpty());
    assertTrue(policy.requiredTool("tell me about user experience", List.of()).isEmpty());
    assertTrue(policy.requiredTool("analyze message history retention", List.of()).isEmpty());
    assertTrue(
        policy
            .requiredTool("what has Shakespeare written?", List.of(), List.of("alice"))
            .isEmpty());
    assertTrue(
        policy.requiredTool("tell me the history of Rome", List.of(), List.of("alice")).isEmpty());
    assertTrue(policy.requiredTool("who is in lounge?", List.of()).isEmpty());
    assertTrue(policy.requiredTool("who is the president?", List.of()).isEmpty());
    assertTrue(policy.requiredTool("status", history).isEmpty());
    assertTrue(policy.requiredTool("check the build again", history).isEmpty());
  }
}
