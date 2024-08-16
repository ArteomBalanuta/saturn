package org.saturn.app.model.dto;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

public class JoinChannelListenerDto {
    public EngineImpl mainEngine;
    public EngineImpl slaveEngine;
    public String author;
    public String channel;
    public String target;
    public String destinationRoom;

    public ChatMessage chatMessage;

    public JoinChannelListenerDto(EngineImpl engine, EngineImpl slaveEngine, String author, String channel) {
        this.mainEngine = engine;
        this.slaveEngine = slaveEngine;
        this.author = author;
        this.channel = channel;
    }
}