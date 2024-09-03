package org.saturn.app.model.command.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.annotation.CommandAliases;
import org.saturn.app.model.command.UserCommandBaseImpl;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;

@Slf4j
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
    public Role getAuthorizedRole() {
        return Role.REGULAR;
    }

    @Override
    public void execute() {
        String author = chatMessage.getNick();
        super.engine.getOutService().enqueueMessageForSending(author, String.format(help, prefix, prefix, prefix), isWhisper());
        log.info("Executed [help] command by user: {}", author);
    }
    //.ddg   ​  
    //    
    //​
    public final String help =
                    "Prefix: %s \\n" +
                    "Commands:\\n" +
                    "\u200B help,h \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B - prints this output \\n" +
                    "\u200B say,echo <text>\u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B - echoes the input \\n" +
                    "\u200B sub,subscribe\u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B - subscribe on nicks,trips,hashes on joining users \\n" +
                    "\u200B note <text>\u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B - saves a note \\n" +
                    "\u200B notes\u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B - lists your saved notes \\n" +
                    "\u200B notes purge\u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B - removes all notes \\n" +
                    "\u200B mail,msg <nick> <text>\u200B - sends a message to <nick> \\n" +
                    "\u200B info,i <nick>\u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B - whispers back user's trip, hash\\n" +
                    "\u200B list <channel_name>\u200B \u200B \u200B \u200B - prints hash,trip,nicks of users in the channel\\n" +
                    "\u200B weather <city>\u200B \u200B \u200B \u200B - some weather data\\n" +
//                    "\u200B \\n" +
                    "Whitelisted user commands:\\n" +
                    "\u200B kick,k <nick>\u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B - kicks the user\\n" +
                    "\u200B ban <nick|trip|hash>\u200B \u200B \u200B - bans the user by either nick,trip or hash\\n" +
                    "\u200B msgroom <room> <text>\u200B \u200B - sends the mail to specified room\\n" +
                    "\u200B sql <SQL>\u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B \u200B - executes the sql against bot\'s database\\n" +
//                    "\u200B \\n" +
                    "Whisper supported commands (input, output is whispered):\\n" +
                    "\u200B /whisper @orangesun [mail,msg] <nick> <text> - sends a message to <nick>\\n" +
//                    "\u200B \\n" +
                    "Examples:\\n" +
                    "\u200B %slist programming \\n" +
                    "\u200B %smail santa Get me a native java compiler";

    public static final String SOLID =
            "```Text \\n" + "S - single responsibility principle \\n"
                    + "O - open-close principle \\n" + "L - liskov substitution principle \\n"
                    + "I - interface segregation principle \\n" + "D - dependency inversion principle \\n"
                    + "``` \\n";
}
