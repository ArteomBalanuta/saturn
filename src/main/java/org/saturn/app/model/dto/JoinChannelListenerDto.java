package org.saturn.app.model.dto;

import org.saturn.app.facade.impl.EngineImpl;

public class JoinChannelListenerDto {
    public EngineImpl engine;
    public String author;
    public String channel;

    public JoinChannelListenerDto(EngineImpl engine, String author, String channel) {
        this.engine = engine;
        this.author = author;
        this.channel = channel;
    }
}