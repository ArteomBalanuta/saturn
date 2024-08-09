package org.saturn.app.listener.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.dto.JoinChannelListenerDto;
import org.saturn.app.model.dto.User;
import org.saturn.app.listener.JoinChannelListener;
import org.saturn.app.util.Util;

import java.util.List;

public class MsgChannelCommandListenerImpl implements JoinChannelListener {
    private final JoinChannelListenerDto dto;

    private Runnable operation;

    public MsgChannelCommandListenerImpl(JoinChannelListenerDto dto) {
        this.dto = dto;
    }

    @Override
    public String getListenerName() {
        return "msgChannelListener";
    }

    @Override
    public void notify(String jsonText) {
        List<User> users = Util.extractUsersFromJson(jsonText);
        EngineImpl mainEngine = dto.mainEngine;
        boolean onlyMeOnline = users.stream().allMatch(User::isIsMe);
        if (onlyMeOnline) {
            mainEngine.outService.enqueueMessageForSending("@" + dto.author + " " + dto.channel + " is empty");
        } else {
            if (this.operation != null) {
                this.operation.run();
            }
        }

        dto.slaveEngine.stop();

        mainEngine.shareMessages();
    }

    @Override
    public void setAction(Runnable runnable) {
        this.operation = runnable;
    }
}
