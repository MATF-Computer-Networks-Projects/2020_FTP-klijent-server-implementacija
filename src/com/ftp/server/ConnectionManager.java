package com.ftp.server;

import java.util.LinkedList;
import java.util.List;

/**
 * This class contains list of clients represented as {@link ClientConnection} connected to server. it has basic command as regular list.
 *
 * @author Stefan
 */
public class ConnectionManager {
    List<ClientConnection> clients;

    public ConnectionManager() {
        clients = new LinkedList<>();
    }

    public void addClient(ClientConnection client) {
        clients.add(client);
    }

    public void removeClient(ClientConnection currentClient) {
        clients.remove(currentClient);
    }
}
