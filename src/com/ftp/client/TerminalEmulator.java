package com.ftp.client;

import com.ftp.file.FTPCommand;
import com.ftp.file.FTPFile;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeItem;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
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
    public static TreeItem<FTPFile> treeItem;
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
                if(command.substring(2).trim().equals("..")){
                    pwd=pwd.substring(0,pwd.lastIndexOf("/"));
                    treeItem=treeItem.getParent();
                }else {
                    boolean found=false;
                    String folderName = command.substring(2).trim().replace("/", "");
                    for (TreeItem<FTPFile> item : treeItem.getChildren()) {
                        if (item.getValue().getName().equals(folderName)) {
                            treeItem = item;
                            found=true;
                        }
                    }
                    if(found){
                        pwd += "/" + folderName;
                    }else{
                        console.appendText(command+": No such file or directory");
                    }
                }
            }
        } else if (command.startsWith("get")) {
            String file = treeItem.getValue().getAbsolutePath() + File.separator + command.substring(3).trim();
            if(command.substring(3).trim().equals("--help")){
                console.appendText("Usage: get [remote_file]");
            }else {
                client.writeToSocket(null,new File(file), FTPCommand.GET);
                FTPClientUI.addToLog("Downloading...\n");
            }
        } else if (command.startsWith("put")) {
            String file = command.substring(3).trim();
            if(file.trim().equals("--help")){
                console.appendText("Usage: put [local_file]");
            }else {
                FTPClientUI.addToLog("Uploading...\n");
                client.writeToSocket(new File(file),new File(treeItem.getValue().getAbsolutePath()), FTPCommand.PUT);
            }
        }else if (command.startsWith("tree")) {
            FTPClientUI.addToLog("Refreshing tree view...\n");
            client.writeToSocket(null,null,FTPCommand.TREE);
        }else if (command.startsWith("rmdir")) {
            String file = command.substring(5).trim();
            if(file.trim().equals("--help")){
                console.appendText("Usage: rmdir [local_file] | [local_folder]");
            }else {
                FTPClientUI.addToLog("Removing selected file/folder...\n");
                client.writeToSocket(null,new File(treeItem.getValue().getAbsolutePath()+ File.separator+file), FTPCommand.RMDIR);
            }
        }else if (command.startsWith("close")) {
            client.writeToSocket(null,null,FTPCommand.CLOSE);
            FTPClientUI.addToLog("Disconnected.\n");
            caretPosition = console.getCaretPosition();
            console.appendText("\n"+System.getProperty("user.name") + "@localhost:~$ ");
            FTPClientUI.disconnect();
            return;
        }else if (command.startsWith("mkdir")) {
            String file = command.substring(5).trim();
            if(file.trim().equals("--help")){
                console.appendText("Usage: rmdir [local_file] | [local_folder]");
            }else {
                FTPClientUI.addToLog("Creating folder...\n");
                client.writeToSocket(null,new File(treeItem.getValue().getAbsolutePath()+ File.separator+file), FTPCommand.MKDIR);
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
                        FTPClientUI.addToLog("Connecting...\n");
                        this.client = new FTPClient(usr, pw, host, port);
                        client.createSocket();
                        FTPClientUI.setClient(this.client);
                        FTPClientUI.connect();
                    }
                }
            } catch (Exception e) {
                console.appendText("Invalid option. Type connect --help for usage.");
            }
        }else{
            try {
                console.appendText(execSysCommand(command));
            } catch (IOException e) {
                e.printStackTrace();
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
        for (TreeItem<FTPFile> item : treeItem.getChildren()) {
            output.append(String.format("%-12s%-20s%-10s%-15s%-20s\n", posixFilePermissions(item.getValue()),
                    item.getValue().getOwner().replace("(User)","")
                            .substring(item.getValue().getOwner().lastIndexOf("\\")+1),
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
    public String posixFilePermissions(FTPFile file){
        String perms=(file.canRead()?"r":"-")+(file.canWrite()?"w":"-")+(file.canExecute()?"x":"-");
        return getFileType(file)+perms+perms+perms;
    }

    public String execSysCommand(String command) throws IOException {
        StringBuilder value=new StringBuilder();
        Runtime rt = Runtime.getRuntime();
        Process proc = rt.exec("cmd.exe /c "+command); //TODO check if windows or linux

        BufferedReader stdInput = new BufferedReader(new
                InputStreamReader(proc.getInputStream()));

        BufferedReader stdError = new BufferedReader(new
                InputStreamReader(proc.getErrorStream()));

        String s = null;
        while ((s = stdInput.readLine()) != null) {
            value.append(s).append("\n");
        }

        while ((s = stdError.readLine()) != null) {
            value.append(s).append("\n");
        }
        return value.toString();
    }

    /**
     * Returns UNIX style file type
     *
     * @param file File to be checked
     * @return String representation of file type
     */
    public String getFileType(FTPFile file){
        if(file.isSymbolicLink()) return "s";
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
        for(TreeItem<FTPFile> item:treeItem.getChildren()){
            if(item.getValue().getName().startsWith(toComplete)){
                console.appendText(item.getValue().getName().substring(toComplete.length()));
                break;
            }
        }
    }
}
