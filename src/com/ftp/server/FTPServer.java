package com.ftp.server;

import com.ftp.client.FolderTreeView;
import com.ftp.file.AES;
import com.ftp.file.FTPCommand;
import com.ftp.file.FTPTransferObject;
import com.ftp.file.TreeItemSerialisation;
import javafx.scene.control.TreeItem;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
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
    private ServerSocket severSocket = null;
    private Socket socket = null;
    private InputStream inStream = null;
    private OutputStream outStream = null;
    private String username = "";
    private String password = "";
    FolderTreeView ftv;
    ConnectionManager manager;

    public FTPServer() {
        manager = new ConnectionManager();
    }

    /**
     * This method opens connection on given port and server is ready for accepting connections. When client connects, server reads it's credentials,
     * and if they are correct server creates {@link ClientConnection} object and add client to list of active clients. Connected client will
     * get it's own socket and thread responsible for reading and writing to it's socket. After that first object with repository explorer
     * will be sent to client. On failed authentication server refuses connection.
     *
     * @throws IOException if credentials are not read correctly
     */
    public void createSocket() {
        ftv = new FolderTreeView(new File(System.getProperty("user.dir")));
        try {
            ServerSocket serverSocket = new ServerSocket(3339);
            while (true) {
                socket = serverSocket.accept();
                inStream = socket.getInputStream();
                outStream = socket.getOutputStream();
                byte[] login = new byte[1024];
                inStream.read(login);
                String[] credentials = (new String(login)).split(":");
                if (credentials.length == 2) {
                    username = credentials[0].trim();
                    password = credentials[1].trim();
                } else {
                    System.out.println("Authentication failed.");
                }
                if (username.equals("admin") && password.equals("admin") || username.equals("root") && password.equals("root")) {
                    ClientConnection currentClient = new ClientConnection(username, password, socket);
                    currentClient.setKey(generateKey());
                    System.out.println("Credentials correct. Successfully logged in as " + username + ", IP:" + currentClient.getClientIP());
                    manager.addClient(currentClient);
                    currentClient.getOutStream().write(currentClient.getKey(),0,16);
                    writeToSocket(currentClient, null, FTPCommand.SUCCESS, 1, "Credentials correct. Successfully logged in");
                    readFromSocket(currentClient);
                } else {
                    ClientConnection client=new ClientConnection(null, null, socket);
                    client.getOutStream().write(new byte[16],0,16);
                    System.out.println("Incorrect credentials!");
                    writeToSocket(client, null, FTPCommand.FAILURE, -1, "Incorrect credentials!");
                }
            }
        } catch (IOException io) {
            io.printStackTrace();
        }
    }

    /**
     * This method is responsible for listening to each client socket and reading commands from it. After client sends {@link FTPTransferObject} server
     * receives it and reads {@link FTPCommand}. Depends on command certain actions are performed, some of them locally some of them returns file to client.
     * Each package sent through socket is authenticated every time to avoid unattended access to server.
     *
     * @param client The client for whom this thread is responsible
     * @throws IOException            When path to file is incorrect
     * @throws SocketException        When socket connection is closed or interrupted
     * @throws ClassNotFoundException When there is problem with class
     */
    public void readFromSocket(ClientConnection client) {
        Thread readThread = new Thread() {
            public void run() {
                while (client.getSocket().isConnected()) {
                    try {
                        synchronized (client.getSocket().getInputStream()) {
                            FTPTransferObject readedObject = null;
                            try {
                                readedObject = readObjectFromStream(client);
                                readFileFromStream(client, readedObject);
                            } catch (IOException | ClassNotFoundException e) {
                                e.printStackTrace();
                            }
                            if (readedObject.getCommand().equals(FTPCommand.GET)) {
                                writeToSocket(client, readedObject.getPathServer(), FTPCommand.GET, 1, "File sent successfully");
                            }
                            if (readedObject.getCommand().equals(FTPCommand.MKDIR)) {
                                createFolder(readedObject.getPathServer());
                                writeToSocket(client, null, FTPCommand.SUCCESS, 1, "Folder created successfully");
                            }
                            if (readedObject.getCommand().equals(FTPCommand.RMDIR)) {
                                try {
                                    deleteFolder(readedObject.getPathServer());
                                    writeToSocket(client, readedObject.getPathServer(), FTPCommand.SUCCESS, 1, "Folder/file deleted successfully");
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    writeToSocket(client, readedObject.getPathServer(), FTPCommand.FAILURE, -1, "Failed to delete file/folder");
                                }
                            }
                            System.out.println(readedObject);
                        }
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }
        };
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
        Thread writeThread = new Thread() {
            public void run() {
                try {
                    sleep(100);
                    writeObjectToStream(client, null, path, command, response, responseMessage);
                    writeFileToStream(client, path, !command.equals(FTPCommand.GET));
                    client.getOutStream().flush();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        };
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
        FTPTransferObject readedObject = null;
        byte[] b = new byte[16];
        client.getInStream().read(b, 0, 16);
        int size = Integer.parseInt((new String(b)).trim());
        byte[] objInputArray = new byte[size];
        client.getInStream().read(objInputArray, 0, size);
        ByteArrayInputStream bis = new ByteArrayInputStream(AES.decrypt(objInputArray,new String(client.getKey())));
        ObjectInput in = new ObjectInputStream(bis);
        readedObject = (FTPTransferObject) in.readObject();
        bis.close();
        in.close();
        return readedObject;
    }

    public void readFileFromStream(ClientConnection client, FTPTransferObject readedObject) throws IOException {
        System.out.println("readFileFromStream");
        if (readedObject.getFileSize() == 0) {
            client.getInStream().read(new byte[1]);
            System.out.println("READED 0 FROM FILE, RETURNING");
            return;
        }
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(readedObject.getPathServer().getPath() + "/" + readedObject.getPathClient().getName())));

        byte[] readBuffer = new byte[528];
        long fileSizeEnc = ((readedObject.getFileSize()/512)+1)*528;
        long fileSize=readedObject.getFileSize();
        while (fileSizeEnc>0) {
            int num = client.getInStream().read(readBuffer, 0, 528);
            if (num <= 0) break;
            byte[] decrypted=AES.decrypt(readBuffer,new String(client.getKey()));
            bos.write(decrypted, 0, fileSize<512?(int)fileSize:decrypted.length);
            fileSizeEnc-=528;
            fileSize-=512;
        }
        writeToSocket(client,null,FTPCommand.SUCCESS,1,"File received successfully");
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
                byte[] encrypted=AES.encrypt(myBuffer,new String(client.getKey()));
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
        byte[] objBytes = AES.encrypt(bos.toByteArray(),new String(client.getKey()));
        synchronized (client.getSocket().getOutputStream()) {
            System.out.println("SENT SIZE " + objBytes.length);
            client.getOutStream().write(Arrays.copyOf((objBytes.length + "").getBytes(), 16), 0, 16);
            client.getOutStream().write(objBytes);
        }
        bos.close();
        out.close();
        System.out.println("OBJECT SIZE " + objBytes.length);
    }

    private byte[] generateKey(){
        byte[] array = new byte[16];
        new Random().nextBytes(array);
        return array;
    }

    void createFolder(File file) {
        file.mkdir();
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

    public static void main(String[] args) {
        FTPServer chatServer = new FTPServer();
        chatServer.createSocket();
    }
}