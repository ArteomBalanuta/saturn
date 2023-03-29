package org.saturn.app.util;

public class Constants {
    public static final int THREAD_NUMBER = 4;
    
    public static String CHAT_JSON = "{ \"cmd\": \"chat\", \"text\": \"%s\"}";
    public static String JOIN_JSON = "{ \"cmd\": \"join\", \"channel\": \"%s\", \"nick\": \"%s#%s\" }";
    
    public static final String HELP_RESPONSE = "\\n" +
            "help, " +
            "drrudi, " +
            "babakiueria, " +
            "scp, " +
            "solid, " +
            "rust, " +
            "note java_is_good, " +
            "notes, " +
            "notes purge, " +
            "ping, " +
            "info, " +
            "list channel_name \\n";
    
    public static final String SOLID =
            "```Text \\n" + "S - single responsibility principle \\n"
                    + "O - open-close principle \\n" + "L - liskov substitution principle \\n"
                    + "I - interface segregation principle \\n" + "D - dependency inversion principle \\n"
                    + "``` \\n";
}
