package orgs.utils;
// Recommend placing server code in a 'server' package

import orgs.models2.*; // Import all your model classes
import orgs.utils.DatabaseConnection; // Your MySQL database connection singleton

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MessagingServer {
    private static final int PORT = 12345; // Port for clients to connect to
    private static final int THREAD_POOL_SIZE = 10; // Max concurrent client handlers
    private static ExecutorService clientThreadPool;
    private static Map<Long, PrintWriter> onlineUsers = new ConcurrentHashMap<>(); // Maps User ID to their PrintWriter

    public static void main(String[] args) {
        System.out.println("Starting Messaging Server...");

        // 1. Initialize Database Connection
        try {
            // This will ensure the connection is attempted/initialized when the server starts
            DatabaseConnection.getConnection();
            System.out.println("Database connection initialized successfully.");
        } catch (SQLException e) {
            System.err.println("Failed to initialize database connection: " + e.getMessage());
            System.err.println("Server cannot start without a database connection. Exiting.");
            return;
        }

        clientThreadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        // 2. Start Server Socket
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server listening on port " + PORT + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept(); // Accept incoming client connection
                System.out.println("New client connected from: " + clientSocket.getInetAddress().getHostAddress());
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clientThreadPool.submit(clientHandler); // Submit handler to thread pool
            }
        } catch (IOException e) {
            System.err.println("Server exception: " + e.getMessage());
        } finally {
            clientThreadPool.shutdown(); // Shutdown thread pool when server stops
            System.out.println("Server stopped.");
        }
    }

    /**
     * ClientHandler is a Runnable that processes commands from a single client.
     */
    private static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private BufferedReader in;
        private PrintWriter out;
        private Long currentUserId = null; // Stores the ID of the logged-in user for this session

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                out = new PrintWriter(clientSocket.getOutputStream(), true); // Auto-flush enabled

                String clientMessage;
                while ((clientMessage = in.readLine()) != null) {
                    System.out.println("[Client " + (currentUserId != null ? currentUserId : "Guest") + " from " + clientSocket.getInetAddress().getHostAddress() + "]: " + clientMessage);
                    processClientMessage(clientMessage);
                }
            } catch (IOException e) {
                System.out.println("Client disconnected (" + (currentUserId != null ? currentUserId : "Guest") + "): " + e.getMessage());
            } finally {
                // Ensure user is marked offline and resources are closed on disconnect
                if (currentUserId != null) {
                    try {
                        User user = User.findById(currentUserId);
                        if (user != null) {
                            user.setOnline(false);
                            user.setLastSeenAt(Timestamp.valueOf(LocalDateTime.now()));
                            user.update(); // Update is_online and last_seen_at in DB
                            System.out.println("User " + currentUserId + " logged out due to disconnect.");
                        }
                    } catch (SQLException e) {
                        System.err.println("Error updating user status on disconnect for " + currentUserId + ": " + e.getMessage());
                    } finally {
                        onlineUsers.remove(currentUserId); // Remove from online users map
                    }
                }
                closeResources();
            }
        }

        private void processClientMessage(String message) {
            String[] parts = message.split(" ", 2); // Split into command and arguments
            String command = parts[0].toUpperCase();
            String args = parts.length > 1 ? parts[1] : "";

            try {
                switch (command) {
                    case "LOGIN":
                        handleLogin(args);
                        break;
                    case "REGISTER":
                        handleRegister(args);
                        break;
                    case "LOGOUT":
                        handleLogout();
                        break;
                    case "UPDATE_USER_INFO":
                        handleUpdateUserInfo(args);
                        break;
                    case "MESSAGE":
                        handleMessage(args);
                        break;
                    case "CALL":
                        handleCall(args);
                        break;
                    default:
                        out.println("ERROR: UNKNOWN_COMMAND");
                        break;
                }
            } catch (SQLException e) {
                out.println("ERROR: DATABASE_ERROR: " + e.getMessage());
                System.err.println("Database error for command " + command + ": " + e.getMessage());
                e.printStackTrace();
            } catch (NumberFormatException e) {
                out.println("ERROR: INVALID_ARGUMENT_FORMAT: " + e.getMessage());
                System.err.println("Invalid number format for command " + command + ": " + e.getMessage());
            } catch (IllegalArgumentException e) {
                out.println("ERROR: INVALID_ARGUMENTS: " + e.getMessage());
                System.err.println("Invalid arguments for command " + command + ": " + e.getMessage());
            } catch (Exception e) {
                out.println("ERROR: SERVER_ERROR: " + e.getMessage());
                System.err.println("Unhandled exception for command " + command + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        private void handleLogin(String args) throws SQLException {
            String[] loginArgs = args.split(" ");
            if (loginArgs.length != 2) {
                out.println("ERROR: LOGIN_USAGE: LOGIN <phone_number> <password>");
                return;
            }
            String phoneNumber = loginArgs[0];
            String password = loginArgs[1]; // In production, never send/store plain passwords
            //System.out.println("My password  : "  + password);
            User user = User.findByPhoneNumber(phoneNumber);
            System.out.println("User password  is : " + user.getPassword());
            if (user != null /* && user.getPasswordHash().equals(password)*/) { // Simplified password check
                this.currentUserId = user.getUserId();
                user.setOnline(true);
                user.setLastSeenAt(Timestamp.valueOf(LocalDateTime.now()));
                user.update(); // Update user status in DB

                onlineUsers.put(currentUserId, out); // Add to online users map
                out.println("LOGIN_SUCCESS:" + currentUserId);
                System.out.println("User " + currentUserId + " logged in.");
            } else {
                out.println("LOGIN_FAILED: INVALID_CREDENTIALS");
            }
        }

        private void handleRegister(String args) throws SQLException {
            String[] regArgs = args.split(" ", 5); // phone, pass, username, fname, lname
            if (regArgs.length < 2) {
                out.println("ERROR: REGISTER_USAGE: REGISTER <phone> <pass> [username] [fname] [lname]");
                return;
            }
            String phoneNumber = regArgs[0];
            String password = regArgs[1]; // Simplified
            String username = regArgs.length > 2 && !"null".equalsIgnoreCase(regArgs[2]) ? regArgs[2] : null;
            String firstName = regArgs.length > 3 && !"null".equalsIgnoreCase(regArgs[3]) ? regArgs[3] : null;
            String lastName = regArgs.length > 4 && !"null".equalsIgnoreCase(regArgs[4]) ? regArgs[4] : null;
            System.out.println("Password  : "  + password);
            if (User.findByPhoneNumber(phoneNumber) != null) {
                out.println("ERROR: REGISTER_FAILED: PHONE_NUMBER_EXISTS");
                return;
            }
            if (username != null && User.findByUsername(username) != null) {
                out.println("ERROR: REGISTER_FAILED: USERNAME_EXISTS");
                return;
            }

            /*
            Long userId, String phoneNumber,String password, String username, String firstName, String lastName, String bio,
                String profilePictureUrl,
            Timestamp lastSeenAt, boolean isOnline, Timestamp createdAt, Timestamp updatedAt)
            */


            // Using the constructor that aligns with your User model for creation
            User newUser = new User(null, phoneNumber, password, username, firstName, lastName,null,
                    null,new Timestamp(System.currentTimeMillis()), false, null, null);
            System.out.println("New User in password : "  + newUser.getPassword() );
            if (newUser.save()) {
                // After saving, the newUser object will have its ID set
                out.println("REGISTER_SUCCESS:" + newUser.getUserId());
                System.out.println("New user registered: " + newUser.getUserId());
            } else {
                out.println("ERROR: REGISTER_FAILED: UNKNOWN_ERROR");
            }
        }

        private void handleLogout() throws SQLException {
            if (currentUserId == null) {
                out.println("ERROR: NOT_LOGGED_IN");
                return;
            }

            User user = User.findById(currentUserId);
            if (user != null) {
                user.setOnline(false);
                user.setLastSeenAt(Timestamp.valueOf(LocalDateTime.now()));
                user.update(); // Update user status in DB
            }
            onlineUsers.remove(currentUserId); // Remove from online users map
            System.out.println("User " + currentUserId + " logged out.");
            currentUserId = null; // Clear the session's user ID
            out.println("LOGOUT_SUCCESS");
        }

        private void handleUpdateUserInfo(String args) throws SQLException {
            if (currentUserId == null) {
                out.println("ERROR: NOT_LOGGED_IN");
                return;
            }

            String[] updateArgs = args.split(" ", 2);
            if (updateArgs.length != 2) {
                out.println("ERROR: UPDATE_USAGE: UPDATE_USER_INFO <field> <value>");
                return;
            }
            String field = updateArgs[0];
            String value = updateArgs[1];
           // System.out.println("Update Password :" + value);
            User user = User.findById(currentUserId);
            if (user == null) {
                out.println("ERROR: USER_NOT_FOUND");
                return;
            }

            boolean updated = false;
            switch (field.toLowerCase()) {
                case "username":
                    // Check for username uniqueness if updating
                    if(User.findByUsername(value) != null && !User.findByUsername(value).getUserId().equals(currentUserId)) {
                        out.println("ERROR: USERNAME_ALREADY_TAKEN");
                        return;
                    }
                    user.setUsername(value);
                    updated = true;
                    break;
                case "first_name":
                    user.setFirstName(value);
                    updated = true;
                    break;
                case "password":
                    user.setPassword(value);
                    updated = true;
                case "last_name":
                    user.setLastName(value);
                    updated = true;
                    break;
                case "bio":
                    user.setBio(value);
                    updated = true;
                    break;
                case "profile_picture_url":
                    user.setProfilePictureUrl(value);
                    updated = true;
                    break;
                // is_online and last_seen_at are typically managed by the server based on connection/activity
                // but for demonstration, allowing client to set (not recommended in prod)
                case "is_online":
                    user.setOnline(Boolean.parseBoolean(value));
                    updated = true;
                    break;
                case "last_seen_at":
                    // Parse as ISO 8601 string or specific format if needed
                    // For simplicity, directly using Timestamp.valueOf
                    user.setLastSeenAt(Timestamp.valueOf(value));
                    updated = true;
                    break;
                default:
                    out.println("ERROR: INVALID_FIELD: " + field);
                    return;
            }

            if (updated && user.update()) {
                out.println("UPDATE_SUCCESS");
            } else if (updated) {
                out.println("ERROR: UPDATE_FAILED: DB_OPERATION_FAILED");
            } else {
                out.println("ERROR: UPDATE_FAILED: NO_CHANGES_MADE");
            }
        }

        private void handleMessage(String args) throws SQLException {
            if (currentUserId == null) {
                out.println("ERROR: NOT_LOGGED_IN");
                return;
            }

            // MESSAGE <sender_id> <chat_id> <message_type> <content> [OPT:MEDIA_ID:<id>] ...
            // The client sends sender_id, but server uses currentUserId to validate
            String[] msgParts = args.split(" ", 4); // Split for initial parts and optional args
            if (msgParts.length < 3) {
                out.println("ERROR: MESSAGE_USAGE: MESSAGE <chat_id> <type> <content> [OPTIONAL_ARGS]");
                return;
            }

            Long senderId = Long.parseLong(msgParts[0]); // Client sends its ID as sender
            Long chatId = Long.parseLong(msgParts[1]);
            String messageType = msgParts[2];
            String content = msgParts[3]; // This might contain spaces if not quoted properly by client

            // Validate senderId matches currentUserId
            if (!senderId.equals(currentUserId)) {
                out.println("ERROR: MESSAGE_DENIED: INVALID_SENDER_ID");
                return;
            }

            // Check if chat exists and user is a participant
            Chat chat = Chat.findById(chatId);
            if (chat == null) {
                out.println("ERROR: MESSAGE_FAILED: CHAT_NOT_FOUND");
                return;
            }
            ChatParticipant participant = ChatParticipant.findByChatIdAndUserId(chatId, currentUserId);
            if (participant == null) {
                out.println("ERROR: MESSAGE_FAILED: NOT_A_PARTICIPANT");
                return;
            }

            Long mediaId = null;
            Long repliedToMessageId = null;
            Long forwardedFromUserId = null;
            Long forwardedFromChatId = null;

            // Parse optional arguments
            if (msgParts.length > 3) {
                String optionalArgsString = msgParts[3]; // The rest of the line starting from content
                // Re-splitting to safely extract content and optional arguments
                int contentEndIndex = optionalArgsString.indexOf(" OPT:");
                if (contentEndIndex != -1) {
                    content = optionalArgsString.substring(0, contentEndIndex).trim();
                    String opts = optionalArgsString.substring(contentEndIndex).trim();

                    for (String opt : opts.split(" OPT:")) {
                        if (opt.startsWith("MEDIA_ID:")) {
                            mediaId = Long.parseLong(opt.substring("MEDIA_ID:".length()));
                        } else if (opt.startsWith("REPLIED_TO:")) {
                            repliedToMessageId = Long.parseLong(opt.substring("REPLIED_TO:".length()));
                        } else if (opt.startsWith("FORWARD_FROM_USER:")) {
                            forwardedFromUserId = Long.parseLong(opt.substring("FORWARD_FROM_USER:".length()));
                        } else if (opt.startsWith("FORWARD_FROM_CHAT:")) {
                            forwardedFromChatId = Long.parseLong(opt.substring("FORWARD_FROM_CHAT:".length()));
                        }
                    }
                }
            }


            Message newMessage = new Message(
                    chatId, currentUserId, messageType, content, mediaId,
                    repliedToMessageId, forwardedFromUserId, forwardedFromChatId,
                    null, false, 0); // editedAt=null, isDeleted=false, viewCount=0 for new message

            if (newMessage.save()) {
                out.println("MESSAGE_SENT:" + newMessage.getMessageId());
                System.out.println("Message " + newMessage.getMessageId() + " saved to chat " + chatId);

                // --- Broadcast message to other online participants in the chat ---
                List<ChatParticipant> participants = ChatParticipant.findByChatId(chatId);
                for (ChatParticipant p : participants) {
                    if (p.getUserId().equals(currentUserId)) {
                        continue; // Don't send back to sender
                    }
                    PrintWriter recipientWriter = onlineUsers.get(p.getUserId());
                    if (recipientWriter != null) {
                        // Format message for recipient to receive
                        // e.g., INCOMING_MESSAGE <message_id> <chat_id> <sender_id> <type> <content> ...
                        String incomingMsg = String.format("INCOMING_MESSAGE %d %d %d %s %s",
                                newMessage.getMessageId(), chatId, currentUserId, messageType, content);
                        // Add optional fields to incoming message
                        if (mediaId != null) incomingMsg += " OPT:MEDIA_ID:" + mediaId;
                        if (repliedToMessageId != null) incomingMsg += " OPT:REPLIED_TO:" + repliedToMessageId;
                        if (forwardedFromUserId != null) incomingMsg += " OPT:FORWARD_FROM_USER:" + forwardedFromUserId;
                        if (forwardedFromChatId != null) incomingMsg += " OPT:FORWARD_FROM_CHAT:" + forwardedFromChatId;

                        recipientWriter.println(incomingMsg);
                        System.out.println("Forwarded message " + newMessage.getMessageId() + " to user " + p.getUserId());
                    } else {
                        // Handle offline users (e.g., store for push notifications, update unread count)
                        System.out.println("User " + p.getUserId() + " is offline. Message " + newMessage.getMessageId() + " for them.");
                        // Increment unread count for offline users
                        p.setUnreadCount(p.getUnreadCount() + 1);
                        p.update(); // Update unread count in DB
                        // TODO: Implement push notification logic here
                    }
                }
                // Update sender's last read message (optional, but good practice)
                participant.setLastReadMessageId(newMessage.getMessageId());
                participant.setUnreadCount(0); // Sender has read their own message
                participant.update();

            } else {
                out.println("ERROR: MESSAGE_FAILED: DB_SAVE_FAILED");
            }
        }

        private void handleCall(String args) {
            if (currentUserId == null) {
                out.println("ERROR: NOT_LOGGED_IN");
                return;
            }

            String[] callArgs = args.split(" ");
            if (callArgs.length != 2) {
                out.println("ERROR: CALL_USAGE: CALL <callee_id> <action_type>");
                return;
            }
            Long calleeId = Long.parseLong(callArgs[0]);
            String actionType = callArgs[1].toUpperCase();

            PrintWriter calleeWriter = onlineUsers.get(calleeId);
            if (calleeWriter != null) {
                // Forward the call action to the callee
                calleeWriter.println(String.format("INCOMING_CALL:%s:%s:%s", currentUserId, calleeId, actionType));
                out.println("CALL_ACTION_SENT:" + actionType);
                System.out.println("Call action '" + actionType + "' from " + currentUserId + " to " + calleeId);
            } else {
                out.println("ERROR: CALL_FAILED: CALLEE_OFFLINE");
                System.out.println("Call action '" + actionType + "' from " + currentUserId + " to " + calleeId + " failed: Callee offline.");
            }
        }

        private void closeResources() {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing client handler resources: " + e.getMessage());
            }
        }
    }
}