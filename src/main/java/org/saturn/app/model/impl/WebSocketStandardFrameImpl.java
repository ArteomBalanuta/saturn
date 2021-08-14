package org.saturn.app.model.impl;

import org.saturn.app.model.WebSocketFrame;

import static java.lang.System.arraycopy;

public class WebSocketStandardFrameImpl implements WebSocketFrame {
    int finByte = 0b10000001;
    int payloadLength;

    byte[] textPayloadBytes;
    int[] textPayload;
    int[] payload = new int[256];

    public WebSocketStandardFrameImpl(String text) {
        this.textPayload = applyMaskToTextPayload(text, maskingKey);
        this.payloadLength = swapHighestBit(text.length()); /* Highest bit swap - in order to set Mask bit */

        this.payload[0] = finByte;
        this.payload[1] = payloadLength;
        this.payload[2] = maskingKey[0];
        this.payload[3] = maskingKey[1];
        this.payload[4] = maskingKey[2];
        this.payload[5] = maskingKey[3];

        arraycopy(this.textPayload, 0, this.payload, 6, this.textPayload.length);
    }

    /* TODO: Masked Server response is not supported */
    public WebSocketStandardFrameImpl(byte[] bytes) {
        this.finByte = bytes[0];
        this.payloadLength = bytes[1];

        /* Fill the payload field from the incoming packet */
        if (payloadLength > 0) {
            byte[] textPayload = new byte[payloadLength];
            System.arraycopy(bytes, 2, textPayload, 0, payloadLength);
            this.textPayloadBytes = textPayload;
        }
    }

    @Override
    public byte[] getWebSocketReadTextBytes() {
        return this.textPayloadBytes;
    }

    @Override
    public byte[] getWebSocketWriteTextBytes() {
        int wsHeaderSize = 6;
        int totalPacketLength = wsHeaderSize + this.textPayload.length;
        int[] buffer = new int[totalPacketLength];

        arraycopy(this.payload, 0, buffer, 0, totalPacketLength);

        byte[] shrinkedBuffer = new byte[totalPacketLength];
        for (int i = 0; i < totalPacketLength; i++) {
            shrinkedBuffer[i] = (byte) buffer[i];
        }
        return shrinkedBuffer;
    }


}
