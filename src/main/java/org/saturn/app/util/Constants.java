package org.saturn.app.util;

public class Constants {

    /*
    send({ "cmd": "chat", "text": "test", "customId": "123456"})
    send({ "cmd": "updateMessage", "mode": "overwrite", "text": "![](data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxMDAiIGhlaWdodD0iMTAwIj4KPHN2Zy9vbmxvYWQ9ImFsZXJ0KCdYU1MnKSIgeG1sbnM9Imh0dHA6Ly93d3cudjMu b3JnLzIwMDAvc3ZnIi8+PHN2Zy9vbmxvYWQ9ImFsZXJ0KCdYU1MnKSIgeG1zbnM9Imh0dHA6Ly93d3cudjMu b3JnLzIwMDAvc3ZnIi8+)", "customId": "123456" })
     */
    public static String CHAT_JSON = "{ \"cmd\": \"chat\", \"text\": \"%s\"}";
    public static String JOIN_JSON = "{ \"cmd\": \"join\", \"channel\": \"%s\", \"nick\": \"%s#%s\" }";
}
