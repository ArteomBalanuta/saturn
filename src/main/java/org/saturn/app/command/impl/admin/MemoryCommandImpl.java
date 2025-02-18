package org.saturn.app.command.impl.admin;

import static org.saturn.app.util.Util.getAdminAndUserTrips;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.service.impl.OutService;
import org.saturn.app.util.Util;

@Slf4j
@CommandAliases(aliases = {"mem", "memory", "memstats"})
public class MemoryCommandImpl extends UserCommandBaseImpl {
  private final OutService outService;

  public MemoryCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminAndUserTrips(engine));
    super.setAliases(aliases);
    this.outService = super.engine.outService;
  }

  @Override
  public Optional<Status> execute() {
    Runtime runtime = Runtime.getRuntime();
    runtime.gc();

    long totalMemory = runtime.totalMemory();
    long freeMemory = runtime.freeMemory();
    long usedMemory = totalMemory - freeMemory;
    long maxMemory = runtime.maxMemory();

    String memPayload =
        "JVM Used Memory: "
            + (usedMemory / (1024 * 1024))
            + " MB \\n"
            + "JVM Free Memory: "
            + (freeMemory / (1024 * 1024))
            + " MB \\n"
            + "JVM Total Memory: "
            + (totalMemory / (1024 * 1024))
            + " MB \\n"
            + "JVM Max Memory: "
            + (maxMemory / (1024 * 1024))
            + " MB \\n";

    String payload = Util.alignWithWhiteSpace(memPayload, ":", "\u2009", false);
    String author = chatMessage.getNick();

    outService.enqueueMessageForSending(author, payload, isWhisper());
    log.info("Executed [memory] command by user: {}", author);

    return Optional.of(Status.SUCCESSFUL);
  }
}
