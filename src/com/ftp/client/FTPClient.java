package com.ftp.client;

import com.ftp.file.AES;
import com.ftp.file.FTPCommand;
import com.ftp.file.FTPTransferObject;
import com.ftp.file.TreeItemSerialisation;
import javafx.scene.control.TreeItem;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;

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
    private TreeItem<File> tree = null;
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
     *
     * @throws IOException if host is unreachable or hostname/port is invalid or other I/O exception
     */
    public void createSocket() {
        try {
            socket = new Socket(host, port);
            inStream = socket.getInputStream();
            outStream = socket.getOutputStream();
            outStream.write((username + ":" + password).getBytes());
            outStream.flush();
            byte[] keyByte=new byte[16];
            inStream.read(keyByte,0,16);
            key=new String(keyByte);
            readFromSocket();
        } catch (IOException u) {
            FTPClientUI.addToLog(u.getMessage());
            u.printStackTrace();
        }
    }

    /**
     * This method creates thread for reading from socket stream. It is called after successful connection to server.
     * Method reads {@link FTPTransferObject} from input stream, and after that reads file bytes (if there is any) or synchronization byte.
     * Tree view (server's file explorer) is updated on every change on server side.
     *
     * @throws SocketException          if socket is closed, or interrupted.
     * @throws IOException              if path is incorrect
     * @throws ClassNotFoundException   class for deserialization was not found
     * @throws StreamCorruptedException if object is not read completely/correctly
     */
    public void readFromSocket() {
        Thread readThread = new Thread() {
            public void run() {
                while (socket.isConnected()) {
                    try {
                        FTPTransferObject readObject = readObjectFromStream();
                        tree = TreeItemSerialisation.deserialize(readObject.getAdditionalData());
                        FTPClientUI.addToLog("Server response: "+readObject.getResponseMessage()+"\n");
                        readFileFromStream(readObject);
                    } catch (IOException | ClassNotFoundException e) {
                        e.printStackTrace();
                        FTPClientUI.addToLog(e.getMessage());
                    }
                }

            }
        };
        readThread.setPriority(Thread.MAX_PRIORITY);
        readThread.start();
    }

    /**
     * This method creates thread that is used for sending {@link FTPTransferObject} to server. After object, if required, file bytes are sent.
     * If there is no need for sending file, synchronization byte is sent, It runs on command (unlike read thread which runs infinitely until connection is closed).
     *
     * @param pathClient Path from client (if file needs to be sent)
     * @param pathServer Path on server side (if file needs to be received or folder needs to be created)
     * @param command    Command to be executed
     * @throws IOException              if file path is incorrect, or stream methods cannot be executed
     * @throws InvalidClassException    If class used for sending has problems
     * @throws NotSerializableException If class is not serializable
     * @throws InterruptedException     If thread is interrupted
     */
    public void writeToSocket(File pathClient, File pathServer, FTPCommand command) {
        Thread writeThread = new Thread() {
            public void run() {
                try {
                    sleep(100);
                    writeObjectToStream(pathClient, pathServer, command);
                    writeFileToStream(pathClient, !command.equals(FTPCommand.PUT));
                    outStream.flush();
                } catch (IOException | InterruptedException e) {
                    FTPClientUI.addToLog(e.getMessage());
                    e.printStackTrace();
                }


            }
        };
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
        System.out.println("writeObjectToStream");
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
        byte[] objBytes = AES.encrypt(bos.toByteArray(),key);
        synchronized (socket) {
            outStream.write(Arrays.copyOf((objBytes.length + "").getBytes(), 16), 0, 16);
            outStream.write(objBytes);
        }
    }

    /**
     * This method send file to server. If there is no need for file to be sent, synchronization byte is sent. File size is sent in object.
     * Method uses {@link BufferedInputStream} for reading file by chunks, each chunk is 512 bytes length, and writes them directly to stream.
     *
     * @param pathClient Path from client's file that needs to be sent
     * @param empty      If there is no need for file to be sent, this flag is set to {@code true} and synchronization byte is sent
     * @throws IOException If there is problem with file, or stream
     */
    public void writeFileToStream(File pathClient, boolean empty) throws IOException {
        if (empty || pathClient == null) {
            outStream.write(new byte[1]);
            System.out.println("WRITTEN 0, RETURNING");
            return;
        }
        System.out.println("writeFileToStream");
        byte[] myBuffer = new byte[512];
        int size = 0;
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(pathClient));
        while (true) {
            int bytesRead = bis.read(myBuffer, 0, 512);
            if (bytesRead == -1) break;
            size += bytesRead;
            synchronized (outStream) {
                byte[] encrypted=AES.encrypt(myBuffer,key);
                outStream.write(encrypted, 0, 528);
            }
        }
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
        System.out.println("readObjectFromStream");
        FTPTransferObject readObject;
        byte[] objInputArray;
        int num = 0;
        byte[] b = new byte[16];
        num = inStream.read(b, 0, 16);
        int size = Integer.parseInt((new String(b)).trim());
        System.out.println("READED SIZE " + size);
        objInputArray = new byte[size];
        num = inStream.read(objInputArray, 0, size);
        ByteArrayInputStream bis = new ByteArrayInputStream(AES.decrypt(objInputArray,key));
        ObjectInput in = new ObjectInputStream(bis);
        readObject = (FTPTransferObject) in.readObject();
        return readObject;
    }

    /**
     * This method reads file from stream and stores it in client's directory. If there is no file, only synchronization byte is read.
     * From given {@link FTPTransferObject} file size is read, and then, with 512 bytes buffer, file is read and stored.
     *
     * @param readObject Read object from stream before reading file
     * @throws IOException If there is problem with file or stream
     */
    public void readFileFromStream(FTPTransferObject readObject) throws IOException {
        System.out.println("readFileFromStream");
        if (readObject.getFileSize() == 0) {
            inStream.read(new byte[1]);
            System.out.println("READED 0 FROM FILE, RETURNING");
            return;
        }
        if (readObject.getPathServer() != null) {
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(readObject.getName())));

            byte[] readBuffer = new byte[528];
            long fileSizeEnc = ((readObject.getFileSize()/512)+1)*528;
            long fileSize=readObject.getFileSize();
            long read=0;
            while (fileSizeEnc>0) {
                int num = inStream.read(readBuffer, 0, 528);
                if (num <= 0) break;
                byte[] decrypted=AES.decrypt(readBuffer,key);
                bos.write(decrypted, 0, fileSize<512?(int)fileSize:decrypted.length);
                read+=512;
                fileSizeEnc-=528;
                fileSize-=512;
                FTPClientUI.updateBar(((double)read-1)/(double)fileSize);
            }
            bos.flush();
            bos.close();
        }
    }

    /**
     * Method returns client username.
     *
     * @return Username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Method returns hostname.
     *
     * @return Hostname
     */
    public String getHost() {
        return host;
    }

    /**
     * Method returns tree representation of remote files/folders
     *
     * @return Tree with files/folders
     */
    public TreeItem<File> getTree() {
        return tree;
    }

}