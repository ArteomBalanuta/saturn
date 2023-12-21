package org.saturn.app.model.command.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.saturn.app.util.Util.getWhiteListedTrips;

@CommandAliases(aliases = {"notes"})
public class NotesUserCommandImpl extends UserCommandBaseImpl {
    private final List<String> aliases = new ArrayList<>();

    public NotesUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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

        if (getArguments().isEmpty()) {
            engine.noteService.executeListNotes(chatMessage.getNick(), trip.get());
        } else if (getArguments().stream().findFirst().get().equals("purge") || getArguments().stream().findFirst().get().equals("clear"))  {
            engine.getNoteService().executeNotesPurge(chatMessage.getNick(), chatMessage.getTrip());
        }
    }
}
