package org.saturn.app.listener.impl;

import org.saturn.app.model.dto.JoinChannelListenerDto;
import org.saturn.app.model.dto.User;
import org.saturn.app.listener.JoinChannelListener;
import org.saturn.app.util.Util;

import java.util.List;

import static org.saturn.app.model.command.impl.ListUserCommandImpl.printUsers;

public class ListCommandListenerImpl implements JoinChannelListener {
    private final JoinChannelListenerDto dto;

    public ListCommandListenerImpl(JoinChannelListenerDto dto) {
        this.dto = dto;
    }

    @Override
    public String getListenerName() {
        return "listCommandListener";
    }

    @Override
    public void notify(String jsonText) {
        List<User> users = Util.getUsers(jsonText);
        boolean onlyMeOnline = users.stream().allMatch(User::isIsMe);
        if (onlyMeOnline) {
            dto.mainEngine.outService.enqueueMessageForSending("@" + dto.author + " " + dto.channel + " is empty");
        } else {
            printUsers(dto.author, users,  dto.mainEngine.outService);
        }
        dto.slaveEngine.stop();

        dto.mainEngine.shareMessages();
    }
}
