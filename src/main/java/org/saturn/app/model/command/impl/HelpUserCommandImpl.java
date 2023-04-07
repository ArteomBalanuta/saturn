package org.saturn.app.model.command.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;

@CommandAliases(aliases = {"help", "h"})
public class HelpUserCommandImpl extends UserCommandBaseImpl {
    private final List<String> aliases = new ArrayList<>();
    private String prefix;

    public HelpUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
        super(message, engine, List.of("x"));
        super.setAliases(this.getAliases());

        this.aliases.addAll(aliases);

        if (super.engine.getConfig() != null) {
            prefix = super.engine.getConfig().getString("cmdPrefix");
        }
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
        super.engine.getOutService().enqueueMessageForSending(String.format(help, prefix, prefix, prefix));
    }
    //.ddg   ​  
    //    
    //​
    public final String help =
                    "Prefix: %s \\n" +
                    "Commands:\\n" +
                    "\u200B help,h - prints this output \\n" +
                    "\u200B say,echo <text> - echoes the input \\n" +
                    "\u200B sub,subscribe - you will receive nicks, trips, hashes for each joining user \\n" +
                    "\u200B note <text> - saves a note \\n" +
                    "\u200B notes - lists your saved notes \\n" +
                    "\u200B notes purge - removes all notes \\n" +
                    "\u200B mail,msg <nick> <text> - sends a message to <nick> \\n" +
                    "\u200B info,i <nick>, - whispers back user's trip, hash \\n" +
                    "\u200B list <channel_name> - prints hash, trip, nicks of active users in the channel\\n" +
                    "\u200B \\n" +
                    "Whitelisted user commands:\\n" +
                    "\u200B kick,k <nick> - kicks the user \\n" +
                    "\u200B ban <nick|trip|hash> - bans the user by either nick,trip or hash \\n" +
                    "\u200B sql <SQL> - executes the sql against bot\'s database\\n" +
                    "\u200B \\n" +
                    "Whisper supported commands (input, output is whispered):\\n" +
                    "\u200B /whisper @orangesun mail,msg <nick> <text> - sends a message to <nick> \\n" +
                    "\u200B \\n" +
                    "Examples: \\n" +
                    "\u200B %slist programming \\n" +
                    "\u200B %smail santa Get me a native java compiler \\n" +
                    "  \\n";

    public static final String SOLID =
            "```Text \\n" + "S - single responsibility principle \\n"
                    + "O - open-close principle \\n" + "L - liskov substitution principle \\n"
                    + "I - interface segregation principle \\n" + "D - dependency inversion principle \\n"
                    + "``` \\n";
}
