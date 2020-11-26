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
    private String username;
    private String password;
    private Socket socket;
    private byte[] key;
    private InputStream inStream = null;
    private OutputStream outStream = null;

    public ClientConnection(String username, String password, Socket socket, byte[] key) {
        this.username = username;
        this.password = password;
        this.socket = socket;
        this.key = key;
    }

    public ClientConnection(String username, String password, Socket socket) {
        this.username = username;
        this.password = password;
        this.socket = socket;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
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

    public byte[] getKey() {
        return key;
    }

    public void setKey(byte[] key) {
        this.key = key;
    }

    public Socket getSocket() {
        return socket;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    public String getClientIP() {
        return socket.getRemoteSocketAddress().toString();
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

    @Override
    public String toString() {
        return "ClientConnection{" +
                "username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", inStream=" + inStream +
                ", outStream=" + outStream +
                '}';
    }
}
