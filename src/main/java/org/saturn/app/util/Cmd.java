package org.saturn.app.util;

public enum Cmd {
    HELP("help"), 
    FISH("fish"), 
    LIST("list"), 
    BABAKIUERIA("babakiueria"), 
    DRRUDI("drrudi"), 
    RUST("rust"), 
    PING("ping"), 
    SOLID("solid"), 
    SCP("scp"), 
    NOTE("note "), 
    NOTES("notes"),
    NOTES_PURGE("notes purge"), 
    SEARCH("s ");

    private final String cmdCode;

    Cmd(String cmdCode) {
        this.cmdCode = cmdCode;
    }

    public String getCmdCode() {
        return this.cmdCode;
    }
}