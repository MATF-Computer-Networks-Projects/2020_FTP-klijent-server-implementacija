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
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * This is basic class that represents FTP server. It uses {@link Socket} for opening connection with given port and communicate with {@link FTPTransferObject}. It has 2 main threads,
 * one for reading and one for writing to socket input/output stream assigned to each client. Upon client connecting, after creating key for encryption
 * it reads username and password as plain {@link String} and after that, if credentials are correct, client can get it's own socket,
 * then runs read thread which is responsible for listening and reading {@link FTPTransferObject} from each client.
 * On client connected, server sends object to client that contains {@link TreeItem} with folders/files from it.
 * Thread for writing to socket output stream runs on command received from client, while reading thread for each client works infinitely (until client disconnect).
 *
 * @author Stefan
 */
public class FTPServer {
    private FolderTreeView ftv;
    private final ConnectionManager manager;
    private final int port;
    private Logger logger = Logger.getLogger("ErrorLoggerServer");
    private FileHandler fh;

    public FTPServer(int port) {
        this.port = port;
        manager = new ConnectionManager();
    }

    /**
     * This method opens connection on given port and server is ready for accepting connections. When client connects, server reads it's credentials,
     * and if they are correct server creates {@link ClientConnection} object and add client to list of active clients. Connected client will
     * get it's own socket and thread responsible for reading and writing to it's socket. After that first object with repository explorer
     * will be sent to client. On failed authentication server refuses connection.
     */
    public void createSocket() throws IOException {
        ftv = new FolderTreeView(new FTPFile(System.getProperty("user.dir")));
        ServerSocket serverSocket = new ServerSocket(port);
        configureLog();
        System.err.println("Server ready");
        while (true) {
            Socket socket = null;
            ClientConnection currentClient = null;
            try {
                socket = serverSocket.accept();
                currentClient = authenticateUser(socket);
                if (currentClient != null) {
                    manager.addClient(currentClient);
                }
                System.out.println("Clients available: " + manager.clients.size());
            } catch (IOException io) {
                addToLog(io);
                assert socket != null;
                socket.close();
                System.err.println("Client has been disconnected! Error: " + io.getMessage());
                System.out.println("Clients remaining: " + manager.clients.size());
            }
        }

    }

    /**
     * This method is responsible for listening to each client socket and reading commands from it. After client sends {@link FTPTransferObject} server
     * receives it and reads {@link FTPCommand}. Depends on command certain actions are performed, some of them locally some of them returns file to client.
     *
     * @param client The client for whom this thread is responsible
     */
    public void readFromSocket(ClientConnection client) {
        Thread readThread = new Thread(() -> {
            while (client.getSocket().isConnected()) {
                try {
                    FTPTransferObject readObject = readObjectFromStream(client);
                    readFileFromStream(client, readObject);

                    if (readObject.getCommand().equals(FTPCommand.GET)) {
                        System.out.println(client.getUsername() + " (" + client.getClientIP() + ") requested download of " + readObject.getPathServer());
                        writeToSocket(client, readObject.getPathServer(), FTPCommand.GET, 1, "File sent successfully");
                    }
                    if (readObject.getCommand().equals(FTPCommand.CLOSE)) {
                        manager.removeClient(client);
                        System.err.println(client.getUsername() + " (" + client.getClientIP() + ") requested disconnect. Disconnecting " + client.getClientIP());
                        System.out.println("Clients remaining: " + manager.clients.size());
                        client.getSocket().close();
                        return;
                    }
                    if (readObject.getCommand().equals(FTPCommand.TREE)) {
                        System.out.println(client.getUsername() + " (" + client.getClientIP() + ") requested tree refresh.");
                        writeToSocket(client, null, FTPCommand.SUCCESS, 1, "Tree view sent.");
                    }
                    if (readObject.getCommand().equals(FTPCommand.MKDIR)) {
                        System.out.println(client.getUsername() + " (" + client.getClientIP() + ") requested creating " + readObject.getPathServer());
                        createFolder(readObject.getPathServer());
                        writeToSocket(client, null, FTPCommand.SUCCESS, 1, "Folder created successfully");
                    }
                    if (readObject.getCommand().equals(FTPCommand.RMDIR)) {
                        System.out.println(client.getUsername() + " (" + client.getClientIP() + ") requested deleting " + readObject.getPathServer());
                        try {
                            deleteFolder(readObject.getPathServer());
                            writeToSocket(client, readObject.getPathServer(), FTPCommand.SUCCESS, 1, "Folder/file deleted successfully");
                        } catch (IOException e) {
                            addToLog(e);
                            writeToSocket(client, readObject.getPathServer(), FTPCommand.FAILURE, -1, e.getMessage());
                        }
                    }

                } catch (IOException | ClassNotFoundException e) {
                    System.err.println(client.getUsername() + " (" + client.getClientIP() + ") disconnected.");
                    manager.removeClient(client);
                    System.out.println("Clients remaining: " + manager.clients.size());
                    try {
                        client.getSocket().close();
                    } catch (IOException ioException) {
                        addToLog(ioException);
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
                writeObjectToStream(client, null, path, command, response, responseMessage);
                writeFileToStream(client, path, !command.equals(FTPCommand.GET));
            } catch (Exception e) {
                System.err.println("Failed to send data to client. It will be disconnected");
                try {
                    client.getSocket().close();
                } catch (IOException ioException) {
                    addToLog(ioException);
                }
                manager.removeClient(client);
            }

        });
        writeThread.setPriority(Thread.MAX_PRIORITY);
        writeThread.start();
    }

    /**
     * This method is responsible for reading serialized object from client input stream. First it reads size of object
     * represented as {@link String}, then reads object with that size.
     *
     * @param client Client which stream needs to be read
     * @return Read object
     */
    public FTPTransferObject readObjectFromStream(ClientConnection client) throws IOException, ClassNotFoundException {
        FTPTransferObject readedObject;
        byte[] b = new byte[16];
        int i = client.getInStream().read(b, 0, 16);
        if (i == 0) return null; //TODO 1
        int size = Integer.parseInt((new String(b)).trim());
        byte[] objInputArray = new byte[size];
        int readBytes = 0;
        while (size - readBytes > 0) {
            while (client.getInStream().available() < Math.min(size - readBytes, 512)) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    addToLog(e);
                }
            }
            byte[] tmpArray = new byte[Math.min(size - readBytes, 512)];

            client.getInStream().read(tmpArray, 0, Math.min(size - readBytes, 512));
            System.arraycopy(tmpArray, 0, objInputArray, readBytes, tmpArray.length);
            readBytes += 512;
        }
        ByteArrayInputStream bis = new ByteArrayInputStream(Objects.requireNonNull(AES.decrypt(objInputArray, new String(client.getKey()))));
        ObjectInput in = new ObjectInputStream(bis);
        readedObject = (FTPTransferObject) in.readObject();
        bis.close();
        in.close();
        return readedObject;
    }

    /**
     * This method reads file that was sent from client. It is decrypted after receiving every chunk (512 bytes).
     *
     * @param client     Client that send file
     * @param readObject Object that contains file size and other file related properties
     * @throws IOException if there is problem with file creation or other.
     */
    public void readFileFromStream(ClientConnection client, FTPTransferObject readObject) throws IOException {
        if (readObject.getFileSize() == 0) {
            return;
        }
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(readObject.getPathServer().getPath() + "/" + readObject.getPathClient().getName())));
        System.out.println("Reading file from " + client.getUsername() + " (" + client.getClientIP() + ")");
        byte[] readBuffer = new byte[528];
        long fileSizeEnc = ((readObject.getFileSize() / 512) + 1) * 528;
        long fileSize = readObject.getFileSize();
        while (fileSizeEnc > 0) {
            while (client.getInStream().available() < 528) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    addToLog(e);
                }
            }
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

    /**
     * This method writes file to stream. It encrypts file chunks (512 bytes) and sends them.
     *
     * @param client     Client to whom file needs to be sent
     * @param pathServer Server side path of file that needs to be sent
     * @param empty      If file don't need to be sent (considering that this method is called always on every object receiving)
     *                   this parameter is set to {@code false}
     * @throws IOException If there is problem with server file
     */
    public void writeFileToStream(ClientConnection client, File pathServer, boolean empty) throws IOException {
        if (empty || pathServer == null || pathServer.getPath().equals("")) {
            return;
        }
        System.out.println("Sending " + pathServer.getName() + " to " + client.getUsername() + " (" + client.getClientIP() + ")");
        byte[] myBuffer = new byte[512];
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(pathServer));
        while (true) {
            int bytesRead = bis.read(myBuffer, 0, 512);
            if (bytesRead == -1) break;
            byte[] encrypted = AES.encrypt(myBuffer, new String(client.getKey()));
            assert encrypted != null;
            client.getOutStream().write(encrypted, 0, 528);
        }
        client.getOutStream().flush();
        bis.close();

    }

    /**
     * This method sends serialized {@link FTPTransferObject} to certain client with necessary data. This can be response message, tree explorer...
     *
     * @param client          Client to whom object needs to be sent
     * @param pathClient      Path from client side (for obtaining file properties)
     * @param pathServer      Path from server side
     * @param command         FTP command
     * @param response        Response code for client
     * @param responseMessage Response message for client
     * @throws IOException if there is problem with certain file
     */
    public void writeObjectToStream(ClientConnection client, File pathClient, File pathServer, FTPCommand command, Integer response, String responseMessage) throws IOException {
        FTPTransferObject objToSend = new FTPTransferObject(null, null, command, response, responseMessage, response == -1 ? null : TreeItemSerialisation.serialize(ftv.getTreeItem()));
        objToSend.setPathClient(pathClient);
        objToSend.setPathServer(pathServer);
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
        assert objBytes != null;
        client.getOutStream().write(Arrays.copyOf((objBytes.length + "").getBytes(StandardCharsets.UTF_8), 16), 0, 16);
        int sendObjectSize = 0;
        while (objBytes.length - sendObjectSize > 0) {
            byte[] tmpArray = Arrays.copyOfRange(objBytes, sendObjectSize, sendObjectSize + Math.min(objBytes.length - sendObjectSize, 512));
            client.getOutStream().write(tmpArray, 0, tmpArray.length);
            sendObjectSize += 512;
        }
        client.getOutStream().flush();
        bos.close();
        out.close();
    }

    /**
     * This method generates encryption key using Diffie Hellman algorithm.
     *
     * @param client Client with whom key needs to be generated
     * @return Generated key
     */
    private String generateKey(Socket client) {
        KeyGenerator keyGenerator = new KeyGenerator(new Random().nextInt() + 10);
        try {
            byte[] toSend = new byte[32];
            client.getOutputStream().write(Arrays.copyOf(String.valueOf(keyGenerator.getCodeToSend()).getBytes(StandardCharsets.US_ASCII), 16));
            client.getInputStream().read(toSend, 0, 32);
            keyGenerator.setReceivedCode(Long.parseLong(new String(toSend, StandardCharsets.US_ASCII).trim()));
            client.getOutputStream().flush();
            return keyGenerator.getFinalCode() + "";
        } catch (IOException e) {
            addToLog(e);
        }
        return null;
    }

    /**
     * This method is used for authenticating user before giving permission to connect to server.
     *
     * @param socket Possible client socket
     * @return Connected client represented as {@link ClientConnection}
     * @throws IOException if there is problem with reading/writing
     */
    private ClientConnection authenticateUser(Socket socket) throws IOException {
        String username = "";
        String password = "";
        ClientConnection currentClient = null;
        InputStream inStream = socket.getInputStream();
        String genKey = generateKey(socket);
        byte[] sizeByte = new byte[32];
        socket.getInputStream().read(sizeByte, 0, 32);
        int size = Integer.parseInt(new String(sizeByte, StandardCharsets.US_ASCII).trim());
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
            System.out.println("Incorrect credentials!");
            writeToSocket(client, null, FTPCommand.FAILURE, -1, "Incorrect credentials!");
        }
        return currentClient;
    }


    /**
     * Creating folder on server side
     *
     * @param file folder that needs to be created
     */
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

    public void configureLog() {
        try {
            logger.setUseParentHandlers(false);
            fh = new FileHandler("ErrorLoggerServer.log");
            logger.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
        } catch (IOException e) {
            addToLog(e);
        }
    }

    public void addToLog(Exception e) {
        System.err.println("Error! Check error log file.");
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        logger.info(sw.toString());
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Missing or invalid arugments. " + args.length);
            return;
        }
        FTPServer chatServer = new FTPServer(Integer.parseInt(args[0]));
        chatServer.createSocket();
    }


}