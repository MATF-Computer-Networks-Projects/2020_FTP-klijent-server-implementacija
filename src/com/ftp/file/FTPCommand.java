package com.ftp.file;

/**
 * This enum contains basic FTP commands used to communicate between remote server and client. Commands {@link FTPCommand#SUCCESS}
 * and {@link FTPCommand#FAILURE} are only server's command and they are used as response on given command from client. Some commands
 * are excluded like OPEN because connection opens on connecting client to server and authenticating with server is done
 * immediately after connecting to it by default (on connecting credentials are sent automatically as ASCII code)
 *
 * @author Stefan
 */
public enum FTPCommand {
    /**
     * Get file from remote server (downloading)
     */
    GET,

    /**
     * Send file to remote server (uploading)
     */
    PUT,

    /**
     * Create new folder on remote server
     */
    MKDIR,

    /**
     * Delete folder (with subfolders and files) or file on remote server.
     * Command DEL for removing files is integrated in {@link FTPCommand#RMDIR}
     */
    RMDIR,

    /**
     * Get tree view from remote server (server's file explorer)
     */
    TREE,

    /**
     * Server command. Given command was executed successfully
     */
    SUCCESS,

    /**
     * Server command. Given command was not executed successfully and response code and message will be sent to client
     */
    FAILURE,

    /**
     * Closing connection between client and server
     */
    CLOSE;
}
