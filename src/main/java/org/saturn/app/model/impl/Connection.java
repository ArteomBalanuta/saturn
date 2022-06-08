package org.saturn.app.model.impl;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class Connection {
    private boolean isMain;

    private final String uri;
    private final int port;

    private InputStream inputStream;
    private OutputStream outputStream;

    public Connection(String uri, int port, boolean isMain) {
        this.isMain = isMain;
        this.uri = uri;
        this.port = port;

        try {
            var socket = new Socket(uri, port);
            var factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            var sslSocket = (SSLSocket) factory.createSocket(socket, uri, port, false);

            setUpConnectionStreams(sslSocket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setUpConnectionStreams(Socket sslSocket) throws IOException {
        inputStream = sslSocket.getInputStream();
        outputStream = sslSocket.getOutputStream();
    }

    public ReadDto read() {
        ReadDto readDto = new ReadDto();
        readDto.bytes = new byte[8192];
        try {
            readDto.nrOfBytesRead = inputStream.read(readDto.bytes, 0, readDto.bytes.length);
        } catch (IOException e) {
            throw new RuntimeException("Connection is closed for " + (isMain ? " main thread " : "list thread "));
        }

        return readDto;
    }

    public void write(byte[] bytes) throws IOException {
        outputStream.write(bytes);
        outputStream.flush();
    }

    public void close() {
        try {
            inputStream.close();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
