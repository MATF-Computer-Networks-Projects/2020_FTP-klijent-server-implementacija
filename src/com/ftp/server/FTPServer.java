package com.ftp.server;

import com.ftp.file.*;
import javafx.scene.control.TreeItem;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

/**
 * This is basic class that represents FTP server. It uses {@link Socket} for opening connection with given port and communicate with {@link FTPTransferObject}. It has 2 main threads,
 * one for reading and one for writing to socket input/output stream assigned to each client. Upon client connecting it reads username and password as plain {@link String}
 * and after that, if credentials are correct, client can get it's own socket, then runs read thread which is responsible for listening and reading {@link FTPTransferObject} from each client.
 * On client connected, server sends object to client that contains {@link TreeItem} with folders/files from it.
 * Thread for writing to socket output stream runs on command received from client, while reading thread for each client works infinitely (until client disconnect).
 *
 * @author Stefan
 */
public class FTPServer {
    private String username = "";
    private String password = "";
    private FolderTreeView ftv;
    private final ConnectionManager manager;

    public FTPServer() {
        manager = new ConnectionManager();
    }

    /**
     * This method opens connection on given port and server is ready for accepting connections. When client connects, server reads it's credentials,
     * and if they are correct server creates {@link ClientConnection} object and add client to list of active clients. Connected client will
     * get it's own socket and thread responsible for reading and writing to it's socket. After that first object with repository explorer
     * will be sent to client. On failed authentication server refuses connection.
     */
    public void createSocket() throws IOException {
        ftv = new FolderTreeView(new File(System.getProperty("user.dir")));
        ServerSocket serverSocket = new ServerSocket(3339);
        while (true) {
            Socket socket=null;
            ClientConnection currentClient;
            try {
                socket = serverSocket.accept();
                currentClient=authenticateUser(socket);
                if(currentClient!=null){
                    manager.addClient(currentClient);
                }
            } catch (IOException io) {
                io.printStackTrace();
                assert socket != null;
                socket.close();
                System.err.println("Client has been disconnected! Error: "+io.getMessage());
            }
        }

    }

    /**
     * This method is responsible for listening to each client socket and reading commands from it. After client sends {@link FTPTransferObject} server
     * receives it and reads {@link FTPCommand}. Depends on command certain actions are performed, some of them locally some of them returns file to client.
     * Each package sent through socket is authenticated every time to avoid unattended access to server.
     *
     * @param client The client for whom this thread is responsible
     */
    public void readFromSocket(ClientConnection client) {
        Thread readThread = new Thread(() -> {
            while (client.getSocket().isConnected()) {
                try {
                    synchronized (client.getSocket().getInputStream()) {
                        FTPTransferObject readObject = null;
                        try {
                            readObject = readObjectFromStream(client);
                            readFileFromStream(client, readObject);
                        } catch (IOException | ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                        assert readObject != null;
                        if (readObject.getCommand().equals(FTPCommand.GET)) {
                            writeToSocket(client, readObject.getPathServer(), FTPCommand.GET, 1, "File sent successfully");
                        }
                        if (readObject.getCommand().equals(FTPCommand.CLOSE)) {
                            System.err.println("Client requested disconnect. Disconnecting "+client.getClientIP());
                            client.getSocket().close();
                            manager.removeClient(client);
                            return;
                        }
                        if (readObject.getCommand().equals(FTPCommand.MKDIR)) {
                            createFolder(readObject.getPathServer());
                            writeToSocket(client, null, FTPCommand.SUCCESS, 1, "Folder created successfully");
                        }
                        if (readObject.getCommand().equals(FTPCommand.RMDIR)) {
                            try {
                                deleteFolder(readObject.getPathServer());
                                writeToSocket(client, readObject.getPathServer(), FTPCommand.SUCCESS, 1, "Folder/file deleted successfully");
                            } catch (IOException e) {
                                e.printStackTrace();
                                writeToSocket(client, readObject.getPathServer(), FTPCommand.FAILURE, -1, "Failed to delete file/folder");
                            }
                        }
                        System.out.println(readObject);
                    }
                } catch (Exception e) {
                    System.err.println("Client "+client.getClientIP()+" disconnected.");
                    manager.removeClient(client);
                    try {
                        client.getSocket().close();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                    return;
                }
            }
        });
        readThread.setPriority(Thread.MAX_PRIORITY);
        readThread.start();
    }

    /**
     * This method writes object to each client. It can contain file or just response message after executing file deletion or folder creation for example.
     *
     * @param client          The client for whom this thread is responsible
     * @param path            Path from local file
     * @param command         {@link FTPCommand#SUCCESS} or {@link FTPCommand#FAILURE} depends on command execution
     * @param response        Response code
     * @param responseMessage Response message
     */
    public void writeToSocket(ClientConnection client, File path, FTPCommand command, Integer response, String responseMessage) {
        Thread writeThread = new Thread(() -> {
            try {
                Thread.sleep(100);
                writeObjectToStream(client, null, path, command, response, responseMessage);
                writeFileToStream(client, path, !command.equals(FTPCommand.GET));
                client.getOutStream().flush();
            } catch (Exception e) {
                System.err.println("Failed to send data to client. It will be disconnected");
                try {
                    client.getSocket().close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
                manager.removeClient(client);
            }

        });
        writeThread.setPriority(Thread.MAX_PRIORITY);
        writeThread.start();
        System.out.println("sent requested ");
    }

    /**
     * This method is responsible for reading object from client input stream. First it reads size of object
     * represented as {@link String}, then reads object with that size.
     *
     * @param client Client which stream needs to be read
     * @return Read object
     */
    public FTPTransferObject readObjectFromStream(ClientConnection client) throws IOException, ClassNotFoundException {
        System.out.println("readObjectFromStream");
        FTPTransferObject readedObject;
        byte[] b = new byte[16];
        int i = client.getInStream().read(b, 0, 16);
        if (i == 0) return null; //TODO 1
        int size = Integer.parseInt((new String(b)).trim());
        byte[] objInputArray = new byte[size];
        i = client.getInStream().read(objInputArray, 0, size);
        if (i == 0) return null; //TODO 1
        ByteArrayInputStream bis = new ByteArrayInputStream(Objects.requireNonNull(AES.decrypt(objInputArray, new String(client.getKey()))));
        ObjectInput in = new ObjectInputStream(bis);
        readedObject = (FTPTransferObject) in.readObject();
        bis.close();
        in.close();
        return readedObject;
    }

    public void readFileFromStream(ClientConnection client, FTPTransferObject readObject) throws IOException {
        System.out.println("readFileFromStream");
        if (readObject.getFileSize() == 0) {
            int i = client.getInStream().read(new byte[1]);//TODO 1
            System.out.println("READED 0 FROM FILE, RETURNING: " + i);
            return;
        }
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(readObject.getPathServer().getPath() + "/" + readObject.getPathClient().getName())));

        byte[] readBuffer = new byte[528];
        long fileSizeEnc = ((readObject.getFileSize() / 512) + 1) * 528;
        long fileSize = readObject.getFileSize();
        while (fileSizeEnc > 0) {
            int num = client.getInStream().read(readBuffer, 0, 528);
            if (num <= 0) break;
            byte[] decrypted = AES.decrypt(readBuffer, new String(client.getKey()));
            assert decrypted != null;
            bos.write(decrypted, 0, fileSize < 512 ? (int) fileSize : decrypted.length);
            fileSizeEnc -= 528;
            fileSize -= 512;
        }
        writeToSocket(client, null, FTPCommand.SUCCESS, 1, "File received successfully");
        bos.flush();
        bos.close();
    }

    public void writeFileToStream(ClientConnection client, File pathServer, boolean empty) throws IOException {
        System.out.println("writeFileToStream");
        if (empty || pathServer == null || pathServer.getPath().equals("")) {
            client.getOutStream().write(new byte[1]);
            System.out.println("WRITTEN 0, RETURNING");
            return;
        }
        int size = 0;
        byte[] myBuffer = new byte[512];
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(pathServer));
        while (true) {
            int bytesRead = bis.read(myBuffer, 0, 512);
            if (bytesRead == -1) break;
            size += bytesRead;
            synchronized (client.getSocket().getOutputStream()) {
                byte[] encrypted = AES.encrypt(myBuffer, new String(client.getKey()));
                assert encrypted != null;
                client.getOutStream().write(encrypted, 0, 528);
            }
        }
        bis.close();
        System.out.println("FILE SENT " + size);
    }

    public void writeObjectToStream(ClientConnection client, File pathClient, File pathServer, FTPCommand command, Integer response, String responseMessage) throws IOException {
        System.out.println("writeObjectToStream");
        FTPTransferObject objToSend = new FTPTransferObject(null, null, command, response, responseMessage, TreeItemSerialisation.serialize(ftv.getTreeItem()));
        objToSend.setPathClient(pathClient);
        objToSend.setPathServer(pathServer);
        objToSend.setAdditionalData(TreeItemSerialisation.serialize(ftv.getTreeItem()));
        if (pathServer != null) {
            if (!pathServer.getName().equals(""))
                objToSend.setName(pathServer.getName());
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = new ObjectOutputStream(bos);
        if (pathServer == null)
            objToSend.setFileSize(0);
        else
            objToSend.setFileSize(pathServer.length());
        out.writeObject(objToSend);
        byte[] objBytes = AES.encrypt(bos.toByteArray(), new String(client.getKey()));
        synchronized (client.getSocket().getOutputStream()) {
            assert objBytes != null;
            System.out.println("SENT SIZE " + objBytes.length);
            client.getOutStream().write(Arrays.copyOf((objBytes.length + "").getBytes(StandardCharsets.UTF_8), 16), 0, 16);
            client.getOutStream().write(objBytes);
        }
        bos.close();
        out.close();
        System.out.println("OBJECT SIZE " + objBytes.length);
    }

    private String generateKey(Socket client) {
        KeyGenerator keyGenerator = new KeyGenerator(new Random().nextInt() + 1);
        try {
            DataOutputStream toClient = new DataOutputStream(client.getOutputStream());
            DataInputStream fromClient = new DataInputStream(client.getInputStream());
            toClient.writeLong(keyGenerator.getCodeToSend());
            keyGenerator.setReceivedCode(fromClient.readLong());
            toClient.flush();
            return keyGenerator.getFinalCode() + "";
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private ClientConnection authenticateUser(Socket socket) throws IOException {
        ClientConnection currentClient=null;
        InputStream inStream = socket.getInputStream();
        String genKey = generateKey(socket);
        System.out.println("KEY " + genKey);
        DataInputStream dis = new DataInputStream(inStream);
        int size = dis.readInt();
        byte[] fromClient = new byte[size];
        inStream.read(fromClient, 0, size);
        String[] credentials = (new String(Objects.requireNonNull(AES.decrypt(fromClient, genKey)), StandardCharsets.UTF_8)).split(":");
        if (credentials.length == 2) {
            username = credentials[0].trim();
            password = credentials[1].trim();
        } else {
            System.out.println("Authentication failed.");
        }
        if (username.equals("admin") && password.equals("admin") || username.equals("root") && password.equals("root")) {
            currentClient = new ClientConnection(username, password, socket);
            currentClient.setKey(genKey);
            System.out.println("Credentials correct. Successfully logged in as " + username + ", IP:" + currentClient.getClientIP());
            writeToSocket(currentClient, null, FTPCommand.SUCCESS, 1, "Credentials correct. Successfully logged in");
            readFromSocket(currentClient);
        } else {
            ClientConnection client = new ClientConnection(null, null, socket);
            client.setKey(genKey);
            client.getOutStream().write(client.getKey().getBytes(StandardCharsets.UTF_8), 0, 16);
            System.out.println("Incorrect credentials!");
            writeToSocket(client, null, FTPCommand.FAILURE, -1, "Incorrect credentials!");
        }
        return currentClient;
    }


    void createFolder(File file) {
        boolean ret = file.mkdir();
        System.out.println("Created folder: " + ret);
    }

    /**
     * This method is used for deleting file or folder
     *
     * @param f file or folder to be deleted
     * @throws IOException If file/folder not found
     */
    void deleteFolder(File f) throws IOException {
        if (f.isDirectory()) {
            for (File c : Objects.requireNonNull(f.listFiles()))
                deleteFolder(c);
        }
        if (!f.delete())
            throw new FileNotFoundException("Failed to delete file: " + f);
    }

    public static void main(String[] args) throws IOException {
        FTPServer chatServer = new FTPServer();
        chatServer.createSocket();
    }
}