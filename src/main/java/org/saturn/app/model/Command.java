package org.saturn.app.model;

import org.saturn.app.util.Cmd;

import java.util.List;

public interface Command {
    String getCommand();
    List<String> getArguments();
    boolean is(Cmd cmd);
}
