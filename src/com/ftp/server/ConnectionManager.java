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

    public void removeClient(ClientConnection client) {
        for (int i = 0; i < clients.size(); i++) {
            if (clients.get(i).equals(client)) {
                clients.remove(i);
                return;
            }
        }
    }

    public ClientConnection getClient(String username, String password) {
        for (ClientConnection c : clients) {
            if (c.getUsername().equals(username) && c.getPassword().equals(password)) return c;
        }
        return null;
    }

    public List<ClientConnection> getClients() {
        return clients;
    }

    public void setClients(List<ClientConnection> clients) {
        this.clients = clients;
    }
}
