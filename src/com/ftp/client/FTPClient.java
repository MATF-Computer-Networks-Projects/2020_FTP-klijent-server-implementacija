package com.ftp.client;

import com.ftp.file.*;
import javafx.scene.control.TreeItem;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

/**
 * This is basic class that represents client connecting to remote {@link com.ftp.server.FTPServer}. It uses {@link Socket}
 * for establishing connection to remote server with given username, password, host, port and communicate with {@link FTPTransferObject}. It has 2 main threads,
 * one for reading and one for writing to socket input/output stream. Upon connecting, client sends username and password as plain {@link String}
 * and after that runs read thread which is responsible for listening and reading {@link FTPTransferObject} from socket. On connecting
 * server sends object that contains {@link TreeItem} with folders/files from server.
 * Thread for writing to socket output stream runs on command while reading thread works infinitely (until connection closes).
 *
 * @author Stefan
 */
public class FTPClient {
    private Socket socket = null;
    private InputStream inStream = null;
    private OutputStream outStream = null;
    private TreeItem<FTPFile> tree = null;
    private int connected = 0;
    private boolean pause = false;
    private final String username;
    private final String password;
    private final String host;
    private final Integer port;
    private String key;

    public FTPClient(String username, String password, String host, Integer port) {
        this.username = username;
        this.password = password;
        this.host = host;
        this.port = port;
    }

    /**
     * This method is used for creating socket and connecting to server. On connecting it sends login credentials
     * and starts reading thread that is responsible for listening to socket and accepting {@link FTPTransferObject}.
     */
    public boolean createSocket() {
        try {
            socket = new Socket(host, port);
            inStream = socket.getInputStream();
            outStream = socket.getOutputStream();
            generateKey();
            byte[] toSend = Objects.requireNonNull(AES.encrypt((username + ":" + password).getBytes(StandardCharsets.UTF_8), key));
            byte[] sizeToSend = Arrays.copyOf(String.valueOf(toSend.length).getBytes(StandardCharsets.US_ASCII), 32);
            outStream.write(sizeToSend, 0, 32);
            outStream.write(toSend);
            outStream.flush();
            readFromSocket();
            return true;
        } catch (IOException u) {
            FTPClientUI.addToLog(u.getMessage());
            u.printStackTrace();
        }
        return false;
    }

    /**
     * This method creates thread for reading from socket stream. It is called after successful connection to server.
     * Method reads {@link FTPTransferObject} from input stream, and after that reads file bytes (if there is any.
     * Tree view (server's file explorer) is updated on every change on server side.
     */
    public void readFromSocket() {
        Thread readThread = new Thread(() -> {
            while (socket.isConnected()) {
                try {
                    FTPTransferObject readObject = readObjectFromStream();
                    if (readObject.getAdditionalData() != null) {
                        tree = TreeItemSerialisation.deserialize(readObject.getAdditionalData());
                    }
                    FTPClientUI.addToLog("Server response: " + readObject.getResponseMessage() + "\n");
                    readFileFromStream(readObject);
                    if (readObject.getResponseCode() == -1) {
                        connected = -1;
                        return;
                    } else {
                        connected = 1;
                    }
                } catch (IOException | ClassNotFoundException e) {
                    FTPClientUI.addToLog(e.getMessage());
                    connected = 0;
                    return;
                }
            }

        });
        readThread.setPriority(Thread.MAX_PRIORITY);
        readThread.start();
    }

    /**
     * This method creates thread that is used for sending {@link FTPTransferObject} to server. After object, if required, encrypted file bytes are sent.
     * It runs on command (unlike read thread which runs infinitely until connection is closed).
     *
     * @param pathClient Path from client (if file needs to be sent)
     * @param pathServer Path on server side (if file needs to be received or folder needs to be created)
     * @param command    Command to be executed
     */
    public void writeToSocket(File pathClient, File pathServer, FTPCommand command) {
        Thread writeThread = new Thread(() -> {
            try {
                writeObjectToStream(pathClient, pathServer, command);
                writeFileToStream(pathClient, !command.equals(FTPCommand.PUT));
                if (command.equals(FTPCommand.CLOSE)) {
                    socket.close();
                }
            } catch (IOException e) {
                FTPClientUI.addToLog(e.getMessage());
                e.printStackTrace();
            }


        });
        writeThread.setPriority(Thread.MAX_PRIORITY);
        writeThread.start();
    }

    /**
     * This method is used for sending object from server to client. Before sending, object length is sent first, represented as string maximum length of 16 bytes.
     * Object represent {@link FTPTransferObject} with command, credentials for authentication, paths, file size...
     *
     * @param pathClient Client's file path
     * @param pathServer Server's file/folder path
     * @param command    Command to be executed
     * @throws IOException If there is problem with object, if there is socket or stream problem
     */
    public void writeObjectToStream(File pathClient, File pathServer, FTPCommand command) throws IOException {
        FTPTransferObject objToSend = new FTPTransferObject(username, password, command, 0, null, null);
        objToSend.setPathServer(pathServer);
        objToSend.setPathClient(pathClient);
        if (pathClient != null) {
            objToSend.setName(pathClient.getName());
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = new ObjectOutputStream(bos);
        if (pathClient == null)
            objToSend.setFileSize(0);
        else
            objToSend.setFileSize(pathClient.length());
        out.writeObject(objToSend);
        byte[] objBytes = AES.encrypt(bos.toByteArray(), key);
        assert objBytes != null;
        outStream.write(Arrays.copyOf((objBytes.length + "").getBytes(StandardCharsets.UTF_8), 16), 0, 16);
        int sendObjectSize = 0;
        while (objBytes.length - sendObjectSize > 0) {
            byte[] tmpArray = Arrays.copyOfRange(objBytes, sendObjectSize, sendObjectSize + Math.min(objBytes.length - sendObjectSize, 512));
            outStream.write(tmpArray, 0, tmpArray.length);
            sendObjectSize += 512;
        }
        outStream.flush();

    }

    /**
     * This method send file as encrypted bytes to server (if required). File size was already sent in object.
     * Method uses {@link BufferedInputStream} for reading file by chunks, each chunk is 512 bytes length, and writes them directly to stream.
     *
     * @param pathClient Path from client's file that needs to be sent
     * @param empty      If there is no need for file to be sent, this flag is set to {@code true} and synchronization byte is sent
     * @throws IOException If there is problem with file, or stream
     */
    public void writeFileToStream(File pathClient, boolean empty) throws IOException {
        if (empty || pathClient == null) {
            return;
        }
        byte[] myBuffer = new byte[512];
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(pathClient));
        long currentTimeSeconds;
        int read = 0;
        while (true) {
            currentTimeSeconds = System.currentTimeMillis() / 1000;
            int bytesRead = bis.read(myBuffer, 0, 512);
            if (bytesRead == -1) break;
            byte[] encrypted = AES.encrypt(myBuffer, key);
            assert encrypted != null;
            outStream.write(encrypted, 0, 528);
            read += 528;
            FTPClientUI.updateBar(((double) read - 1) / (double) pathClient.length(), 528 / (((double) System.currentTimeMillis() / 1000) - currentTimeSeconds));
            while (pause) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        FTPClientUI.updateBar(0, 0);
        outStream.flush();
        bis.close();
    }

    /**
     * This method reads object from stream. First it reads object size that is sent from server, and then {@link FTPTransferObject}.
     *
     * @return Read object from stream
     * @throws IOException            If object is not read correctly, or there is some stream problem (bytes not read correctly)
     * @throws ClassNotFoundException If there is problem with class
     */
    public FTPTransferObject readObjectFromStream() throws IOException, ClassNotFoundException {
        FTPTransferObject readObject;
        byte[] objInputArray;
        int num;
        byte[] b = new byte[16];
        num = inStream.read(b, 0, 16);
        if (num == 0) return null;
        int size = Integer.parseInt((new String(b, StandardCharsets.UTF_8)).trim());
        objInputArray = new byte[size];
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        int readBytes = 0;
        while (size - readBytes > 0) {
            while (inStream.available() < Math.min(size - readBytes, 512)) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            byte[] tmpArray = new byte[Math.min(size - readBytes, 512)];
            inStream.read(tmpArray, 0, Math.min(size - readBytes, 512));
            System.arraycopy(tmpArray, 0, objInputArray, readBytes, tmpArray.length);
            readBytes += 512;
        }

        ByteArrayInputStream bis = new ByteArrayInputStream(Objects.requireNonNull(AES.decrypt(objInputArray, key)));
        ObjectInput in = new ObjectInputStream(bis);
        readObject = (FTPTransferObject) in.readObject();
        return readObject;
    }

    /**
     * This method reads file from stream and stores it in client's directory.
     * From given {@link FTPTransferObject} file size is read, and then, with 512 bytes buffer, file is read and stored.
     *
     * @param readObject Read object from stream before reading file
     * @throws IOException If there is problem with file or stream
     */
    public void readFileFromStream(FTPTransferObject readObject) throws IOException {
        if (readObject.getFileSize() == 0) {
            return;
        }
        if (readObject.getPathServer() != null) {
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(readObject.getName())));

            byte[] readBuffer = new byte[528];
            long fileSizeEnc = ((readObject.getFileSize() / 512) + 1) * 528;
            long fileSize = readObject.getFileSize();
            long read = 0;
            long currentTimeSeconds;
            while (fileSizeEnc > 0) {
                while (inStream.available() < 528) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                currentTimeSeconds = System.currentTimeMillis() / 1000;
                int num = inStream.read(readBuffer, 0, 528);
                if (num <= 0) break;
                byte[] decrypted = AES.decrypt(readBuffer, key);
                assert decrypted != null;
                bos.write(decrypted, 0, fileSize < 512 ? (int) fileSize : decrypted.length);
                read += 512;
                fileSizeEnc -= 528;
                fileSize -= 512;
                FTPClientUI.updateBar(((double) read - 1) / ((double) fileSize), 512 / (((double) System.currentTimeMillis() / 1000) - currentTimeSeconds));
                while (pause) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            FTPClientUI.updateBar(0, 0);
            bos.flush();
            bos.close();
        }
    }

    /**
     * This method uses Diffie Hellman algorithm for generating encryption key with server.
     */
    public void generateKey() {
        KeyGenerator keyGenerator = new KeyGenerator(new Random().nextInt() + 10);
        try {
            byte[] received = new byte[32];
            inStream.read(received, 0, 32);
            keyGenerator.setReceivedCode(Long.parseLong(new String(received, StandardCharsets.US_ASCII).trim()));
            outStream.write(Arrays.copyOf(String.valueOf(keyGenerator.getCodeToSend()).getBytes(StandardCharsets.US_ASCII), 16));
            outStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.key = String.valueOf(keyGenerator.getFinalCode());
    }

    public String getUsername() {
        return username;
    }

    public String getHost() {
        return host;
    }

    public TreeItem<FTPFile> getTree() {
        return tree;
    }

    public boolean checkConnected() {
        int iter = 0;
        while (connected == 0) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (connected == -1) return false;
            if (connected == 1) return true;
            if (iter > 20) break;
            iter++;
        }
        return false;
    }

    public boolean getPause() {
        return pause;
    }

    public void setPause(boolean p) {
        pause = p;
    }
}