package at.ac.hcw.chat.client;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
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

/**
 * Final Advanced Controller for HCW Chat Room.
 * Preserves: Multi-stage Login, Private Tabs, Chat Bubbles, Avatars, Timestamps,
 * and fixed Red Bullet Notifications.
 */
public class ChatController {
    // UI Connections from FXML
    @FXML private TextField ipField, portField, nameField, messageField;
    @FXML private Label statusLabel, welcomeLabel;
    @FXML private ListView<String> userListView;
    @FXML private TabPane chatTabPane;
    @FXML private VBox chatBox; // Container for General chat bubbles
    @FXML private ImageView selectedAvatarPreview, userAvatarImage;

    // Networking Resources
    private static Socket socket;
    private static PrintWriter out;
    private static BufferedReader in;
    private static String userName;
    private static volatile boolean isRunning = false;

    /**
     * static activeController: Keeps a reference to the UI currently on screen.
     * This is vital for updating the UI from the background networking thread.
     */
    private static ChatController activeController;

    // Map to store private chat message containers (VBoxes) for each user
    private static final Map<String, VBox> privateChatLog = new HashMap<>();

    // Map to store specific Tab objects to manage Red Bullet notifications
    private static final Map<String, Tab> tabMap = new HashMap<>();

    // State transfer variables across scenes
    private static String currentAvatarPath = "/at/ac/hcw/chat/client/images/profile0.jpeg";
    private static String tempIP;
    private static int tempPort;

    @FXML
    public void initialize() {
        // Register this instance as the active controller immediately
        activeController = this;

        // Double-click listener on user list to open private tabs
        if (userListView != null) {
            userListView.setOnMouseClicked(event -> {
                // If the user double-clicks a name that isn't their own
                if (event.getClickCount() == 2) {
                    String selected = userListView.getSelectionModel().getSelectedItem();
                    if (selected != null && !selected.equals(userName)) {
                        // Open or switch to a private tab
                        openPrivateTab(selected, true); // true = focus/switch to this tab
                    }
                }
            });
        }

        /*
         * RED BULLET CLEAR LOGIC:
         * This listener detects when the user clicks on a different tab.
         * If the newly selected tab has a red dot (Graphic), it removes it.
         */
        if (chatTabPane != null) {
            chatTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                if (newTab != null && newTab.getGraphic() != null) {
                    newTab.setGraphic(null); // Clear the notification dot
                }
            });
        }
    }

    /**
     * Helper to create the actual Red Bullet UI element.
     */
    private Circle createNotificationDot() {
        Circle dot = new Circle(5);
        dot.setStyle("-fx-fill: #FF5252; -fx-stroke: white; -fx-stroke-width: 1;");
        return dot;
    }

    /**
     * Manages Private Tab creation and switching.
     */
    private void openPrivateTab(String targetUser, boolean shouldFocus) {
        Platform.runLater(() -> {
            if (activeController == null || activeController.chatTabPane == null) return;

            // If the tab is already open, just switch to it if requested
            if (tabMap.containsKey(targetUser)) {
                if (shouldFocus) activeController.chatTabPane.getSelectionModel().select(tabMap.get(targetUser));
                return;
            }

            // Create new graphical containers for the private chat
            VBox privateBox = new VBox(15);
            privateBox.setStyle("-fx-background-color: white; -fx-padding: 15;");
            ScrollPane scrollPane = new ScrollPane(privateBox);
            scrollPane.setFitToWidth(true);
            scrollPane.setStyle("-fx-background-color: white;");

            // Create the Tab object
            Tab newTab = new Tab(targetUser, scrollPane);
            newTab.setClosable(true);

            // Cleanup maps when user closes the tab
            newTab.setOnClosed(e -> {
                privateChatLog.remove(targetUser);
                tabMap.remove(targetUser);
            });

            activeController.chatTabPane.getTabs().add(newTab);
            privateChatLog.put(targetUser, privateBox);
            tabMap.put(targetUser, newTab);

            // Switch to the new tab if requested
            if (shouldFocus) activeController.chatTabPane.getSelectionModel().select(newTab);
        });
    }

    /**
     * Programmatically builds a Telegram-style message bubble.
     */
    private void addMessageBubble(VBox container, String name, String avatarPath, String message, boolean isSelf) {
        if (container == null) return;
        Platform.runLater(() -> {
            try {
                // Safely load the avatar image
                InputStream is = getClass().getResourceAsStream(avatarPath);
                // Fallback to default avatar if path is invalid
                if (is == null) is = getClass().getResourceAsStream("/at/ac/hcw/chat/client/images/profile0.jpeg");

                // Configure the avatar display
                ImageView avatarView = new ImageView(new Image(is));
                avatarView.setFitHeight(40); avatarView.setFitWidth(40);
                avatarView.setClip(new Circle(20, 20, 20));

                // Create name and timestamp label
                Label nameLbl = new Label(name + "  " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
                nameLbl.setStyle("-fx-font-size: 9px; -fx-text-fill: #90A4AE; -fx-font-weight: bold;");

                // Create message content label
                Label msgContent = new Label(message);
                msgContent.setWrapText(true); msgContent.setMaxWidth(300);


                // Build the message bubble (VBox)
                VBox bubble = new VBox(3, nameLbl, msgContent);
                if (isSelf) {
                    // Right-aligned blue bubble for current user
                    bubble.setStyle("-fx-background-color: #E3F2FD; -fx-background-radius: 15 0 15 15; -fx-padding: 8 12;");
                    bubble.setAlignment(Pos.TOP_RIGHT);
                } else {
                    // Left-aligned grey bubble for other users
                    bubble.setStyle("-fx-background-color: #F1F1F1; -fx-background-radius: 0 15 15 15; -fx-padding: 8 12;");
                    bubble.setAlignment(Pos.TOP_LEFT);
                }

                // Row container (HBox) to hold bubble and avatar side-by-side
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

    /**
     * Routes incoming packets to the correct UI components.
     * Preserves Red Bullet notifications and Private Chat routing.
     */
    private void handleMessageRouting(String packet) {
        if (activeController == null) return;

        // Update the sidebar user list
        if (packet.startsWith("USERLIST:")) {
            String[] users = packet.substring(9).split(",");
            activeController.userListView.getItems().clear();
            for (String u : users) if (!u.isEmpty()) activeController.userListView.getItems().add(u);
        }
        // Handle chat messages separated by the Pipe "|" character
        else if (packet.contains("|")) {
            String[] parts = packet.split("\\|"); // Split into Header, Avatar, and Message
            if (parts.length < 3) return;
            String header = parts[0]; String avatar = parts[1]; String text = parts[2];

            // Scenario 1: Private Message Error (User offline) -> ROUTE TO PRIVATE TAB
            if (header.startsWith("[Private Error ")) {
                String target = header.substring(15, header.length() - 1);
                openPrivateTab(target, false); // Ensure tab is open
                Platform.runLater(() -> {
                    VBox box = privateChatLog.get(target);
                    if (box != null) addMessageBubble(box, "System", avatar, text, false);
                });
            }
            // Scenario 2: Incoming Private Message from someone else
            else if (header.startsWith("[Private from ")) {
                String sender = header.substring(14, header.length() - 1);
                openPrivateTab(sender, false);
                Platform.runLater(() -> {
                    VBox box = privateChatLog.get(sender);
                    if (box != null) {
                        addMessageBubble(box, sender, avatar, text, false);
                        notifyTab(sender);
                    }
                });
            }

            // Scenario 3: Confirmation of your own private message
            else if (header.startsWith("[Private to ")) {
                String target = header.substring(12, header.length() - 1);
                openPrivateTab(target, false);
                Platform.runLater(() -> {
                    VBox box = privateChatLog.get(target);
                    if (box != null) addMessageBubble(box, userName, currentAvatarPath, text, true);
                });
            }

            // Scenario 4: Public Chat
            else {
                boolean isSelf = header.equals(userName);
                if (activeController.chatBox != null) {
                    addMessageBubble(activeController.chatBox, header, avatar, text, isSelf);

                    // Show red dot on General tab if someone else messaged while you were in a PV
                    if (!isSelf) notifyGeneral();
                }
            }
        }
    }

    /**
     * Shows a red dot on the General tab if the user is currently in a private chat.
     */
    private void notifyGeneral() {
        Platform.runLater(() -> {
            // Use activeController to ensure we are talking to the visible window
            if (activeController != null && activeController.chatTabPane != null && !activeController.chatTabPane.getTabs().isEmpty()) {
                Tab generalTab = activeController.chatTabPane.getTabs().get(0);

                // Only show the dot if General tab is NOT the one currently selected
                Tab selectedTab = activeController.chatTabPane.getSelectionModel().getSelectedItem();
                // Check if the current visible tab is NOT the general tab
                if (selectedTab != null && !selectedTab.equals(generalTab)) {
                    generalTab.setGraphic(createNotificationDot());
                }
            }
        });
    }

    /**
     * Shows a red dot on a specific private tab.
     */
    private void notifyTab(String name) {
        Platform.runLater(() -> {
            if (activeController == null) return;

            Tab targetTab = tabMap.get(name);
            if (targetTab != null) {
                // Only show the dot if this private tab is NOT the active one
                Tab selectedTab = activeController.chatTabPane.getSelectionModel().getSelectedItem();
                // Check if the current visible tab is NOT the one that received the message
                if (selectedTab != null && !selectedTab.equals(targetTab)) {
                    targetTab.setGraphic(createNotificationDot());
                }
            }
        });
    }
    @FXML protected void onSendButtonClick() {
        String msg = messageField.getText().trim();
        if (msg.isEmpty() || out == null) return;
        // the tab which the client is looking at.
        Tab sel = chatTabPane.getSelectionModel().getSelectedItem();
        // Route message based on current active tab (General vs @User)
        if (sel.getText().equals("General")) out.println(msg);
        else out.println("@" + sel.getText() + ": " + msg);
        messageField.clear();
    }

    // --- Standard Methods (Time, Network, Scenes) ---

    private void listenToServer() {
        try {
            String line;
            // Blocking call: readLine() waits until the server sends a message
            while (isRunning && (line = in.readLine()) != null) {
                final String m = line; Platform.runLater(() -> handleMessageRouting(m)); // Pass data to UI router
            }
        } catch (IOException e) {} finally {
            // when is running is true, and we are out of while loop, means the server is down.
            if (isRunning) Platform.runLater(this::showErrorPopup); // Trigger error UI if loop breaks
        }
    }

    /**
     * Triggered by the Connect button.
     * Performs a strict multi-step validation of Name, IP, and Port.
     */
    @FXML
    protected void onConnectButtonClick() {
        String nameInput = nameField.getText().trim();//trim() --> wenn es leerzeichen gibt wird gelöscht
        String ipInput = ipField.getText().trim();
        String portInput = portField.getText().trim();

        // 1. Basic validation: Check if any field is empty
        if (nameInput.isEmpty()) {
            showLoginError("Please enter your name!");
            return;
        }
        if (ipInput.isEmpty() || portInput.isEmpty()) {
            showLoginError("Check IP address and port number");
            return;
        }

        /*
         * 2. STRICT NETWORK VALIDATION:
         * We attempt a real connection test. If this fails, the user cannot see the avatars.
         */
        try {
            int port = Integer.parseInt(portInput);

            // Create an un-connected socket
            Socket testSocket = new Socket();

            /*
             * Attempt to connect with a short timeout (1.5 seconds).
             * InetSocketAddress handles both IP format and Hostname reachability.
             */
            testSocket.connect(new java.net.InetSocketAddress(ipInput, port), 1500);

            // If the code reaches here, the server is DEFINITELY online and the IP is correct.
            testSocket.close(); // Close the test probe immediately

            // Save verified data to static variables
            userName = nameInput;
            tempIP = ipInput;
            tempPort = port;

            // Everything is valid, proceed to Avatar selection
            switchScene("/at/ac/hcw/chat/client/avatar-view.fxml");

        } catch (NumberFormatException e) {
            showLoginError("Check IP address and port number");
        } catch (java.net.UnknownHostException e) {
            // This catches cases where the IP address format is invalid
            showLoginError("Invalid IP address format");
        } catch (java.io.IOException e) {
            // This catches "Connection Refused" (wrong port) or "Timed Out" (wrong IP)
            showLoginError("Check IP address and port number");
        }
    }

    /**
     * Helper to display error messages on the login screen.
     */
    private void showLoginError(String message) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(message);
                statusLabel.setStyle("-fx-text-fill: #E57373;"); // Muted soft red
            }
        });
    }


    @FXML private void onAvatarSelected(javafx.scene.input.MouseEvent e) {
        ImageView iv = (ImageView) e.getSource(); // Get the clicked image
        String url = iv.getImage().getUrl();
        // Extract local path from URL
        currentAvatarPath = url.substring(url.indexOf("/at/ac/hcw/"));
        if (selectedAvatarPreview != null) selectedAvatarPreview.setImage(iv.getImage());
    }

    @FXML protected void onFinalJoinClick() {
        try {
            switchScene("/at/ac/hcw/chat/client/chat-view.fxml");
            new Thread(() -> {
                try {
                    Thread.sleep(500); // Wait for UI to settle
                    socket = new Socket(tempIP, tempPort);//server ip und port
                    out = new PrintWriter(socket.getOutputStream(), true);
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    // Send initial handshake packet: "Name|AvatarPath"
                    out.println(userName + "|" + currentAvatarPath);//sendet Anmelde informationen an server
                    isRunning = true;
                    new Thread(this::listenToServer).start();//startet einen thread der hört zu server
                } catch (Exception ex) { ex.printStackTrace(); }
            }).start();//startet thread
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    /**
     * Closes the chat window and displays the Error View.
     * Improved: Uses a global search to find and close the active Stage.
     */

    private void showErrorPopup() {
        Platform.runLater(() -> {
            try {
                isRunning = false;
                if (socket != null) socket.close();

                // 1. Find the current stage (window) and close it
                Stage currentStage = null;
                if (chatTabPane != null && chatTabPane.getScene() != null) {//prüft ob wir in chat scene sind
                    currentStage = (Stage) chatTabPane.getScene().getWindow();
                } else {
                    // Fallback: Find any open window to close
                    List<Window> openWindows = Window.getWindows();
                    if (!openWindows.isEmpty()) currentStage = (Stage) openWindows.get(0);//erste offene seite wird geschlossen
                }

                if (currentStage != null) currentStage.close();

                // 2. Open the Error View as a fresh, independent window
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/at/ac/hcw/chat/client/error-view.fxml"));
                Parent root = loader.load();
                Stage errorStage = new Stage();
                errorStage.setScene(new Scene(root));
                errorStage.setTitle("Connection Error");
                errorStage.setResizable(false);
                errorStage.show();

            } catch (IOException e) {
                System.err.println("Failed to display error popup: " + e.getMessage());
            }
        });
    }

    @FXML protected void onDisconnectButtonClick(ActionEvent event) {
        isRunning = false;//stoppt der listener-Thread
        try {
            if (socket != null) socket.close();
            privateChatLog.clear(); tabMap.clear();
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            switchSceneOnStage(stage, "/at/ac/hcw/chat/client/login-view.fxml");
        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * Utility to swap the FXML content within the current window Stage.
     */
    private void switchSceneOnStage(Stage s, String p) throws IOException {
        FXMLLoader l = new FXMLLoader(getClass().getResource(p));
        s.setScene(new Scene(l.load()));
        ChatController next = l.getController();
        activeController = next; // Register the new scene's controller
        if (p.contains("chat-view")) {//if the next page would be chat-page
            next.welcomeLabel.setText("User: " + userName);
            next.userAvatarImage.setImage(new Image(getClass().getResourceAsStream(currentAvatarPath)));
        }
    }

    /**
     * Helper to find the current active window and switch its scene.
     */
    private void switchScene(String p) {
        Platform.runLater(() -> {
            try {
                Stage current;
                if (ipField != null) current = (Stage) ipField.getScene().getWindow();
                else if (selectedAvatarPreview != null) current = (Stage) selectedAvatarPreview.getScene().getWindow();
                else current = (Stage) Window.getWindows().get(0);//holt das erste offene java fenster
                switchSceneOnStage(current, p);//p ist das pfad von neue fxml
            } catch (Exception e) { e.printStackTrace(); }
        });
    }
}