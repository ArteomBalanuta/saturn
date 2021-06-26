package org.saturn.app.model;

public interface WebSocketFrame {

    byte[] getWebSocketReadTextBytes();

    byte[] getWebSocketWriteTextBytes();

    int[] maskingKey = {0x7e, 0xbc, 0xab, 0x7a};

    default int[] applyMaskToTextPayload(String text, int[] maskByteArr) {
        int[] encodedText = new int[text.length()];
        int j;
        for (int i = 0; i < text.length(); i++) {
            j = i % 4;
            encodedText[i] = (text.charAt(i) ^ maskByteArr[j]);
        }

        return encodedText;
    }

    default int swapHighestBit(int payloadSize) {
        return payloadSize | 0b10000000;
    }
}
