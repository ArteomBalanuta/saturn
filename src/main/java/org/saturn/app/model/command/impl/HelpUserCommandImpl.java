package org.saturn.app.model.command.impl;

import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.command.UserCommandBaseImpl;

import java.util.List;

public class HelpUserCommandImpl extends UserCommandBaseImpl {

    private String prefix;
    public HelpUserCommandImpl(EngineImpl engine, List<String> whiteListedTrips) {
        super(null, engine, whiteListedTrips);
        super.setCommandName(this.getCommandName());

        if (super.engine.getConfig() != null) {
            prefix = super.engine.getConfig().getString("cmdPrefix");
        }
    }

    @Override
    public String getCommandName() {
        return "help";
    }

    @Override
    public List<String> getArguments() {
        return super.getArguments();
    }

    @Override
    public void execute() {
        super.engine.getOutService().enqueueMessageForSending(String.format(help, prefix, prefix, prefix));
    }

    public final String help = "\\n" +
            "Prefix: " + "%s" + "\\n" +
            " - " + "\\n" +
            "Examples: " + "\\n" +
            "%s" + "list programming" + "\\n" +
            "%s" + "mail mercury hi there" + "\\n" +
            " - " + "\\n" +
            "Commands: " + "\\n" +
            "help" + "\\n" +
            "say <text>" + "\\n" +
            "note <text>, " + "\\n" +
            "list <channel_name> \\n" +
            " - " + "\\n" +
            "drrudi" + "\\n" +
            "babakiueria, " + "\\n" +
            "scp, " + "\\n" +
            "solid, " + "\\n" +
            " - " + "\\n" +
            "rust, " + "\\n" +
            "notes, " + "\\n" +
            "notes purge, " + "\\n" +
            "ping, " + "\\n" +
            "info, " + "\\n" ;


    public static final String SOLID =
            "```Text \\n" + "S - single responsibility principle \\n"
                    + "O - open-close principle \\n" + "L - liskov substitution principle \\n"
                    + "I - interface segregation principle \\n" + "D - dependency inversion principle \\n"
                    + "``` \\n";
}
