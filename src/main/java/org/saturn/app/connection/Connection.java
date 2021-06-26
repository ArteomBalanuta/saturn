package org.saturn.app.connection;

import org.saturn.app.model.impl.ReadDto;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class Connection {
    private final String uri;
    private final int port;

    private static InputStream inputStream;
    private static OutputStream outputStream;

    public Connection(String uri, int port) {
        this.uri = uri;
        this.port = port;

        try {
            var socket = new Socket(uri, port);
            var factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            var sslSocket = (SSLSocket) factory.createSocket(socket, uri, port, false);

            setUpStreams(sslSocket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setUpStreams(Socket sslSocket) throws IOException {
        inputStream = sslSocket.getInputStream();
        outputStream = sslSocket.getOutputStream();
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public static ReadDto read() {
        ReadDto readDto = new ReadDto();
        readDto.bytes = new byte[2048];
        try {
            readDto.nrOfBytesRead = inputStream.read(readDto.bytes, 0, readDto.bytes.length);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return readDto;
    }

    public void write(byte[] bytes) throws IOException {
        outputStream.write(bytes);
        outputStream.flush();
    }
}
