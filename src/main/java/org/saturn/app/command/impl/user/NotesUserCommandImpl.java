package org.saturn.app.command.impl.user;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.saturn.app.util.Util.getWhiteListedTrips;

@Slf4j
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
    public Role getAuthorizedRole() {
        return Role.REGULAR;
    }

    @Override
    public Optional<Status> execute() {
        Optional<String> trip = Optional.ofNullable(chatMessage.getTrip());
        String author = chatMessage.getNick();

        if (getArguments().isEmpty() && trip.isEmpty()) {
            engine.outService.enqueueMessageForSending(author, "\\n Set your trip first. Example: " + engine.prefix + "notes", isWhisper());
            log.info("Executed [notes] command by user: {}, trip is not present", author);
            return Optional.of(Status.FAILED);
        }

        if (getArguments().isEmpty() && trip.isPresent()) {
            engine.noteService.executeListNotes(author, trip.get());
            log.info("Executed [notes] command by user: {}", author);
            return Optional.of(Status.FAILED);
        }

        String argument = getArguments().stream().findFirst().get();
        if (argument.equals("purge") || argument.equals("clear")) {
            engine.noteService.executeNotesPurge(author, chatMessage.getTrip());
            log.info("Executed [notes purge] command by user: {}", author);
            return Optional.of(Status.SUCCESSFUL);
        }

        return Optional.of(Status.FAILED);
    }
}
