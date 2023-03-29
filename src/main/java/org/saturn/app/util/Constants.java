package org.saturn.app.util;

public class Constants {
    public static final int THREAD_NUMBER = 4;
    
    public static String CHAT_JSON = "{ \"cmd\": \"chat\", \"text\": \"%s\"}";
    public static String JOIN_JSON = "{ \"cmd\": \"join\", \"channel\": \"%s\", \"nick\": \"%s#%s\" }";
}
