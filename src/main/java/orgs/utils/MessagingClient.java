package orgs.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class MessagingClient {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String currentUserId; // To store the ID of the logged-in user

    public MessagingClient(String serverAddress, int serverPort) throws IOException {
        this.socket = new Socket(serverAddress, serverPort);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true); // true for auto-flush
        System.out.println("Connected to server at " + serverAddress + ":" + serverPort);

        // Start a separate thread to listen for server responses
        new Thread(this::listenForServerResponses).start();
    }

    // --- Core Communication Methods ---

    private void sendMessageToServer(String message) {
        if (out != null) {
            out.println(message);
            System.out.println("Sent: " + message);
        }
    }

    private void listenForServerResponses() {
        try {
            String serverResponse;
            while ((serverResponse = in.readLine()) != null) {
                System.out.println("Received: " + serverResponse);
                // Basic response parsing for login success
                if (serverResponse.startsWith("LOGIN_SUCCESS:")) {
                    this.currentUserId = serverResponse.split(":")[1].trim();
                    System.out.println("Logged in as User ID: " + this.currentUserId);
                } else if (serverResponse.startsWith("LOGOUT_SUCCESS")) {
                    this.currentUserId = null;
                    System.out.println("Logged out successfully.");
                } else if (serverResponse.startsWith("REGISTER_SUCCESS:")) {
                    // Assuming server sends back the new user ID upon successful registration
                    String newUserId = serverResponse.split(":")[1].trim();
                    System.out.println("Registered with User ID: " + newUserId);
                }
            }
        } catch (IOException e) {
            System.err.println("Server connection lost: " + e.getMessage());
        } finally {
            close();
        }
    }

    public void close() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
            System.out.println("Client connection closed.");
        } catch (IOException e) {
            System.err.println("Error closing client resources: " + e.getMessage());
        }
    }

    // --- Specific Message Sending Methods ---

    /**
     * Sends a login request to the server.
     * @param phoneNumber The user's phone number.
     * @param password The user's password.
     */
    public void  login(String phoneNumber, String password) {
        sendMessageToServer(String.format("LOGIN %s %s", phoneNumber, password));
    }

    /**
     * Sends a logout request to the server.
     * Requires the user to be logged in (currentUserId must be set).
     */
    public void logout() {
        if (currentUserId == null) {
            System.out.println("Not logged in to logout.");
            return;
        }
        sendMessageToServer(String.format("LOGOUT %s", currentUserId));
    }

    /**
     * Sends a registration (sign-in) request to the server.
     * @param phoneNumber The new user's phone number.
     * @param password The new user's password.
     * @param username Optional: The new user's username. Use "null" if not provided.
     * @param firstName Optional: The new user's first name. Use "null" if not provided.
     * @param lastName Optional: The new user's last name. Use "null" if not provided.
     */
    public void register(String phoneNumber, String password, String username, String firstName, String lastName) {
        // Replace nulls with "null" string for protocol consistency if needed
        username = username != null ? username : "null";
        firstName = firstName != null ? firstName : "null";
        lastName = lastName != null ? lastName : "null";
        sendMessageToServer(String.format("REGISTER %s %s %s %s %s", phoneNumber, password, username, firstName, lastName));
    }

    /**
     * Sends an update user information request to the server.
     * Requires the user to be logged in (currentUserId must be set).
     * @param fieldName The name of the field to update (e.g., "username", "first_name", "bio").
     * @param newValue The new value for the field.
     */
    public void updateUserInformation(String fieldName, String newValue) {
        if (currentUserId == null) {
            System.out.println("Login first to update info.");
            return;
        }
        sendMessageToServer(String.format("UPDATE_USER_INFO %s %s %s", currentUserId, fieldName, newValue));
    }

    /**
     * Sends a chat message to the server.
     * Requires the user to be logged in (currentUserId must be set).
     * @param chatId The ID of the chat.
     * @param messageType The type of message (e.g., "text", "image", "video").
     * @param content The message content or URL/path for media.
     * @param mediaId Optional: ID of the media file. Use null if not applicable.
     * @param repliedToMessageId Optional: ID of the message this message is replying to. Use null if not applicable.
     * @param forwardedFromUserId Optional: ID of the user from whom this message was forwarded. Use null if not applicable.
     * @param forwardedFromChatId Optional: ID of the chat from which this message was forwarded. Use null if not applicable.
     */
    public void sendMessage(Long chatId, String messageType, String content,
                            Long mediaId, Long repliedToMessageId,
                            Long forwardedFromUserId, Long forwardedFromChatId) {
        if (currentUserId == null) {
            System.out.println("Login first to send messages.");
            return;
        }

        StringBuilder messageBuilder = new StringBuilder(String.format("MESSAGE %s %d %s %s",
                currentUserId, chatId, messageType, content));

        if (mediaId != null) {
            messageBuilder.append(" OPT:MEDIA_ID:").append(mediaId);
        }
        if (repliedToMessageId != null) {
            messageBuilder.append(" OPT:REPLIED_TO:").append(repliedToMessageId);
        }
        if (forwardedFromUserId != null) {
            messageBuilder.append(" OPT:FORWARD_FROM_USER:").append(forwardedFromUserId);
        }
        if (forwardedFromChatId != null) {
            messageBuilder.append(" OPT:FORWARD_FROM_CHAT:").append(forwardedFromChatId);
        }

        sendMessageToServer(messageBuilder.toString());
    }

    /**
     * Sends a live call action to the server.
     * Requires the user to be logged in (currentUserId must be set).
     * @param calleeId The ID of the user being called/involved in the call.
     * @param actionType The type of call action (e.g., "REQUEST", "ACCEPT", "REJECT", "END").
     */
    public void sendLiveCallAction(Long calleeId, String actionType) {
        if (currentUserId == null) {
            System.out.println("Login first to initiate/manage calls.");
            return;
        }
        sendMessageToServer(String.format("CALL %s %d %s", currentUserId, calleeId, actionType));
    }

    // --- Main method for testing ---
    public static void main(String[] args) {
        String serverAddress = "localhost"; // Change to your server's IP if it's remote
        int serverPort = 12345; // Ensure this matches your server's port

        try (Scanner scanner = new Scanner(System.in)) {
            MessagingClient client = new MessagingClient(serverAddress, serverPort);

            System.out.println("\n--- Client Commands ---");
            System.out.println("1. login <phone> <pass>");
            System.out.println("2. register <phone> <pass> [username] [fname] [lname]");
            System.out.println("3. update_info <field> <value>");
            System.out.println("4. message <chat_id> <type> <content> [OPT:media_id:X] [OPT:replied_to:X] [OPT:forward_user:X] [OPT:forward_chat:X]");
            System.out.println("5. call <callee_id> <action_type>");
            System.out.println("6. logout");
            System.out.println("7. exit");
            System.out.println("-----------------------\n");

            String line;
            while (true) {
                System.out.print("> ");
                line = scanner.nextLine();
                String[] parts = line.split(" ", 2); // Split only on first space to get command and rest

                String command = parts[0].toLowerCase();
                String arg = parts.length > 1 ? parts[1] : "";

                try {
                    switch (command) {
                        case "login":
                            String[] loginArgs = arg.split(" ");
                            if (loginArgs.length == 2) {
                                client.login(loginArgs[0], loginArgs[1]);
                            } else {
                                System.out.println("Usage: login <phone> <pass>");
                            }
                            break;
                        case "register":
                            String[] regArgs = arg.split(" ", 5); // phone, pass, username, fname, lname
                            if (regArgs.length >= 2) {
                                String username = regArgs.length > 2 && !regArgs[2].equalsIgnoreCase("null") ? regArgs[2] : null;
                                String firstName = regArgs.length > 3 && !regArgs[3].equalsIgnoreCase("null") ? regArgs[3] : null;
                                String lastName = regArgs.length > 4 && !regArgs[4].equalsIgnoreCase("null") ? regArgs[4] : null;
                                client.register(regArgs[0], regArgs[1], username, firstName, lastName);
                            } else {
                                System.out.println("Usage: register <phone> <pass> [username] [fname] [lname]");
                            }
                            break;
                        case "update_info":
                            String[] updateArgs = arg.split(" ", 2); // field, value
                            if (updateArgs.length == 2) {
                                client.updateUserInformation(updateArgs[0], updateArgs[1]);
                            } else {
                                System.out.println("Usage: update_info <field> <value>");
                            }
                            break;
                        case "message":
                            // Example: message 100 text "Hello_World" OPT:REPLIED_TO:5
                            String[] msgArgs = arg.split(" ", 4); // chat_id, type, content, (optional_args)
                            if (msgArgs.length >= 3) {
                                Long chatId = Long.parseLong(msgArgs[0]);
                                String msgType = msgArgs[1];
                                String msgContent = msgArgs[2];
                                Long mediaId = null;
                                Long repliedTo = null;
                                Long forwardedUser = null;
                                Long forwardedChat = null;

                                if (msgArgs.length > 3) {
                                    String optionalArgs = msgArgs[3];
                                    for (String opt : optionalArgs.split(" ")) {
                                        if (opt.startsWith("OPT:MEDIA_ID:")) {
                                            mediaId = Long.parseLong(opt.substring("OPT:MEDIA_ID:".length()));
                                        } else if (opt.startsWith("OPT:REPLIED_TO:")) {
                                            repliedTo = Long.parseLong(opt.substring("OPT:REPLIED_TO:".length()));
                                        } else if (opt.startsWith("OPT:FORWARD_FROM_USER:")) {
                                            forwardedUser = Long.parseLong(opt.substring("OPT:FORWARD_FROM_USER:".length()));
                                        } else if (opt.startsWith("OPT:FORWARD_FROM_CHAT:")) {
                                            forwardedChat = Long.parseLong(opt.substring("OPT:FORWARD_FROM_CHAT:".length()));
                                        }
                                    }
                                }
                                client.sendMessage(chatId, msgType, msgContent, mediaId, repliedTo, forwardedUser, forwardedChat);
                            } else {
                                System.out.println("Usage: message <chat_id> <type> <content> [OPT:media_id:X] [OPT:replied_to:X] [OPT:forward_user:X] [OPT:forward_chat:X]");
                            }
                            break;
                        case "call":
                            String[] callArgs = arg.split(" ");
                            if (callArgs.length == 2) {
                                Long calleeId = Long.parseLong(callArgs[0]);
                                String actionType = callArgs[1].toUpperCase();
                                client.sendLiveCallAction(calleeId, actionType);
                            } else {
                                System.out.println("Usage: call <callee_id> <action_type>");
                            }
                            break;
                        case "logout":
                            client.logout();
                            break;
                        case "exit":
                            client.close();
                            return; // Exit the loop and main method
                        default:
                            System.out.println("Unknown command.");
                            break;
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Invalid number format in command: " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("Error processing command: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Could not connect to server: " + e.getMessage());
        }
    }
}