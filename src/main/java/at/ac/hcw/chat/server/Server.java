package at.ac.hcw.chat.server;

import java.io.*; // Classes for handling data streams (Reader/Writer)
import java.net.*; // Classes for Socket networking and IP handling
import java.util.*; // Utility classes like Map, List, and Scanner
import java.util.concurrent.ConcurrentHashMap; // High-performance thread-safe Map

/**
 * Advanced Multi-Threaded Chat Server with Avatar Support.
 *
 * New Protocol:
 * 1. Initial Connection: Expects "Name|AvatarPath"
 * 2. Public Broadcast: Sends "SenderName|SenderAvatar|Message"
 * 3. Private Message: Sends "[Private from Name]|SenderAvatar|Message"
 */
public class Server {
    /**
     * clientMap: Stores active users.
     * Key: Username (String) | Value: Their specific output stream (PrintWriter)
     */
    private static final Map<String, PrintWriter> clientMap = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        // Force the JVM to prioritize IPv4 over IPv6 for local network discovery
        System.setProperty("java.net.preferIPv4Stack", "true");

        // Scanner reads from Standard Input (Keyboard) for admin configuration
        Scanner scanner = new Scanner(System.in);

        System.out.println("--- HCW Chat Server with Avatar Support ---");

        // Dynamic Port Input
        System.out.print("Please enter the Port number (e.g., 888): ");
        int port;
        try {
            port = Integer.parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            System.err.println("Invalid port! Defaulting to 888.");
            port = 888;
        }

        // IP Discovery to help users know where to connect
        String primaryIP = getPrimaryIP();
        if (primaryIP == null) {
            System.err.println("ERROR: No active network connection found!");
            return;
        }

        System.out.println("\n----------------------------");
        System.out.println("SERVER STATUS: ONLINE");
        System.out.println("IP ADDRESS   : " + primaryIP);
        System.out.println("LISTENING ON : Port " + port);
        System.out.println("----------------------------\n");

        /*
         * ServerSocket: Listens on the port and waits for client connection requests.
         * Once a client connects, it returns a 'Socket' for that specific user.
         */
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                // Blocking call: execution pauses here until a client joins
                Socket clientSocket = serverSocket.accept();
                System.out.println("[CONNECT]: Connection established with " + clientSocket.getInetAddress());

                // Hand off the new connection to a dedicated Thread (ClientHandler)
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.err.println("Critical Socket Error: " + e.getMessage());
        }
    }

    /**
     * Scans physical network adapters to find the primary IPv4 address.
     */
    private static String getPrimaryIP() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                // Filter out loopback (127.0.0.1) and inactive/virtual interfaces
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        // Typical private network ranges
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) return ip;
                    }
                }
            }
        } catch (SocketException e) { e.printStackTrace(); }
        return null;
    }

    /**
     * Handles one specific client in a background thread.
     */
    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String clientName;
        private String avatarUrl; // NEW: Stores the chosen penguin path for this session

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                // Initialize character-based communication streams
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                /*
                 * STEP 1: LOGIN PROTOCOL
                 * Expecting the client to send a formatted string: "Username|/path/to/avatar.jpeg"
                 */
                String loginLine = in.readLine();

                if (loginLine != null && loginLine.contains("|")) {
                    // \\| is needed because the Pipe character is a regex reserved symbol
                    String[] parts = loginLine.split("\\|");
                    this.clientName = parts[0];
                    this.avatarUrl = parts[1]; // Store the avatar path for broadcasting

                    clientMap.put(this.clientName, out);
                    System.out.println("[LOG]: " + clientName + " joined using avatar: " + avatarUrl);

                    broadcastUserList(); // Sync sidebar for all users

                    // Announce the new user using the bubble format: Name|Avatar|Message
                    broadcast("[System]|/at/ac/hcw/chat/client/images/profile0.jpeg|" + clientName + " joined the room.");
                }

                String message;
                /*
                 * STEP 2: MAIN MESSAGE LOOP
                 * Wait for messages. Decipher if they are Private or Public.
                 */
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("@") && message.contains(":")) {
                        handlePrivateMessage(message);
                    } else {
                        // Public Broadcast format: "Name|Avatar|Text"
                        System.out.println("[MSG]: " + clientName + ": " + message);
                        broadcast(this.clientName + "|" + this.avatarUrl + "|" + message);
                    }
                }
            } catch (IOException e) {
                // Connection lost due to client crash or network failure
            } finally {
                // CLEANUP: Free resources and notify others
                if (clientName != null) {
                    clientMap.remove(clientName);
                    broadcastUserList();
                    broadcast("[System]|/at/ac/hcw/chat/client/images/profile0.jpeg|" + clientName + " left.");
                    System.out.println("[LOG]: " + clientName + " disconnected.");
                }
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        /**
         * Logic for routing private messages to a specific user.
         */
        private void handlePrivateMessage(String rawMessage) {
            try {
                int colonIndex = rawMessage.indexOf(":");
                String targetName = rawMessage.substring(1, colonIndex).trim(); // Extract target name
                String content = rawMessage.substring(colonIndex + 1).trim(); // Extract text

                PrintWriter targetWriter = clientMap.get(targetName);
                if (targetWriter != null) {
                    // Send to Receiver: "[Private from Name]|Avatar|Message"
                    targetWriter.println("[Private from " + clientName + "]|" + this.avatarUrl + "|" + content);
                    // Send confirmation to Sender: "[Private to Name]|Avatar|Message"
                    out.println("[Private to " + targetName + "]|" + this.avatarUrl + "|" + content);
                } else {
                    // Target not found: "[Private Error Name]|SystemAvatar|ErrorText"
                    out.println("[Private Error " + targetName + "]|/at/ac/hcw/chat/client/images/profile0.jpeg|User offline.");
                }
            } catch (Exception e) {
                out.println("[System]|/at/ac/hcw/chat/client/images/profile0.jpeg|Bad message format.");
            }
        }

        /**
         * Iterates through the map and sends the packet to every active stream.
         */
        private void broadcast(String formattedPacket) {
            for (PrintWriter writer : clientMap.values()) {
                writer.println(formattedPacket);
            }
        }

        /**
         * Sends the "USERLIST:User1,User2,..." string for sidebar synchronization.
         */
        private void broadcastUserList() {
            StringBuilder sb = new StringBuilder("USERLIST:");
            for (String name : clientMap.keySet()) {
                sb.append(name).append(",");
            }
            String listMessage = sb.toString();
            for (PrintWriter writer : clientMap.values()) {
                writer.println(listMessage);
            }
        }
    }
}