package org.saturn.app.command.impl.user;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;

class ListUserCommandImplTest {
  @Test
  void executeCurrentRoomListsUsersSuccessfully() {
    EngineImpl engine = CommandTestSupport.engine();
    engine.channel = "programming";
    engine.currentChannelUsers.add(user("alice", "trip-a", "hash-a"));
    engine.currentChannelUsers.add(user("bob", null, "hash-b"));
    ChatMessage message =
        CommandTestSupport.chatMessage("*list programming", "testAuthor", "testTrip");

    ListUserCommandImpl cmd = new ListUserCommandImpl(engine, message, List.of());

    Status result = cmd.execute().orElseThrow();

    assertEquals(Status.SUCCESSFUL, result);
    assertEquals(
        "@testAuthor \nUsers online: \nhash-a - trip-a - alice\nhash-b - ------ - bob\n\n",
        engine.outgoingMessageQueue.poll());
  }

  @Test
  void executeWithoutArgumentsListsUsersAndReturnsFailure() {
    EngineImpl engine = CommandTestSupport.engine();
    engine.currentChannelUsers.add(user("alice", "trip-a", "hash-a"));
    ChatMessage message = CommandTestSupport.chatMessage("*list", "testAuthor", "testTrip");

    ListUserCommandImpl cmd = new ListUserCommandImpl(engine, message, List.of());

    Status result = cmd.execute().orElseThrow();

    assertEquals(Status.FAILED, result);
    assertEquals(
        "@testAuthor \nUsers online: \nhash-a - trip-a - alice\n\n",
        engine.outgoingMessageQueue.poll());
    assertEquals("@testAuthor Example: *list programming", engine.outgoingMessageQueue.poll());
  }

  private static User user(String nick, String trip, String hash) {
    return new User("programming", false, nick, trip, "", hash, 0, 0L, false);
  }
}
