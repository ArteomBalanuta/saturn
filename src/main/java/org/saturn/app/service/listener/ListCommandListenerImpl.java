package org.saturn.app.service.listener;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.command.impl.ListUserCommandImpl;
import org.saturn.app.model.dto.User;
import org.saturn.app.service.ListCommandListener;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
        List<String> nickList = users.stream().map(user -> user.getTrip() + " " + user.getNick()).collect(Collectors.toList());
        if (users.isEmpty()) {
            mainEngine.outService.enqueueMessageForSending("@" + dto.author + " " + dto.channel + " is empty");
        } else {
            mainEngine.outService.enqueueMessageForSending("@" + dto.author + " " + nickList);
        }

        mainEngine.shareMessages();
    }
}
