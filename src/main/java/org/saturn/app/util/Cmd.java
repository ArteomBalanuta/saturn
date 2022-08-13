package org.saturn.app.util;

public enum Cmd {
    HELP("help"), 
    FISH("fish"), 
    LIST("list"),
    MSGCHANNEL("msgchannel"),
    BABAKIUERIA("babakiueria"), 
    DRRUDI("drrudi"), 
    RUST("rust"), 
    PING("ping"), 
    SOLID("solid"), 
    SCP("scp"), 
    NOTE("note "), 
    NOTES("notes"),
    NOTESPURGE("notes purge"),
    SEARCH("s "),
    DOG("dog"),
    SQL("sql "),
    BAN("ban "),
    UNBAN("unban "),
    BANLIST("banlist"),
    INFO("info "),
    SUB("sub"),
    VOTEKICK("votekick "),
    VOTE("vote"),
    SENTRY("antipest"),
    ECHO("echo "),
    MAIL("mail ");

    private final String cmdCode;

    Cmd(String cmdCode) {
        this.cmdCode = cmdCode;
    }

    public String getCmdCode() {
        return this.cmdCode;
    }
}
