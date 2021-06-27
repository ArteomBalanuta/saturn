package org.saturn.app.util;

public class Constants {

    public static final int THREAD_NUMBER = 4;
    public static final int STANDARD_FRAME_MAX_TEXT_PAYLOAD_SIZE = 125;

    /* chat-ws */
    public static final String UPGRADE_REQUEST = "GET /chat-ws HTTP/1.1\r\n" +
            "User-Agent: Mozilla/5.0 (X11; Linux x86_64; rv:78.0) Gecko/20100101 Firefox/78.0\r\n" +
            "Host: hack.chat\r\n" +
            "Accept: */*\r\n" +
            "Accept-Language: en-US,en;q=0.5\r\n" +
            "Accept-Encoding: gzip, deflate\r\n" +
            "Sec-WebSocket-Version: 13\r\n" +
            "Sec-WebSocket-Extensions: permessage-deflate\r\n" +
            "Sec-WebSocket-Key: iu62cI/zQ0qwWXOzlhfDEA==\r\n" +
            "Connection: keep-alive, Upgrade\r\n" +
            "Pragma: no-cache\r\n" +
            "Cache-Control: no-cache\r\n" +
            "Upgrade: websocket\r\n\r\n";

    public static String CHAT_JSON = "{ \"cmd\": \"chat\", \"text\": \"%s\"}";
    public static String JOIN_JSON = "{ \"cmd\": \"join\", \"channel\": \"%s\", \"nick\": \"%s\" }";
}
