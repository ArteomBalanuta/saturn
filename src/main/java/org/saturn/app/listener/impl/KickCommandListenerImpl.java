package org.saturn.app.listener.impl;

import org.saturn.app.listener.JoinChannelListener;
import org.saturn.app.model.dto.JoinChannelListenerDto;
import org.saturn.app.model.dto.User;
import org.saturn.app.util.Util;

import java.util.List;

import static org.saturn.app.model.command.impl.ListUserCommandImpl.printUsers;

public class KickCommandListenerImpl implements JoinChannelListener {
    private final JoinChannelListenerDto dto;

    private Runnable operation;

    public KickCommandListenerImpl(JoinChannelListenerDto dto) {
        this.dto = dto;
    }

    @Override
    public String getListenerName() {
        return "kickCommandListener";
    }

    @Override
    public void notify(String jsonText) {
        List<User> users = Util.extractUsersFromJson(jsonText);
        boolean targetIsOnline = users.stream().anyMatch(user -> user.getNick().equals(dto.target));

        if (targetIsOnline) {
            if (this.operation != null) {
                System.out.println("Executing runnable..");
                this.operation.run();
                System.out.println("Done executing runnable..");
            }

        } else {
            dto.mainEngine.outService.enqueueMessageForSending("@" + dto.target + " isn't in the room ?" + dto.destinationRoom);
        }

        dto.slaveEngine.stop();
        dto.mainEngine.shareMessages();
    }

    @Override
    public  void setAction(Runnable runnable) {
        this.operation = runnable;
    }
}
