package org.saturn.app.model.command.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.saturn.app.util.Util.getWhiteListedTrips;

@CommandAliases(aliases = {"note", "save"})
public class NoteUserCommandImpl extends UserCommandBaseImpl {
    private final List<String> aliases = new ArrayList<>();

    public NoteUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
        super(message, engine, getWhiteListedTrips(engine));
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
    public void execute() {
        Optional<String> trip = Optional.ofNullable(chatMessage.getTrip());
        boolean empty = getArguments().stream().findFirst().isEmpty();
        if (empty) {
            engine.getOutService().enqueueMessageForSending(chatMessage.getNick() + " ", engine.prefix + "note Jedi am I?!", isWhisper());
            return;
        }

        trip.ifPresent(s -> engine.noteService.save(s, stringsToString(getArguments())));

        engine.getOutService().enqueueMessageForSending(chatMessage.getNick(), " note saved!", isWhisper());
    }

    public String stringsToString(List<String> strings) {
        StringBuilder b = new StringBuilder();
        strings.forEach(string -> b.append(string).append(" "));
        return b.toString();
    }

}
