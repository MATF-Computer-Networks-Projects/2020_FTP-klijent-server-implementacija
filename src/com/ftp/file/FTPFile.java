package com.ftp.file;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This class is replacement for {@link File} class in standard Java library. It is used for keeping file properties after serialization
 * because Java's {@link File} doesn't keep field values after deserializing.
 */
public class FTPFile implements Serializable {

    public File file;
    public String absolutePath;
    public String path;
    public String name;
    public long length;
    public boolean directory;
    public boolean canRead;
    public boolean canWrite;
    public boolean canExecute;
    public boolean symLink;
    public long lastModified;
    public String owner;

    public FTPFile(String path) {
        this.file = new File(path);
        try {
            initParams();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Initializes parameters before object usage
     *
     * @throws IOException If {@link Path} cannot be obtained
     */
    private void initParams() throws IOException {
        absolutePath = file.getAbsolutePath();
        path = file.getPath();
        name = file.getName();
        length = file.length();
        directory = file.isDirectory();
        lastModified = file.lastModified();
        symLink = Files.isSymbolicLink(file.toPath());
        canWrite = file.canWrite();
        canRead = file.canRead();
        canExecute = file.canExecute();
        owner = Files.getOwner(file.toPath()).toString();
    }

    public FTPFile(File file) {
        this.file = file;
        try {
            initParams();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getAbsolutePath() {
        return absolutePath;
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public long length() {
        return length;
    }

    public boolean isDirectory() {
        return directory;
    }

    public long lastModified() {
        return lastModified;
    }

    public Path toPath() {
        return file.toPath();
    }

    public String getOwner() {
        return owner;
    }

    public boolean isSymbolicLink() {
        return symLink;
    }

    public boolean canWrite() {
        return canWrite;
    }

    public boolean canRead() {
        return canRead;
    }

    public boolean canExecute() {
        return canExecute;
    }
}
