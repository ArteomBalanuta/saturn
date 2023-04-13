package org.saturn.app.listener.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.command.impl.ListUserCommandImpl;
import org.saturn.app.model.dto.JoinChannelListenerDto;
import org.saturn.app.model.dto.User;
import org.saturn.app.service.JoinChannelListener;

import java.util.List;

import static org.saturn.app.model.command.impl.ListUserCommandImpl.printUsers;

public class MsgChannelCommandListenerImpl implements JoinChannelListener {
    private final JoinChannelListenerDto dto;

    private Runnable operation;

    public MsgChannelCommandListenerImpl(JoinChannelListenerDto dto) {
        this.dto = dto;
    }

    @Override
    public String getListenerName() {
        return "msgchannel";
    }

    @Override
    public void setAction(Runnable runnable) {
        this.operation = runnable;
    }
    @Override
    public void notify(List<User> users) {
        EngineImpl mainEngine = dto.engine;
        if (users.isEmpty()) {
            mainEngine.outService.enqueueMessageForSending("@" + dto.author + " " + dto.channel + " is empty");
        } else {
            if(this.operation != null) {
                System.out.println("running operation");
                this.operation.run();
                System.out.println("stopped running operation");
            }
        }

        mainEngine.shareMessages();
    }
}
