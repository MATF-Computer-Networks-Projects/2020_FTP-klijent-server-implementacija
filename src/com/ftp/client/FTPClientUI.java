package com.ftp.client;

import com.ftp.file.FTPCommand;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

/**
 * This class is user interface for {@link FTPClient}. It contains input fields, areas, buttons for easier usage for performing FTP commands,
 * but also there is terminal which can be used for executing commands directly to {@link com.ftp.server.FTPServer}. This user interface executes same commands
 * in background (like {@link FTPCommand#GET},{@link FTPCommand#PUT}... to make easier usage of this client.
 *
 * @author Stefan
 */
public class FTPClientUI extends Application {
    public File selected = null;
    public static TreeItem<File> selectedFolder;
    public static FTPClient client;
    public static String logMessage = "";
    public static ProgressBar bar = new ProgressBar(0);
    private static TextArea log = new TextArea();
    private static TerminalEmulator te;
    private static TreeView<File> treeView = new TreeView<File>(null);

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("FTP Client");
        Label lbHost = new Label("Host:");
        TextField host = new TextField();
        Label lbPort = new Label("Port:");
        TextField port = new TextField();
        Label lbUsername = new Label("Username:");
        TextField username = new TextField();
        Label lbPassword = new Label("Password:");
        PasswordField password = new PasswordField();
        Button connect = new Button("Connect");
        Button upload = new Button("Upload");
        Button download = new Button("Download");
        Button mkdir = new Button("Create folder");
        Button rmdir = new Button("Delete folder/file");
        CheckBox rememberMe = new CheckBox("Remember me");
        TextArea console = new TextArea();
        console.appendText(System.getProperty("user.name") + "@localhost:~$ ");
        TextField newFolder = new TextField();
        TextField pathToFile = new TextField();
        bar.setPadding(new Insets(10));
        pathToFile.setEditable(false);
        VBox actions = new VBox(download, newFolder, mkdir, rmdir);
        actions.setPadding(new Insets(10));
        actions.setSpacing(10);
        HBox hBox = new HBox(lbHost, host, lbPort, port, lbUsername, username, lbPassword, password, connect);
        hBox.setSpacing(10);
        hBox.setPadding(new Insets(10));
        te = new TerminalEmulator(client, console);
        rememberMe.setPadding(new Insets(10));
        FileReader reader = new FileReader("connect.properties");
        Properties props = new Properties();
        props.load(reader);
        if (Integer.parseInt(props.get("remember").toString()) == 1) {
            username.setText(props.get("username").toString());
            password.setText(props.get("password").toString());
            host.setText(props.get("host").toString());
            port.setText(props.get("port").toString());
            rememberMe.setSelected(true);
        }
        rmdir.setOnAction(e -> {
            if (selected == null) {
                Alert a = new Alert(Alert.AlertType.WARNING);
                a.setContentText("Folder/file not selected! Select file/folder first.");
                a.show();
            } else {
                addToLog("Removing selected file/folder...\n");
                client.writeToSocket(null, selected, FTPCommand.RMDIR);
            }
        });
        download.setOnAction(e -> {
            if (selected == null) {
                Alert a = new Alert(Alert.AlertType.WARNING);
                a.setContentText("Folder/file not selected! Select file/folder first.");
                a.show();
            } else {
                addToLog("Downloading " + selected.getName() + "...\n");
                client.writeToSocket(null, selected, FTPCommand.GET);
            }
        });
        upload.setOnAction(e -> {
            if (false) {
                Alert a = new Alert(Alert.AlertType.WARNING);
                a.setContentText("File not choosen! Chose file first.");
                a.show();
            } else {
                File f=new File(pathToFile.getText());
                addToLog("Uploading " + f.getName() + "...\n");
                client.writeToSocket(f, selectedFolder.getValue(), FTPCommand.PUT);
            }
        });
        mkdir.setOnAction(e -> {
            if (treeView.getSelectionModel().getSelectedItem() == null || newFolder.getText() == null || newFolder.getText().equals("")) {
                Alert a = new Alert(Alert.AlertType.WARNING);
                a.setContentText("Folder not selected or folder name is empty!");
                a.show();
            } else {
                addToLog("Creating folder...\n");
                client.writeToSocket(null, new File(selectedFolder.getValue().getPath()+"/"+newFolder.getText()), FTPCommand.MKDIR);
            }
        });
        connect.setOnAction(e -> {
            Properties p = new Properties();
            if (rememberMe.isSelected()) {
                p.setProperty("remember", "1");
                p.setProperty("username", username.getText());
                p.setProperty("password", password.getText());
                p.setProperty("host", host.getText());
                p.setProperty("port", port.getText());
                try {
                    p.store(new FileWriter("connect.properties"), "Client connection credentials");
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            } else {
                try {
                    p.setProperty("remember", "0");
                    p.store(new FileWriter("connect.properties"), "Client connection credentials");
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
            addToLog("Connecting...\n");
            client = new FTPClient(username.getText(), password.getText(), host.getText(), Integer.parseInt(port.getText()));
            client.createSocket();
            console.setText(username.getText() + "@" + host.getText() + ":~$ ");
        });
        FileChooser fileChooser = new FileChooser();
        treeView.setCellFactory(new Callback<TreeView<File>, TreeCell<File>>() {
            public TreeCell<File> call(TreeView<File> tv) {
                return new TreeCell<File>() {
                    @Override
                    protected void updateItem(File item, boolean empty) {
                        super.updateItem(item, empty);
                        setText((empty || item == null) ? "" : item.getName());
                    }
                };
            }
        });
        treeView.setEditable(true);

        treeView.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                try {
                    TreeView<File> tree = ((TreeView<File>) event.getSource());
                    TreeItem<File> sel = tree.getSelectionModel().getSelectedItem();
                    selectedFolder = sel.getValue().isDirectory()?sel:sel.getParent();
                    selected = sel.getValue();
                } catch (Exception e) {
                }
            }
        });
        HBox treeAndLog = new HBox(treeView, log, actions);
        treeAndLog.setSpacing(10);
        treeAndLog.setPadding(new Insets(10));
        Button selectFile = new Button("Select File");
        selectFile.setOnAction(e -> {
            File selectedFile = fileChooser.showOpenDialog(primaryStage);
            pathToFile.setText(selectedFile.getAbsolutePath());
        });
        TabPane tabPane = new TabPane();
        Tab terminal1 = new Tab("Terminal 1", console);
        tabPane.getTabs().add(terminal1);
        VBox vBox = new VBox(hBox, rememberMe, bar, treeAndLog, pathToFile, selectFile, upload, tabPane);
        treeView.setMaxHeight(400);
        treeView.setMaxWidth(300);
        vBox.setPadding(new Insets(10));
        Scene scene = new Scene(vBox, 960, 600);
        console.getStylesheets().add("ClientUI.css");
        console.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                te.executeCommand();
            }
        });
        console.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.BACK_SPACE) {
                if (console.getCaretPosition() <= te.getCaretPosition()) {
                    e.consume();
                }
            }
        });
        console.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.TAB) {
                te.autoFill();
                e.consume();
            }
        });
        console.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.UP) {
                if (console.getText().substring(console.getText().lastIndexOf("$") + 1).trim().length() == 0) {
                    console.appendText(te.getLastCommand());
                }
                e.consume();
            }
        });
        bar.setMinWidth(scene.getWidth() - 20 - newFolder.getWidth());
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private static void expandTreeView(TreeItem<File> selectedItem) {
        if (selectedItem != null) {
            expandTreeView(selectedItem.getParent());
            if (!selectedItem.isLeaf()) {
                selectedItem.setExpanded(true);
            }
        }
    }

    public static void addToLog(String message) {
        logMessage += message;
        log.setText(logMessage);
        Platform.runLater(() -> {
            treeView.setRoot(client.getTree());
            treeView.refresh();
            if (selectedFolder != null)
                expandTreeView(selectedFolder);
        });
    }

    public static void setClient(FTPClient client) {
        FTPClientUI.client = client;
    }

    public static void updateBar(double percentage) {
        Platform.runLater(() -> {
            bar.setProgress(percentage);
        });
    }

    public static FTPClient getClient() {
        return client;
    }

    public static void main(String[] args) {
        launch(args);
    }
}

//connect -h localhost -p 3339 -usr admin -pw admin
