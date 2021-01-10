package com.ftp.client;

import com.ftp.file.FTPCommand;
import com.ftp.file.FTPFile;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Callback;

import java.io.*;
import java.util.Date;
import java.util.Properties;

/**
 * This class is user interface for {@link FTPClient}. It contains input fields, areas, buttons for easier usage for performing FTP commands,
 * but also there is terminal which can be used for executing commands directly to {@link com.ftp.server.FTPServer}. This user interface executes same commands
 * in background (like {@link FTPCommand#GET},{@link FTPCommand#PUT}... to make easier usage of this client.
 *
 * @author Stefan
 */
public class FTPClientUI extends Application {
    public FTPFile selected = null;
    double xOffset;
    double yOffset;
    public static boolean connected=false;
    public static TreeItem<FTPFile> selectedFolder;
    public static FTPClient client;
    public static String logMessage = "";
    public static ProgressBar bar = new ProgressBar(0);
    public static Label speed = new Label();
    public static Button connect = new Button("Connect");
    private static final TextArea log = new TextArea();
    private static TerminalEmulator te;
    private static final TreeView<FTPFile> treeView = new TreeView<>(null);

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
        Button upload = new Button("Upload");
        Button pause = new Button("Pause");
        Button mkdir = new Button("Create folder");
        CheckBox rememberMe = new CheckBox("Remember me");
        TextArea console = new TextArea();
        HBox titleBar = new HBox(10);
        titleBar.setAlignment(Pos.TOP_RIGHT);
        titleBar.setPadding(new Insets(0, 0, 20, 0));
        Button x = new Button("X");
        Button mm = new Button("_");
        titleBar.getChildren().addAll(mm, x);
        console.appendText(System.getProperty("user.name") + "@localhost:~$ ");
        TextField newFolder = new TextField();
        TextField pathToFile = new TextField();
        bar.setPadding(new Insets(10));
        pathToFile.setEditable(false);
        VBox actions = new VBox(newFolder, mkdir);
        actions.setAlignment(Pos.CENTER);
        actions.setPadding(new Insets(10));
        actions.setSpacing(10);
        HBox hBox = new HBox(lbHost, host, lbPort, port, lbUsername, username, lbPassword, password, connect);
        hBox.setAlignment(Pos.CENTER);
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
        x.setOnAction(e -> {
            System.exit(0);
        });
        mm.setOnAction(e -> {
            Stage obj = (Stage) primaryStage.getScene().getWindow();
            obj.setIconified(true);
        });
        upload.setOnAction(e -> {
            if (selected==null||selectedFolder==null) {
                Alert a = new Alert(Alert.AlertType.WARNING);
                a.setContentText("File not choosen! Chose file first.");
                a.show();
            } else {
                File f = new File(pathToFile.getText());
                addToLog("Uploading " + f.getName() + "...\n");
                client.writeToSocket(f, new File(selectedFolder.getValue().getAbsolutePath()), FTPCommand.PUT);
            }
        });
        mkdir.setOnAction(e -> {
            if (treeView.getSelectionModel().getSelectedItem() == null || newFolder.getText() == null || newFolder.getText().equals("")) {
                Alert a = new Alert(Alert.AlertType.WARNING);
                a.setContentText("Folder not selected or folder name is empty!");
                a.show();
            } else {
                addToLog("Creating folder...\n");
                client.writeToSocket(null, new File(selectedFolder.getValue().getPath() + "/" + newFolder.getText()), FTPCommand.MKDIR);
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
            if(!connected) {
                addToLog("Connecting...\n");
                client = new FTPClient(username.getText(), password.getText(), host.getText(), Integer.parseInt(port.getText()));
                boolean success = client.createSocket();
                if (success) {
                    checkConnected(client, console, connect);
                } else {
                    addToLog("Failed to connect.\n");
                }
            }else{
                connected=false;
                client.writeToSocket(null,null,FTPCommand.CLOSE);
                addToLog("Disconnected.\n");
                console.appendText("\n"+System.getProperty("user.name") + "@localhost:~$ ");
                connect.setText("Connect");
            }
        });
        FileChooser fileChooser = new FileChooser();
        treeView.setCellFactory(new Callback<>() {
            public TreeCell<FTPFile> call(TreeView<FTPFile> tv) {
                return new TreeCell<>() {
                    @Override
                    protected void updateItem(FTPFile item, boolean empty) {
                        super.updateItem(item, empty);
                        setText((empty || item == null) ? "" : item.getName());
                    }
                };
            }
        });
        treeView.setEditable(true);

        treeView.setOnMouseClicked(event -> {
            try {
                @SuppressWarnings("unchecked")
                TreeView<FTPFile> tree = ((TreeView<FTPFile>) event.getSource());
                TreeItem<FTPFile> sel = tree.getSelectionModel().getSelectedItem();
                selectedFolder = sel.getValue().isDirectory() ? sel : sel.getParent();
                selected = sel.getValue();
            } catch (Exception ignored) {
            }
        });
        HBox treeAndLog = new HBox(treeView, log, actions);
        treeAndLog.setAlignment(Pos.CENTER);
        treeAndLog.setSpacing(10);
        treeAndLog.setPadding(new Insets(10));
        treeAndLog.setMaxHeight(300);
        Button selectFile = new Button("Select File");
        selectFile.setOnAction(e -> {
            File selectedFile = fileChooser.showOpenDialog(primaryStage);
            pathToFile.setText(selectedFile.getAbsolutePath());
        });
        HBox selectUpload = new HBox(10);
        selectUpload.getChildren().addAll(selectFile, upload);
        TabPane tabPane = new TabPane();
        Tab terminal1 = new Tab("Terminal 1", console);
        tabPane.getTabs().add(terminal1);
        selectUpload.setPadding(new Insets(0, 0, 10, 0));
        VBox fileUpload = new VBox(10);
        fileUpload.getChildren().addAll(pathToFile, selectUpload);
        HBox progress = new HBox(10);
        progress.setAlignment(Pos.CENTER);
        speed.setMinWidth(30);
        speed.setMaxWidth(80);
        pause.setOnAction(e->{
            if(client.getPause()){
                pause.setText("Pause");
                client.setPause(false);
            }else{
                pause.setText("Resume");
                client.setPause(true);
            }
        });
        progress.getChildren().addAll(bar,pause,speed);
        VBox vBox = new VBox(titleBar, hBox, rememberMe, progress, treeAndLog, fileUpload, tabPane);
        vBox.setAlignment(Pos.CENTER);
        treeView.setMaxHeight(400);
        treeView.setMaxWidth(300);
        vBox.setPadding(new Insets(20));
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
        scene.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                xOffset = primaryStage.getX() - event.getScreenX();
                yOffset = primaryStage.getY() - event.getScreenY();
            }
        });
        scene.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                primaryStage.setX(event.getScreenX() + xOffset);
                primaryStage.setY(event.getScreenY() + yOffset);
            }
        });
        MenuItem downloadItem = new MenuItem("Download");
        MenuItem deleteItem = new MenuItem("Delete");
        MenuItem detailsItem = new MenuItem("Details");
        MenuItem refreshItem = new MenuItem("Refresh");
        downloadItem.setOnAction(e -> {
            if (selected == null) {
                Alert a = new Alert(Alert.AlertType.WARNING);
                a.setContentText("Folder/file not selected! Select file/folder first.");
                a.show();
            } else {
                addToLog("Downloading " + selected.getName() + "...\n");
                client.writeToSocket(null, new File(selected.getAbsolutePath()), FTPCommand.GET);
            }
        });
        deleteItem.setOnAction(e -> {
            if (selected == null) {
                Alert a = new Alert(Alert.AlertType.WARNING);
                a.setContentText("Folder/file not selected! Select file/folder first.");
                a.show();
            } else {
                addToLog("Removing selected file/folder...\n");
                client.writeToSocket(null, new File(selected.getAbsolutePath()), FTPCommand.RMDIR);
            }
        });
        detailsItem.setOnAction(e -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("Details for " + selected.getName());
            a.setContentText("Path: " + selected.getAbsolutePath() + "\nSize: " + selected.length() + " B\nLast modified on: " + new Date(selected.lastModified()) + "\n");
            a.show();
        });
        refreshItem.setOnAction(e->{
            client.writeToSocket(null,null,FTPCommand.TREE);
            addToLog("Refreshing tree view...\n");
        });
        ContextMenu rootContextMenu = new ContextMenu();
        rootContextMenu.getItems().add(downloadItem);
        rootContextMenu.getItems().add(deleteItem);
        rootContextMenu.getItems().add(detailsItem);
        rootContextMenu.getItems().add(refreshItem);

        treeView.setContextMenu(rootContextMenu);
        bar.setMinWidth(scene.getWidth() - 170 - newFolder.getWidth());
        primaryStage.setMinHeight(800);
        primaryStage.setMinWidth(1000);
        scene.getStylesheets().add("MainClientUI.css");
        scene.setFill(Color.TRANSPARENT);
        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void checkConnected(FTPClient client, TextArea console, Button connect) {
        Platform.runLater(()->{
            connected=client.checkConnected();
            if(connected) {
                console.setText(client.getUsername() + "@" + client.getHost() + ":~$ ");
                connect.setText("Disconnect");
            }else{
                connect.setText("Connect");
            }
        });
    }

    public static void disconnect() {
        connected=false;
        connect.setText("Connect");
    }

    public static void connect() {
        connected=client.checkConnected();
        if(connected) {
            connect.setText("Disconnect");
        }else{
            connect.setText("Connect");
        }
    }

    private static void expandTreeView(TreeItem<FTPFile> selectedItem) {
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

    public static void updateBar(double percentage, double speed) {
        Platform.runLater(() -> {
            bar.setProgress(percentage);
            FTPClientUI.speed.setText((long) speed + " B/s");
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
