package org.saturn.app.model.impl;

import org.saturn.app.model.WebSocketFrame;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static java.lang.System.arraycopy;

public class WebSocketExtendedFrameImpl implements WebSocketFrame {
    int finByte = 0b10000001;
    int extendedPayloadLengthFlag = 0b11111110;

    byte[] textPayloadBytes;
    int[] textPayload;
    int[] payload = new int[8192];

    public WebSocketExtendedFrameImpl() {
    }

    public WebSocketExtendedFrameImpl(String text) {
        this.textPayload = applyMaskToTextPayload(text, maskingKey);

        this.payload[0] = finByte;
        this.payload[1] = extendedPayloadLengthFlag;

        short payloadSize = Integer.valueOf(text.length()).shortValue();
        byte[] payloadSizeBytes = new byte[]{(byte) ((payloadSize >> 8) & 0xFF), (byte) (payloadSize & 0xFF)};

        this.payload[2] = payloadSizeBytes[0];
        this.payload[3] = payloadSizeBytes[1];

        this.payload[4] = maskingKey[0];
        this.payload[5] = maskingKey[1];
        this.payload[6] = maskingKey[2];
        this.payload[7] = maskingKey[3];

        System.arraycopy(this.textPayload, 0, this.payload, 8, this.textPayload.length);
    }

    /* Masked Server response is not supported */
    public WebSocketExtendedFrameImpl(byte[] bytes) {
        this.finByte = bytes[0];
        this.extendedPayloadLengthFlag = bytes[1];

        ByteBuffer bb = ByteBuffer.allocate(2);
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.put(bytes[2]);
        bb.put(bytes[3]);
        short payloadLength = bb.getShort(0);

        /* Fill the payload field from the incoming packet */
        if (payloadLength > 0) {
            byte[] textPayload = new byte[payloadLength];
            for (int i = 0; i < payloadLength; i++) {
                textPayload[i] = bytes[4 + i];
                this.textPayloadBytes = textPayload;
            }
        }
    }

    @Override
    public byte[] getWebSocketReadTextBytes() {
        return this.textPayloadBytes;
    }

    @Override
    public byte[] getWebSocketWriteTextBytes() {
        int wsHeaderSize = 8;
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
