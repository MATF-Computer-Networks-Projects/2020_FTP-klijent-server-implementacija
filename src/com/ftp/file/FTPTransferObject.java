package com.ftp.file;

import java.io.File;
import java.io.Serializable;

/**
 * This is main class used for communicating between server and client. It contains file parameters,
 * client username and password needed for authorizing requests, {@link FTPCommand} for executing request on server side, response code and message that
 * are returned from server after executing command, and additional data as byte array that is primarily used for sending
 * file explorer as {@link javafx.scene.control.TreeItem} to client.
 *
 * @author Stefan
 */
public class FTPTransferObject implements Serializable {
    private final String username;
    private final String password;
    private final FTPCommand command;
    private final Integer responseCode;
    private final String responseMessage;
    private long fileSize;
    private String name;
    private File pathClient;
    private File pathServer;
    private byte[] additionalData;

    public FTPTransferObject(String username, String password, FTPCommand command, Integer responseCode, String responseMessage, byte[] additionalData) {
        this.username = username;
        this.password = password;
        this.command = command;
        this.responseCode = responseCode;
        this.responseMessage = responseMessage;
        this.additionalData = additionalData;
    }

    public FTPCommand getCommand() {
        return command;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public File getPathClient() {
        return pathClient;
    }

    public void setPathClient(File pathClient) {
        this.pathClient = pathClient;
    }

    public File getPathServer() {
        return pathServer;
    }

    public void setPathServer(File pathServer) {
        this.pathServer = pathServer;
    }

    public byte[] getAdditionalData() {
        return additionalData;
    }

    public void setAdditionalData(byte[] additionalData) {
        this.additionalData = additionalData;
    }
}
