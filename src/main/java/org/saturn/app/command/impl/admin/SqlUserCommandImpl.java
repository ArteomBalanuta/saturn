package org.saturn.app.command.impl.admin;

import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.saturn.app.util.Util.getAdminTrips;

@CommandAliases(aliases = {"sql"})
public class SqlUserCommandImpl extends UserCommandBaseImpl {

    private final List<String> aliases = new ArrayList<>();

    public SqlUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
        super(message, engine, getAdminTrips(engine));
        super.setAliases(this.getAliases());
        this.aliases.addAll(aliases);
    }

    @Override
    public List<String> getAliases() {
        return this.aliases;
    }
    @Override
    public List<String> getArguments() {
        return super.getArguments();
    }
    @Override
    public Role getAuthorizedRole() {
        return Role.ADMIN;
    }

    @Override
    public Optional<Status> execute() {
        String cmd = chatMessage.getText();
        String result = engine.sqlService.executeSql(cmd, true);
        engine.outService.enqueueMessageForSending(chatMessage.getNick(),  "Result: \\n" + result, isWhisper());
        return Optional.of(Status.SUCCESSFUL);
    }
}
