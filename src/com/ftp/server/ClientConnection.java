package com.ftp.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Objects;

/**
 * This class represents client. It contains client username,password and unique socket assigned to it. Input and output stream are derived from socket.
 * It contains methods for getting client data such as IP.
 *
 * @author Stefan
 */
public class ClientConnection {
    private final String username;
    private final String password;
    private final Socket socket;
    private String key;

    public ClientConnection(String username, String password, Socket socket) {
        this.username = username;
        this.password = password;
        this.socket = socket;
    }


    public InputStream getInStream() {
        try {
            return socket.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public OutputStream getOutStream() {
        try {
            return socket.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Socket getSocket() {
        return socket;
    }

    public String getClientIP() {
        return socket.getRemoteSocketAddress().toString().substring(1);
    }

    public String getUsername() {
        return username;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClientConnection that = (ClientConnection) o;
        return Objects.equals(username, that.username) &&
                Objects.equals(password, that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, password);
    }

}
