package at.ac.hcw.chat.client;

import javafx.application.Application; // The base class from which all JavaFX applications extend
import javafx.fxml.FXMLLoader; // Class responsible for loading FXML layout files and linking them to Controllers
import javafx.scene.Scene; // The container for all content in a JavaFX window (the "stage")
import javafx.stage.Stage; // Represents the actual window provided by the operating system

/**
 * The main entry point for the Client-side JavaFX application.
 * By extending the 'Application' class, this class inherits the JavaFX lifecycle
 * (Init -> Start -> Stop).
 */
public class ChatApplication extends Application {

    /**
     * The start method is the main entry point for all JavaFX applications.
     * It is called automatically by the JavaFX Runtime after the system is ready.
     *
     * @param stage The primary "window" for this application, provided by the platform.
     */
    @Override
    public void start(Stage stage) throws Exception {
        /*
         * FXMLLoader: This object looks for the .fxml file in the project's resources.
         * getResource(): Uses an absolute path (starting with '/') to locate 'login-view.fxml'.
         * The path must match the folder structure inside src/main/resources.
         */
        FXMLLoader fxmlLoader = new FXMLLoader(ChatApplication.class.getResource("/at/ac/hcw/chat/client/login-view.fxml"));

        /*
         * Scene: We create a new Scene by loading the FXML hierarchy.
         * The fxmlLoader.load() method parses the XML and instantiates the UI components
         * and their associated Controller (ChatController).
         */
        Scene scene = new Scene(fxmlLoader.load());

        // stage.setTitle: Sets the text displayed in the window's title bar
        stage.setTitle("HCW Chat - Login");

        /*
         * stage.setResizable(false): Disables the user's ability to resize the window.
         * This is useful for Login screens to maintain the intended aesthetic layout.
         */
        stage.setResizable(false);

        /*
         * stage.setScene: Places the prepared scene (content) onto the stage (window).
         */
        stage.setScene(scene);

        /*
         * stage.show(): Makes the window visible to the user.
         * Without this call, the application would run in the background without a GUI.
         */
        stage.show();
    }

    /**
     * The standard Java main method.
     * In a JavaFX application, this is used primarily to launch the GUI thread.
     */
    public static void main(String[] args) {
        /*
         * System.setProperty: A critical networking configuration.
         * By setting "java.net.preferIPv4Stack" to "true", we tell the Java Virtual Machine
         * to use IPv4 addresses (like 192.168.x.x) instead of IPv6.
         * This is essential for local network socket communication, especially on macOS
         * and Linux, to ensure the Client can successfully discover the Server.
         */
        System.setProperty("java.net.preferIPv4Stack", "true");

        /*
         * launch(): A static method from the Application class.
         * It initializes the JavaFX toolkit, starts the JavaFX Application Thread,
         * and eventually calls the start() method defined above.
         */
        launch();
    }
}