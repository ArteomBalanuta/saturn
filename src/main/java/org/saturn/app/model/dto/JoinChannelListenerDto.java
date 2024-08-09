package org.saturn.app.model.dto;

import org.saturn.app.facade.impl.EngineImpl;

public class JoinChannelListenerDto {
    public EngineImpl mainEngine;
    public EngineImpl slaveEngine;
    public String author;
    public String channel;
    public String target;
    public String destinationRoom;

    public JoinChannelListenerDto(EngineImpl engine, EngineImpl slaveEngine, String author, String channel) {
        this.mainEngine = engine;
        this.slaveEngine = slaveEngine;
        this.author = author;
        this.channel = channel;
    }
}