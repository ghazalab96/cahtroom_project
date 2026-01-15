package at.ac.hcw.chat.client;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node; //
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.stage.Window;
import java.io.*;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ChatController {
    // FXML Connections
    @FXML private TextField ipField, portField, nameField, messageField;
    @FXML private Label statusLabel, welcomeLabel;
    @FXML private ListView<String> userListView;
    @FXML private TabPane chatTabPane;
    @FXML private VBox chatBox;
    @FXML private ImageView selectedAvatarPreview, userAvatarImage;

    // Networking Static Variables
    private static Socket socket;
    private static PrintWriter out;
    private static BufferedReader in;
    private static String userName;
    private static volatile boolean isRunning = false;
    private static ChatController activeController;

    // Maps to track Private Chats
    private static final Map<String, VBox> privateChatLog = new HashMap<>();
    private static final Map<String, Tab> tabMap = new HashMap<>();

    // State Transfer
    private static String currentAvatarPath = "/at/ac/hcw/chat/client/images/profile0.jpeg";
    private static String tempIP;
    private static int tempPort;

    @FXML
    public void initialize() {
        activeController = this;

        if (userListView != null) {
            userListView.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    String selected = userListView.getSelectionModel().getSelectedItem();
                    if (selected != null && !selected.equals(userName)) openPrivateTab(selected, true);
                }
            });
        }

        if (chatTabPane != null) {
            chatTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                if (newTab != null) newTab.setGraphic(null);
            });
        }
    }

    private void openPrivateTab(String targetUser, boolean shouldFocus) {
        Platform.runLater(() -> {
            if (activeController == null || activeController.chatTabPane == null) return;
            if (tabMap.containsKey(targetUser)) {
                if (shouldFocus) activeController.chatTabPane.getSelectionModel().select(tabMap.get(targetUser));
                return;
            }

            VBox privateBox = new VBox(15);
            privateBox.setStyle("-fx-background-color: white; -fx-padding: 15;");
            ScrollPane scrollPane = new ScrollPane(privateBox);
            scrollPane.setFitToWidth(true);
            scrollPane.setStyle("-fx-background-color: white;");

            Tab newTab = new Tab(targetUser, scrollPane);
            newTab.setClosable(true);
            newTab.setOnClosed(e -> { privateChatLog.remove(targetUser); tabMap.remove(targetUser); });

            activeController.chatTabPane.getTabs().add(newTab);
            privateChatLog.put(targetUser, privateBox);
            tabMap.put(targetUser, newTab);
            if (shouldFocus) activeController.chatTabPane.getSelectionModel().select(newTab);
        });
    }

    private void addMessageBubble(VBox container, String name, String avatarPath, String message, boolean isSelf) {
        if (container == null) return;
        Platform.runLater(() -> {
            try {
                InputStream is = getClass().getResourceAsStream(avatarPath);
                if (is == null) is = getClass().getResourceAsStream("/at/ac/hcw/chat/client/images/profile0.jpeg");

                ImageView avatarView = new ImageView(new Image(is));
                avatarView.setFitHeight(40); avatarView.setFitWidth(40);
                avatarView.setClip(new Circle(20, 20, 20));

                Label nameLbl = new Label(name + "  " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
                nameLbl.setStyle("-fx-font-size: 9px; -fx-text-fill: #90A4AE; -fx-font-weight: bold;");

                Label msgContent = new Label(message);
                msgContent.setWrapText(true); msgContent.setMaxWidth(300);

                VBox bubble = new VBox(3, nameLbl, msgContent);
                if (isSelf) {
                    bubble.setStyle("-fx-background-color: #E3F2FD; -fx-background-radius: 15 0 15 15; -fx-padding: 8 12;");
                    bubble.setAlignment(Pos.TOP_RIGHT);
                } else {
                    bubble.setStyle("-fx-background-color: #F1F1F1; -fx-background-radius: 0 15 15 15; -fx-padding: 8 12;");
                    bubble.setAlignment(Pos.TOP_LEFT);
                }

                HBox row = new HBox(10);
                if (isSelf) {
                    row.setAlignment(Pos.TOP_RIGHT); row.getChildren().addAll(bubble, avatarView);
                } else {
                    row.setAlignment(Pos.TOP_LEFT); row.getChildren().addAll(avatarView, bubble);
                }
                container.getChildren().add(row);
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    private void handleMessageRouting(String packet) {
        if (activeController == null) return;
        if (packet.startsWith("USERLIST:")) {
            String[] users = packet.substring(9).split(",");
            Platform.runLater(() -> {
                if (activeController.userListView != null) {
                    activeController.userListView.getItems().clear();
                    for (String u : users) if (!u.isEmpty()) activeController.userListView.getItems().add(u);
                }
            });
        } else if (packet.contains("|")) {
            String[] parts = packet.split("\\|");
            if (parts.length < 3) return;
            String header = parts[0]; String avatar = parts[1]; String text = parts[2];

            if (header.startsWith("[Private from ")) {
                String sender = header.substring(14, header.length() - 1);
                openPrivateTab(sender, false);
                Platform.runLater(() -> {
                    VBox box = privateChatLog.get(sender);
                    if (box != null) {
                        addMessageBubble(box, sender, avatar, text, false);
                        notifyTab(sender);
                    }
                });
            } else if (header.startsWith("[Private to ")) {
                String target = header.substring(12, header.length() - 1);
                openPrivateTab(target, false);
                Platform.runLater(() -> {
                    VBox box = privateChatLog.get(target);
                    if (box != null) addMessageBubble(box, userName, currentAvatarPath, text, true);
                });
            } else {
                boolean isSelf = header.equals(userName);
                if (activeController.chatBox != null) {
                    addMessageBubble(activeController.chatBox, header, avatar, text, isSelf);
                }
            }
        }
    }

    private void notifyTab(String name) {
        Tab t = tabMap.get(name);
        if (t != null && !chatTabPane.getSelectionModel().getSelectedItem().equals(t)) {
            Circle dot = new Circle(5); dot.setStyle("-fx-fill: #FF5252; -fx-stroke: white;");
            t.setGraphic(dot);
        }
    }

    @FXML protected void onSendButtonClick() {
        String msg = messageField.getText().trim();
        if (msg.isEmpty() || out == null) return;
        Tab sel = chatTabPane.getSelectionModel().getSelectedItem();
        if (sel.getText().equals("General")) out.println(msg);
        else out.println("@" + sel.getText() + ": " + msg);
        messageField.clear();
    }

    @FXML protected void onConnectButtonClick() {
        if (nameField.getText().trim().isEmpty()) {
            statusLabel.setText("Please enter your name!");
            return;
        }
        userName = nameField.getText().trim();
        tempIP = ipField.getText().trim(); tempPort = Integer.parseInt(portField.getText().trim());
        switchScene("/at/ac/hcw/chat/client/avatar-view.fxml");
    }

    @FXML private void onAvatarSelected(javafx.scene.input.MouseEvent e) {
        ImageView iv = (ImageView) e.getSource();
        String url = iv.getImage().getUrl();
        currentAvatarPath = url.substring(url.indexOf("/at/ac/hcw/"));
        if (selectedAvatarPreview != null) selectedAvatarPreview.setImage(iv.getImage());
    }

    @FXML protected void onFinalJoinClick() {
        try {
            switchScene("/at/ac/hcw/chat/client/chat-view.fxml");
            new Thread(() -> {
                try {
                    Thread.sleep(500);
                    socket = new Socket(tempIP, tempPort);
                    out = new PrintWriter(socket.getOutputStream(), true);
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    out.println(userName + "|" + currentAvatarPath);
                    isRunning = true;
                    new Thread(this::listenToServer).start();
                } catch (Exception ex) { ex.printStackTrace(); }
            }).start();
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void listenToServer() {
        try {
            String line;
            while (isRunning && (line = in.readLine()) != null) {
                final String m = line; Platform.runLater(() -> handleMessageRouting(m));
            }
        } catch (IOException e) {} finally { if (isRunning) Platform.runLater(this::showErrorPopup); }
    }

    private void showErrorPopup() {
        Platform.runLater(() -> {
            try {
                isRunning = false; if (socket != null) socket.close();
                if (activeController != null && activeController.chatTabPane != null) {
                    ((Stage) activeController.chatTabPane.getScene().getWindow()).close();
                }
                FXMLLoader l = new FXMLLoader(getClass().getResource("/at/ac/hcw/chat/client/error-view.fxml"));
                Stage s = new Stage(); s.setScene(new Scene(l.load())); s.setTitle("Error"); s.show();
            } catch (Exception e) {}
        });
    }

    /**
     * UPDATED: Handles disconnect and returns to Login reliably.
     */
    @FXML
    protected void onDisconnectButtonClick(ActionEvent event) {
        isRunning = false;
        try {
            if (socket != null) socket.close();
            privateChatLog.clear(); tabMap.clear();

            // Find the stage from the event source (the button clicked)
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

            switchSceneOnStage(stage, "/at/ac/hcw/chat/client/login-view.fxml");
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void switchSceneOnStage(Stage s, String p) throws IOException {
        FXMLLoader l = new FXMLLoader(getClass().getResource(p));
        s.setScene(new Scene(l.load()));
        ChatController next = l.getController();
        activeController = next;
        if (p.contains("chat-view")) {
            next.welcomeLabel.setText("User: " + userName);
            next.userAvatarImage.setImage(new Image(getClass().getResourceAsStream(currentAvatarPath)));
        }
    }

    private void switchScene(String p) {
        Platform.runLater(() -> {
            try {
                Stage current;
                if (ipField != null) current = (Stage) ipField.getScene().getWindow();
                else if (selectedAvatarPreview != null) current = (Stage) selectedAvatarPreview.getScene().getWindow();
                else current = (Stage) Window.getWindows().get(0);
                switchSceneOnStage(current, p);
            } catch (Exception e) { e.printStackTrace(); }
        });
    }
}