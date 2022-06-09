package org.saturn.app.util;

public enum Cmd {
    HELP("help"), 
    FISH("fish"), 
    LIST("list"),
    MSG_CHANNEL("msgchannel"),
    BABAKIUERIA("babakiueria"), 
    DRRUDI("drrudi"), 
    RUST("rust"), 
    PING("ping"), 
    SOLID("solid"), 
    SCP("scp"), 
    NOTE("note "), 
    NOTES("notes"),
    NOTES_PURGE("notes purge"), 
    SEARCH("s "),
    DOG("dog"),
    SQL("sql "),
    BAN("ban "),
    UNBAN("unban "),
    BANLIST("banlist"),
    INFO("info "),
    VOTE_KICK("votekick "),
    VOTE("vote"),
    SENTRY("antipest"),
    MAIL("mail ");

    private final String cmdCode;

    Cmd(String cmdCode) {
        this.cmdCode = cmdCode;
    }

    public String getCmdCode() {
        return this.cmdCode;
    }
}
