package org.saturn.app.model.impl;

import org.saturn.app.model.Command;
import org.saturn.app.util.Cmd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandImpl implements Command {
    private final Cmd command;
    private final List<String> arguments = new ArrayList<>();
    
    public CommandImpl(String command) {
        if (command.contains(" ")) {
            this.command = Cmd.valueOf(command.substring(0, command.indexOf(" ")).trim().toUpperCase());
        } else {
            this.command = Cmd.valueOf(command.trim().toUpperCase());
        }
        String arguments = command.substring(command.indexOf(" ") + 1);
        if (!arguments.equals("") && !arguments.equals(" ")) {
            if (arguments.contains(" ")) {
                this.arguments.addAll(Arrays.asList(arguments.split(" ")));
            } else {
                this.arguments.add(arguments);
            }
        }
    }
    
    @Override
    public String getCommand() {
        return command.getCmdCode().trim();
    }
    
    @Override
    public List<String> getArguments() {
        return arguments;
    }
    
    @Override
    public boolean is(Cmd cmd) {
        return this.command.equals(cmd);
    }
}
