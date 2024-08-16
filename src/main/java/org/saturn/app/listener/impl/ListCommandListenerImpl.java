package org.saturn.app.listener.impl;

import org.saturn.app.model.dto.JoinChannelListenerDto;
import org.saturn.app.model.dto.User;
import org.saturn.app.listener.JoinChannelListener;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.impl.OutService;
import org.saturn.app.util.Util;

import java.util.List;
import java.util.Objects;

public class ListCommandListenerImpl implements JoinChannelListener {
    private final JoinChannelListenerDto dto;

    private ChatMessage chatMessage;

    public ListCommandListenerImpl(JoinChannelListenerDto dto) {
        this.dto = dto;
    }

    @Override
    public String getListenerName() {
        return "listCommandListener";
    }

    @Override
    public void notify(String jsonText) {
        List<User> users = Util.extractUsersFromJson(jsonText);
        boolean onlyMeOnline = users.stream().allMatch(User::isIsMe);
        OutService outService = dto.mainEngine.outService;
        String author = chatMessage.getNick();
        if (onlyMeOnline) {
            outService.enqueueMessageForSending(author, " " + dto.channel + " is empty", chatMessage.isWhisper());
        } else {
            printUsers(author, users,  outService, chatMessage.isWhisper());
        }
        dto.slaveEngine.stop();

        dto.mainEngine.shareMessages();
    }

    @Override
    public void setChatMessage(ChatMessage message) {
        this.chatMessage = message;
    }

    private void printUsers(String author, List<User> users, OutService outService, boolean isWhisper) {
        StringBuilder output = new StringBuilder();
        users.forEach(user -> output.append(user.getHash()).append(" - ").append(user.getTrip() == null || Objects.equals(user.getTrip(), "") ? "------" : user.getTrip()).append(" - ").append(user.getNick()).append("\\n"));

        outService.enqueueMessageForSending(author, "\\nUsers online: \\n" + output + "\\n", isWhisper);
    }
}
