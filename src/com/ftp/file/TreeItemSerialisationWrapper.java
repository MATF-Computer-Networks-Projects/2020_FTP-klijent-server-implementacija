package com.ftp.file;

import javafx.scene.control.TreeItem;

import java.io.*;
import java.util.Stack;

/**
 * This class is wrapper for {@link TreeItem} and enables it to be serializable because it is not by default
 *
 * @param <T> Parameter for {@link TreeItem}
 * @author Stefan
 */
public class TreeItemSerialisationWrapper<T extends Serializable> implements Serializable {
    private static final long serialVersionUID = 1L;

    private transient TreeItem<T> item;

    public TreeItemSerialisationWrapper(TreeItem<T> item) {
        if (item == null) {
            throw new IllegalArgumentException();
        }
        this.item = item;
    }

    private void writeObject(ObjectOutputStream out)
            throws IOException {
        Stack<TreeItem<T>> stack = new Stack<>();
        stack.push(item);

        out.defaultWriteObject();
        do {
            TreeItem<T> current = stack.pop();
            int size = current.getChildren().size();
            out.writeInt(size);
            out.writeObject(current.getValue());
            for (int i = size - 1; i >= 0; --i) {
                stack.push(current.getChildren().get(i));
            }
        } while (!stack.isEmpty());
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        class Container {
            int count;
            final TreeItem<T> item;

            Container(ObjectInputStream in) throws ClassNotFoundException, IOException {
                this.count = in.readInt();
                this.item = new TreeItem<>((T) in.readObject());
            }
        }
        in.defaultReadObject();
        Container root = new Container(in);
        this.item = root.item;

        if (root.count > 0) {
            Stack<Container> stack = new Stack<>();
            stack.push(root);
            do {
                Container current = stack.peek();
                --current.count;
                if (current.count <= 0) {
                    stack.pop();
                }

                Container newContainer = new Container(in);
                current.item.getChildren().add(newContainer.item);
                if (newContainer.count > 0) {
                    stack.push(newContainer);
                }
            } while (!stack.isEmpty());
        }
    }

    private Object readResolve() throws ObjectStreamException {
        return item;
    }
}