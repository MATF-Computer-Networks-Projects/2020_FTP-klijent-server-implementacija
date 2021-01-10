package com.ftp.file;

import javafx.scene.control.TreeItem;

import java.io.*;

/**
 * This class contains static methods for serializing and deserializing {@link TreeItem} because it is not serializable by default.
 *
 * @author Stefan
 */
public class TreeItemSerialisation {

    /**
     * Method deserialize {@link TreeItem}
     * @param root Byte array
     * @return Deserialized {@link TreeItem}
     */
    public static TreeItem<FTPFile> deserialize(byte[] root){
        TreeItem<FTPFile> root2=null;
        ObjectInputStream ois;
        try{
            ois=new ObjectInputStream(new ByteArrayInputStream(root));
            root2 = (TreeItem<FTPFile>) ois.readObject();
        }catch (Exception e){
            e.printStackTrace();
        }
        return root2;
    }

    /**
     * Method serializes {@link TreeItem}
     * @param root {@link TreeItem} that needs to be serialized
     * @return Byte array
     */
    public static byte[] serialize(TreeItem<FTPFile> root){
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos;
        try{
            oos=new ObjectOutputStream(bos);
            oos.writeObject(new TreeItemSerialisationWrapper(root));
            oos.flush();
        }catch (Exception e){
            e.printStackTrace();
        }
        return bos.toByteArray();
    }

}