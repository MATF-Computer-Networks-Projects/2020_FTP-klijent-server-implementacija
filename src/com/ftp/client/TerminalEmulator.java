package com.ftp.client;

import com.ftp.file.FTPCommand;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeItem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;

/**
 * Old style linux terminal emulator used for directly executing basic commands to remote FTP server. Also supports basic terminal commands like
 * cd, pwd, ls...
 *
 * @author Stefan
 */
public class TerminalEmulator {
    private FTPClient client;
    private final TextArea console;
    public static String pwd = "";
    public static TreeItem<File> treeItem;
    private int caretPosition;
    private String lastCommand = "";

    public TerminalEmulator(FTPClient client, TextArea console) {
        this.client = client;
        this.console = console;
        this.caretPosition = console.getCaretPosition();
    }

    /**
     * Executes command given in terminal
     *
     */
    public void executeCommand() {
        if(client==null){
            client=FTPClientUI.getClient();
        }
        if (treeItem == null && client != null) {
            treeItem = client.getTree();
        }
        String command = console.getText().substring(console.getText().lastIndexOf("$") + 1, console.getCaretPosition()).trim();
        lastCommand = command;
        if (command.equals("ls")) {
            try {
                listFiles();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (command.equals("pwd")) {
            console.appendText(client.getTree().getValue().getName());
        } else if (command.startsWith("cd")) {
            if(command.substring(2).trim().equals("--help")){
                console.appendText("Usage: cd [path]");
            }else{
                pwd += "/" + (command.substring(2).trim().replace("/", ""));
                for (TreeItem<File> item : treeItem.getChildren()) {
                    if (item.getValue().getName().equals(pwd.substring(pwd.lastIndexOf("/") + 1).trim())) {
                        treeItem = item;
                    }
                }
            }
        } else if (command.startsWith("get")) {
            String file = treeItem.getValue() + File.separator + command.substring(3).trim();
            if(command.substring(3).trim().equals("--help")){
                console.appendText("Usage: get [remote_file]");
            }else {
                client.writeToSocket(null,new File(file), FTPCommand.GET);
            }
        } else if (command.startsWith("put")) {
            String file = command.substring(3).trim();
            if(file.trim().equals("--help")){
                console.appendText("Usage: put [local_file]");
            }else {
                client.writeToSocket(new File(file),treeItem.getValue(), FTPCommand.PUT);
            }
        } else if (command.startsWith("connect")) {
            try {
                String credentials = command.substring(7);
                if(credentials.trim().equals("--help")){
                    console.appendText("Usage: connect [OPTION]... [VALUE]...\n\t-h,\t\thostname\n\t-p,\t\ttarget port\n\t-usr,\t\tlogin username\n\t-pw,\t\tlogin password");
                }else {
                    List<String> cr = Arrays.asList(credentials.split(" "));
                    String host = cr.get(cr.indexOf(cr.stream().filter(e -> {
                        return e.trim().contains("-h");
                    }).findFirst().get()) + 1);
                    int port = Integer.parseInt(cr.get(cr.indexOf(cr.stream().filter(e -> {
                        return e.trim().contains("-p");
                    }).findFirst().get()) + 1));
                    String usr = cr.get(cr.indexOf(cr.stream().filter(e -> {
                        return e.trim().contains("-usr");
                    }).findFirst().get()) + 1);
                    String pw = cr.get(cr.indexOf(cr.stream().filter(e -> {
                        return e.trim().contains("-pw");
                    }).findFirst().get()) + 1);
                    if (client == null) {
                        this.client = new FTPClient(usr, pw, host, port);
                        client.createSocket();
                        FTPClientUI.setClient(this.client);
                    }
                }
            } catch (Exception e) {
                console.appendText("Invalid option. Type connect --help for usage.");
            }
        }
        console.appendText("\n" + (client==null?System.getProperty("user.name"):client.getUsername()) + "@" + (client==null?"localhost":client.getHost()) + ":~" + pwd + "$ ");
        caretPosition = console.getCaretPosition();
    }

    /**
     * Creates table with files listed with {@code ls} command.
     */
    public void listFiles() throws IOException {
        StringBuilder output= new StringBuilder();
        for (TreeItem<File> item : treeItem.getChildren()) {
            output.append(String.format("%-12s%-20s%-10s%-15s%-20s\n", posixFilePermissions(item.getValue()),
                    Files.getOwner(item.getValue().toPath()).toString().replace("(User)","")
                            .substring(Files.getOwner(item.getValue().toPath()).toString().lastIndexOf("\\")+1),
                    (item.getValue().isDirectory()&&item.getValue().length()==0?"4096":item.getValue().length()),
                    (new SimpleDateFormat("MMM dd yyyy")).format(item.getValue().lastModified()),
                    item.getValue().getName()));
        }
        console.appendText(output.toString());
    }

    /**
     * Returns UNIX style permissions
     *
     * @param file File to be checked
     * @return String representation of file permissions
     */
    public String posixFilePermissions(File file){
        String perms=(file.canRead()?"r":"-")+(file.canWrite()?"w":"-")+(file.canExecute()?"x":"-");
        return getFileType(file)+perms+perms+perms;
    }

    /**
     * Returns UNIX style file type
     *
     * @param file File to be checked
     * @return String representation of file type
     */
    public String getFileType(File file){
        if(Files.isSymbolicLink(file.toPath())) return "s";
        if(file.isDirectory()) return "d";
        return "-";
    }

    /**
     * Method for getting cursor position in terminal
     * @return Cursor position
     */
    public int getCaretPosition() {
        return caretPosition;
    }
    /**
     * Method for writing last used command on pressing up key
     * @return Last used command
     */
    public String getLastCommand() {
        return lastCommand;
    }

    /**
     * Autocomplete when pressing tab
     */
    public void autoFill() {
        String toComplete=console.getText().substring(console.getText().lastIndexOf("$")+2).split(" ")[1].trim();
        for(TreeItem<File> item:treeItem.getChildren()){
            if(item.getValue().getName().startsWith(toComplete)){
                console.appendText(item.getValue().getName().substring(toComplete.length()));
                break;
            }
        }
    }
}
