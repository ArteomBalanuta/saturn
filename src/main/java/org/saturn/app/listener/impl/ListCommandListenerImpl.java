package org.saturn.app.listener.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.command.impl.ListUserCommandImpl;
import org.saturn.app.model.dto.User;
import org.saturn.app.service.ListCommandListener;

import java.util.List;

import static org.saturn.app.model.command.impl.ListUserCommandImpl.printUsers;

public class ListCommandListenerImpl implements ListCommandListener {
    private final ListUserCommandImpl.Dto dto;

    public ListCommandListenerImpl(ListUserCommandImpl.Dto dto) {
        this.dto = dto;
    }

    @Override
    public String getListenerName() {
        return "listcommand";
    }

    @Override
    public void notify(List<User> users) {
        EngineImpl mainEngine = dto.engine;
        if (users.isEmpty()) {
            mainEngine.outService.enqueueMessageForSending("@" + dto.author + " " + dto.channel + " is empty");
        } else {
            printUsers(dto.author, users,  mainEngine.outService);
        }

        mainEngine.shareMessages();
    }
}
