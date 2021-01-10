package com.ftp.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javafx.scene.control.TreeItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * This class provides tree representation of remote repository folders and files
 * @author Stefan
 */
public class FolderTreeView {
    private final FTPFile rootFolder;
    TreeItem<FTPFile> treeItem;

    public FolderTreeView(FTPFile path) {
        this.rootFolder = path;
        treeItem = new TreeItem<>(rootFolder);
        createTree(treeItem);
    }

    /**
     * Method creates tree from given root folder recursively
     * @param rootItem Root folder
     */
    public void createTree(TreeItem<FTPFile> rootItem) {
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(Paths.get(rootItem.getValue().getAbsolutePath()))) {
            for (Path path : directoryStream) {
                TreeItem<FTPFile> newItem = new TreeItem<>(new FTPFile(path.toFile()));
                newItem.setExpanded(false);
                rootItem.getChildren().add(newItem);
                if (Files.isDirectory(path)) {
                    createTree(newItem);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public TreeItem<FTPFile> getTreeItem() {
        treeItem = new TreeItem<>(rootFolder);
        createTree(treeItem);
        return treeItem;
    }

}