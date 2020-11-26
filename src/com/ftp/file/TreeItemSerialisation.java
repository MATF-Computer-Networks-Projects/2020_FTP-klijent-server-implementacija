package com.ftp.file;

import javafx.scene.control.TreeItem;

import java.io.*;
import java.util.Arrays;

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
    public static TreeItem<File> deserialize(byte[] root){
        TreeItem<File> root2=null;
        ObjectInputStream ois;
        try{
            ois=new ObjectInputStream(new ByteArrayInputStream(root));
            root2 = (TreeItem<File>) ois.readObject();
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
    public static byte[] serialize(TreeItem<File> root){
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = null;
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