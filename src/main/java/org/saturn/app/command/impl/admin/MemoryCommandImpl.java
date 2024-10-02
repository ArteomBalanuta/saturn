package org.saturn.app.command.impl.admin;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.impl.OutService;
import org.saturn.app.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.saturn.app.util.Util.getWhiteListedTrips;

@Slf4j
@CommandAliases(aliases = {"replica", "bot", "agent"})
public class MemoryCommandImpl extends UserCommandBaseImpl {
    private final OutService outService;
    private final List<String> aliases = new ArrayList<>();

    public MemoryCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
        super(message, engine, getWhiteListedTrips(engine));
        super.setAliases(this.getAliases());
        this.outService = super.engine.outService;
        this.aliases.addAll(aliases);
    }

    @Override
    public Optional<Status> execute() {
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();

        long totalMemory = runtime.totalMemory();    // Total memory the JVM is using
        long freeMemory = runtime.freeMemory();      // Free memory within the JVM
        long usedMemory = totalMemory - freeMemory;  // Memory used by the JVM
        long maxMemory = runtime.maxMemory();        // Maximum memory the JVM can use

        String memPayload = "JVM Used Memory: " + (usedMemory / (1024 * 1024)) + " MB \\n" +
                        "JVM Free Memory: " + (freeMemory / (1024 * 1024)) + " MB \\n" +
                        "JVM Total Memory: " + (totalMemory / (1024 * 1024)) + " MB \\n" +
                        "JVM Max Memory: " + (maxMemory / (1024 * 1024)) + " MB \\n";

        String payload = Util.alignWithWhiteSpace(memPayload, ":","\u2009", false);
        String author = chatMessage.getNick();

        outService.enqueueMessageForSending(author, payload, isWhisper());

        log.info("Executed [memory] command by user: {}", author);

        return Optional.of(Status.SUCCESSFUL);
    }


    // Print memory usage in megabytes

}
