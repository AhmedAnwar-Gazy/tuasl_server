// src/orgs/client/ChatClient.java
package orgs.client;

import orgs.model.Message;
import orgs.model.User;
import orgs.model.Chat;
import orgs.protocol.Command;
import orgs.protocol.Request;
import orgs.protocol.Response;
import orgs.utils.LocalDateTimeAdapter; // Make sure this is imported
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.Socket;
import java.time.LocalDateTime; // Required for LocalDateTimeAdapter
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit; // For timeout

public class ChatClient {
    private static final String SERVER_IP = "127.0.0.1"; // Localhost
    private static final int SERVER_PORT = 12345;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter()) // Register the adapter
            .serializeNulls()
            .create();

    private User currentUser; // Store logged-in user info

    // A queue to hold responses received by the listener thread, to be picked up by the main thread
    private final BlockingQueue<Response> responseQueue = new LinkedBlockingQueue<>();

    public ChatClient() {
        try {
            socket = new Socket(SERVER_IP, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            System.out.println("Connected to chat server.");

            // Start a separate thread for listening to incoming messages from the server
            new Thread(this::listenForServerMessages, "ServerListener").start();

        } catch (IOException e) {
            System.err.println("Error connecting to server: " + e.getMessage());
            System.exit(1); // Exit if connection fails
        }
    }

    private void listenForServerMessages() {
        try {
            String serverResponseJson;
            while ((serverResponseJson = in.readLine()) != null) {
                Response response = gson.fromJson(serverResponseJson, Response.class);

                // Check if this response is an unsolicited new message
                if (response.isSuccess() && "New message received".equals(response.getMessage())) {
                    Message newMessage = gson.fromJson(response.getData(), Message.class);
                    // Print unsolicited messages directly to the console
                    System.out.println("\n[NEW MESSAGE from User " + newMessage.getSenderId() + " in Chat ID " + newMessage.getChatId() + "]: " + newMessage.getContent());
                    System.out.print("> "); // Re-prompt the user
                } else {
                    // All other responses (like login, send message confirmation, get messages)
                    // are put into the queue for the main thread to pick up.
                    responseQueue.put(response); // This is blocking if queue is full (unlikely here)
                }
            }
        } catch (IOException e) {
            System.err.println("Server connection lost: " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("Listener thread interrupted: " + e.getMessage());
            Thread.currentThread().interrupt(); // Restore interrupted status
        } finally {
            closeConnection();
        }
    }

    // Partial src/orgs/client/ChatClient.java (Focus on new parts)

// ... (existing imports, class definition, socket setup) ...

    public void startClient() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Welcome to the Tuasil Messaging Client!");

        while (currentUser == null) {
            System.out.println("\n--- Auth Options ---");
            System.out.println("1. Login");
            System.out.println("2. Register");
            System.out.print("Choose an option: ");
            String authChoice = scanner.nextLine();

            if ("1".equals(authChoice)) {
                System.out.print("Enter username: ");
                String username = scanner.nextLine();
                System.out.print("Enter password: ");
                String password = scanner.nextLine();
                if (login(username, password)) {
                    System.out.println("Logged in as: " + currentUser.getUsername());
                    break;
                } else {
                    System.out.println("Login failed. Please try again.");
                }
            } else if ("2".equals(authChoice)) {
                registerUser(scanner);
            } else {
                System.out.println("Invalid option.");
            }
        }

        // Main command loop
        while (true) {
            System.out.println("\n--- Commands ---");
            System.out.println("1. Send Message");
            System.out.println("2. Get Chat Messages");
            System.out.println("3. Create Chat");
            System.out.println("4. My Profile (View/Update)");
            System.out.println("5. Get All Users");
            System.out.println("6. My Chats");
            System.out.println("7. Add/Remove Chat Participant");
            System.out.println("8. My Contacts");
            System.out.println("9. Block/Unblock User");
            System.out.println("10. My Notifications");
            System.out.println("11. Update/Delete Message");
            System.out.println("12. Delete Chat");
            System.out.println("13. Logout");
            System.out.print("Enter command number: ");

            String commandChoice = scanner.nextLine();
            switch (commandChoice) {
                case "1":
                    System.out.print("Enter Chat ID: ");
                    int chatId = getIntInput(scanner);
                    if (chatId == -1) break;
                    System.out.print("Enter message content: ");
                    String content = scanner.nextLine();
                    sendMessage(chatId, content);
                    break;
                case "2":
                    System.out.print("Enter Chat ID to get messages from: ");
                    int getChatId = getIntInput(scanner);
                    if (getChatId == -1) break;
                    getChatMessages(getChatId);
                    break;
                case "3":
                    System.out.print("Enter Chat Name (e.g., 'My New Group'): ");
                    String chatName = scanner.nextLine();
                    System.out.print("Enter Chat Type (private/group): "); // Add more specific logic for group
                    String chatType = scanner.nextLine();
                    createChat(chatName, chatType);
                    break;
                case "4":
                    manageProfile(scanner);
                    break;
                case "5":
                    getAllUsers();
                    break;
                case "6":
                    getUserChats();
                    break;
                case "7":
                    manageChatParticipants(scanner);
                    break;
                case "8":
                    manageContacts(scanner);
                    break;
                case "9":
                    blockUnblockUser(scanner);
                    break;
                case "10":
                    manageNotifications(scanner);
                    break;
                case "11":
                    updateDeleteMessage(scanner);
                    break;
                case "12":
                    System.out.print("Enter Chat ID to delete: ");
                    int delChatId = getIntInput(scanner);
                    if (delChatId == -1) break;
                    deleteChat(delChatId);
                    break;
                case "13":
                    System.out.println("Logging out...");
                    closeConnection();
                    return;
                default:
                    System.out.println("Invalid command. Please try again.");
            }
        }
    }

    // Helper for safe integer input
    private int getIntInput(Scanner scanner) {
        try {
            return Integer.parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Please enter a number.");
            return -1;
        }
    }

    // --- New Client Command Implementations ---

    private void registerUser(Scanner scanner) {
        System.out.print("Enter desired username: ");
        String username = scanner.nextLine();
        System.out.print("Enter password: ");
        String password = scanner.nextLine();
        System.out.print("Enter phone number (e.g., +1234567890): ");
        String phoneNumber = scanner.nextLine();
        System.out.print("Enter first name: ");
        String firstName = scanner.nextLine();
        System.out.print("Enter last name: ");
        String lastName = scanner.nextLine();
        System.out.print("Enter a short bio: ");
        String bio = scanner.nextLine();

        User newUser = new User();
        newUser.setUsername(username);
        newUser.setPassword(password); // IMPORTANT: Hash this on client or send securely
        newUser.setPhoneNumber(phoneNumber);
        newUser.setFirstName(firstName);
        newUser.setLastName(lastName);
        newUser.setBio(bio);
        newUser.setOnline(false); // Default for registration
        newUser.setLastSeenAt(null); // Default

        Request request = new Request(Command.REGISTER, gson.toJson(newUser));
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            System.out.println("Registration successful! You can now log in.");
            // If auto-login desired, call login() here
        } else if (response != null) {
            System.out.println("Registration failed: " + response.getMessage());
        }
    }

    private void manageProfile(Scanner scanner) {
        System.out.println("\n--- Your Profile ---");
        // Get user profile first
        getUserProfile(currentUser.getId()); // Get own profile

        System.out.println("\nProfile Options:");
        System.out.println("1. Update Profile");
        System.out.println("2. Delete Account (WARNING: IRREVERSIBLE)");
        System.out.println("3. Back to main menu");
        System.out.print("Choose an option: ");
        String choice = scanner.nextLine();

        switch (choice) {
            case "1":
                updateUserProfile(scanner);
                break;
            case "2":
                System.out.print("Are you sure you want to delete your account? Type 'YES' to confirm: ");
                String confirm = scanner.nextLine();
                if ("YES".equals(confirm)) {
                    deleteUser();
                    // If successful, client will be logged out and should exit
                } else {
                    System.out.println("Account deletion cancelled.");
                }
                break;
            case "3":
                break;
            default:
                System.out.println("Invalid option.");
        }
    }

    private void getUserProfile(int userId) {
        Map<String, String> params = new HashMap<>();
        params.put("userId", String.valueOf(userId)); // Request a specific user's profile

        Request request = new Request(Command.GET_USER_PROFILE, gson.toJson(params));
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            User profile = gson.fromJson(response.getData(), User.class);
            System.out.println("Username: " + profile.getUsername());
            System.out.println("Name: " + profile.getFirstName() + " " + profile.getLastName());
            System.out.println("Phone: " + profile.getPhoneNumber());
            System.out.println("Bio: " + profile.getBio());
            System.out.println("Online: " + profile.isOnline());
            if (profile.getLastSeenAt() != null) {
                System.out.println("Last Seen: " + profile.getLastSeenAt().format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, HH:mm")));
            }
        } else if (response != null) {
            System.out.println("Failed to get profile: " + response.getMessage());
        }
    }

    private void updateUserProfile(Scanner scanner) {
        if (currentUser == null) {
            System.out.println("Not logged in.");
            return;
        }

        System.out.println("Enter new values (leave blank to keep current):");
        System.out.print("First Name (" + currentUser.getFirstName() + "): ");
        String firstName = scanner.nextLine();
        if (!firstName.isEmpty()) currentUser.setFirstName(firstName);

        System.out.print("Last Name (" + currentUser.getLastName() + "): ");
        String lastName = scanner.nextLine();
        if (!lastName.isEmpty()) currentUser.setLastName(lastName);

        System.out.print("Bio (" + (currentUser.getBio() != null ? currentUser.getBio() : "None") + "): ");
        String bio = scanner.nextLine();
        if (!bio.isEmpty()) currentUser.setBio(bio);

        System.out.print("Profile Picture URL (" + (currentUser.getProfilePictureUrl() != null ? currentUser.getProfilePictureUrl() : "None") + "): ");
        String profilePicUrl = scanner.nextLine();
        if (!profilePicUrl.isEmpty()) currentUser.setProfilePictureUrl(profilePicUrl);

        // Send the updated currentUser object
        Request request = new Request(Command.UPDATE_USER_PROFILE, gson.toJson(currentUser));
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            System.out.println("Profile updated successfully!");
            currentUser = gson.fromJson(response.getData(), User.class); // Update local copy
        } else if (response != null) {
            System.out.println("Failed to update profile: " + response.getMessage());
        }
    }

    private void deleteUser() {
        if (currentUser == null) {
            System.out.println("Not logged in.");
            return;
        }
        Map<String, Integer> params = new HashMap<>();
        params.put("userId", currentUser.getId());

        Request request = new Request(Command.DELETE_USER, gson.toJson(params));
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            System.out.println("Account deleted successfully.");
            closeConnection();
            System.exit(0); // Exit the client
        } else if (response != null) {
            System.out.println("Failed to delete account: " + response.getMessage());
        }
    }

    private void getAllUsers() {
        Request request = new Request(Command.GET_ALL_USERS, null);
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            Type userListType = new TypeToken<List<User>>() {}.getType();
            List<User> users = gson.fromJson(response.getData(), userListType);
            System.out.println("\n--- All Registered Users ---");
            if (users.isEmpty()) {
                System.out.println("No users found.");
            } else {
                for (User user : users) {
                    System.out.println("ID: " + user.getId() + ", Username: " + user.getUsername() + ", Name: " + user.getFirstName() + " " + user.getLastName() + (user.isOnline() ? " (Online)" : ""));
                }
            }
        } else if (response != null) {
            System.out.println("Failed to get all users: " + response.getMessage());
        }
    }

    private void createChat(String chatName, String chatType) {
        if (currentUser == null) {
            System.out.println("You must be logged in to create chats.");
            return;
        }

        Chat newChat = new Chat();
        newChat.setChatName(chatName);
        newChat.setChatType(chatType); // "private" or "group"
        // Creator ID will be set on the server

        Request request = new Request(Command.CREATE_CHAT, gson.toJson(newChat));
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            Chat createdChat = gson.fromJson(response.getData(), Chat.class);
            System.out.println("Chat created successfully! Chat ID: " + createdChat.getId() + ", Type: " + createdChat.getChatType());
        } else if (response != null) {
            System.out.println("Failed to create chat: " + response.getMessage());
        }
    }

    private void getUserChats() {
        Request request = new Request(Command.GET_USER_CHATS, null);
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            Type chatListType = new TypeToken<List<Chat>>() {}.getType();
            List<Chat> chats = gson.fromJson(response.getData(), chatListType);
            System.out.println("\n--- Your Chats ---");
            if (chats.isEmpty()) {
                System.out.println("No chats found.");
            } else {
                for (Chat chat : chats) {
                    System.out.println("ID: " + chat.getId() + ", Name: " + chat.getChatName() + ", Type: " + chat.getChatType());
                }
            }
        } else if (response != null) {
            System.out.println("Failed to get your chats: " + response.getMessage());
        }
    }

    private void deleteChat(int chatId) {
        Map<String, Integer> params = new HashMap<>();
        params.put("chatId", chatId);
        Request request = new Request(Command.DELETE_CHAT, gson.toJson(params));
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            System.out.println("Chat " + chatId + " deleted successfully!");
        } else if (response != null) {
            System.out.println("Failed to delete chat: " + response.getMessage());
        }
    }

    private void updateDeleteMessage(Scanner scanner) {
        System.out.println("\n--- Message Options ---");
        System.out.println("1. Update Message Content");
        System.out.println("2. Delete Message");
        System.out.println("3. Mark Message as Read (Increments View Count)");
        System.out.println("4. Back to main menu");
        System.out.print("Choose an option: ");
        String choice = scanner.nextLine();

        switch (choice) {
            case "1":
                System.out.print("Enter Message ID to update: ");
                int msgIdToUpdate = getIntInput(scanner);
                if (msgIdToUpdate == -1) break;
                System.out.print("Enter new message content: ");
                String newContent = scanner.nextLine();
                updateMessage(msgIdToUpdate, newContent);
                break;
            case "2":
                System.out.print("Enter Message ID to delete: ");
                int msgIdToDelete = getIntInput(scanner);
                if (msgIdToDelete == -1) break;
                deleteMessage(msgIdToDelete);
                break;
            case "3":
                System.out.print("Enter Message ID to mark as read: ");
                int msgIdToRead = getIntInput(scanner);
                if (msgIdToRead == -1) break;
                markMessageAsRead(msgIdToRead);
                break;
            case "4":
                break;
            default:
                System.out.println("Invalid option.");
        }
    }

    private void updateMessage(int messageId, String newContent) {
        Message message = new Message();
        message.setId(messageId);
        message.setContent(newContent);
        // Server will set senderId and editedAt

        Request request = new Request(Command.UPDATE_MESSAGE, gson.toJson(message));
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            System.out.println("Message updated successfully!");
        } else if (response != null) {
            System.out.println("Failed to update message: " + response.getMessage());
        }
    }

    private void deleteMessage(int messageId) {
        Map<String, Integer> params = new HashMap<>();
        params.put("messageId", messageId);
        Request request = new Request(Command.DELETE_MESSAGE, gson.toJson(params));
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            System.out.println("Message deleted successfully!");
        } else if (response != null) {
            System.out.println("Failed to delete message: " + response.getMessage());
        }
    }

    private void markMessageAsRead(int messageId) {
        Map<String, Integer> params = new HashMap<>();
        params.put("messageId", messageId);
        Request request = new Request(Command.MARK_MESSAGE_AS_READ, gson.toJson(params));
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            System.out.println("Message marked as read.");
        } else if (response != null) {
            System.out.println("Failed to mark message as read: " + response.getMessage());
        }
    }

    private void manageChatParticipants(Scanner scanner) {
        System.out.println("\n--- Chat Participant Management ---");
        System.out.println("1. Add Participant to Chat");
        System.out.println("2. Get Chat Participants");
        System.out.println("3. Update Participant Role");
        System.out.println("4. Remove Participant from Chat");
        System.out.println("5. Back to main menu");
        System.out.print("Choose an option: ");
        String choice = scanner.nextLine();

        switch (choice) {
            case "1":
                System.out.print("Enter Chat ID: ");
                int addPartChatId = getIntInput(scanner);
                if (addPartChatId == -1) break;
                System.out.print("Enter User ID to add: ");
                int userIdToAdd = getIntInput(scanner);
                if (userIdToAdd == -1) break;
                System.out.print("Enter Role (e.g., member, admin): ");
                String role = scanner.nextLine();
                addChatParticipant(addPartChatId, userIdToAdd, role);
                break;
            case "2":
                System.out.print("Enter Chat ID to list participants: ");
                int getPartChatId = getIntInput(scanner);
                if (getPartChatId == -1) break;
                getChatParticipants(getPartChatId);
                break;
            case "3":
                System.out.print("Enter Chat Participant ID to update role: ");
                int partIdToUpdate = getIntInput(scanner);
                if (partIdToUpdate == -1) break;
                System.out.print("Enter new Role (e.g., member, admin): ");
                String newRole = scanner.nextLine();
                updateChatParticipant(partIdToUpdate, newRole);
                break;
            case "4":
                System.out.print("Enter Chat ID to remove from: ");
                int removePartChatId = getIntInput(scanner);
                if (removePartChatId == -1) break;
                System.out.print("Enter Participant ID to remove: ");
                int participantIdToRemove = getIntInput(scanner); // This refers to the chat_participants table ID
                if (participantIdToRemove == -1) break;
                System.out.print("Enter the User ID of the participant to remove (for server validation): ");
                int userIdToRemove = getIntInput(scanner); // This is the user_id in the chat_participants table
                if (userIdToRemove == -1) break;
                removeChatParticipant(participantIdToRemove, removePartChatId, userIdToRemove);
                break;
            case "5":
                break;
            default:
                System.out.println("Invalid option.");
        }
    }

    private void addChatParticipant(int chatId, int userId, String role) {
        orgs.model.ChatParticipant participant = new orgs.model.ChatParticipant();
        participant.setChatId(chatId);
        participant.setUserId(userId);
        participant.setRole(role);

        Request request = new Request(Command.ADD_CHAT_PARTICIPANT, gson.toJson(participant));
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            System.out.println("Participant added successfully!");
        } else if (response != null) {
            System.out.println("Failed to add participant: " + response.getMessage());
        }
    }

    private void getChatParticipants(int chatId) {
        Map<String, Integer> params = new HashMap<>();
        params.put("chatId", chatId);
        Request request = new Request(Command.GET_CHAT_PARTICIPANTS, gson.toJson(params));
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            Type participantListType = new TypeToken<List<orgs.model.ChatParticipant>>() {}.getType();
            List<orgs.model.ChatParticipant> participants = gson.fromJson(response.getData(), participantListType);
            System.out.println("\n--- Participants in Chat ID: " + chatId + " ---");
            if (participants.isEmpty()) {
                System.out.println("No participants found.");
            } else {
                for (orgs.model.ChatParticipant p : participants) {
                    System.out.println("Participant ID: " + p.getId() + ", User ID: " + p.getUserId() + ", Role: " + p.getRole());
                }
            }
        } else if (response != null) {
            System.out.println("Failed to get chat participants: " + response.getMessage());
        }
    }

    private void updateChatParticipant(int participantId, String newRole) {
        orgs.model.ChatParticipant participant = new orgs.model.ChatParticipant();
        participant.setId(participantId);
        participant.setRole(newRole);

        Request request = new Request(Command.UPDATE_CHAT_PARTICIPANT, gson.toJson(participant));
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            System.out.println("Participant role updated successfully!");
        } else if (response != null) {
            System.out.println("Failed to update participant role: " + response.getMessage());
        }
    }

    private void removeChatParticipant(int participantId, int chatId, int userIdToRemove) {
        Map<String, Integer> params = new HashMap<>();
        params.put("participantId", participantId);
        params.put("chatId", chatId); // Pass for server-side validation
        params.put("userId", userIdToRemove); // Pass for server-side validation

        Request request = new Request(Command.REMOVE_CHAT_PARTICIPANT, gson.toJson(params));
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            System.out.println("Participant removed successfully!");
        } else if (response != null) {
            System.out.println("Failed to remove participant: " + response.getMessage());
        }
    }

    private void manageContacts(Scanner scanner) {
        System.out.println("\n--- Contact Management ---");
        System.out.println("1. Add Contact");
        System.out.println("2. List Contacts");
        System.out.println("3. Remove Contact");
        System.out.println("4. Back to main menu");
        System.out.print("Choose an option: ");
        String choice = scanner.nextLine();

        switch (choice) {
            case "1":
                System.out.print("Enter User ID to add as contact: ");
                int contactUserId = getIntInput(scanner);
                if (contactUserId == -1) break;
                addContact(contactUserId);
                break;
            case "2":
                getContacts();
                break;
            case "3":
                System.out.print("Enter User ID to remove from contacts: ");
                int removeContactId = getIntInput(scanner);
                if (removeContactId == -1) break;
                removeContact(removeContactId);
                break;
            case "4":
                break;
            default:
                System.out.println("Invalid option.");
        }
    }

    private void addContact(int contactUserId) {
        Map<String, Integer> params = new HashMap<>();
        params.put("contactUserId", contactUserId);
        Request request = new Request(Command.ADD_CONTACT, gson.toJson(params));
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            System.out.println("Contact added successfully!");
        } else if (response != null) {
            System.out.println("Failed to add contact: " + response.getMessage());
        }
    }

    private void getContacts() {
        Request request = new Request(Command.GET_CONTACTS, null);
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            Type userListType = new TypeToken<List<User>>() {}.getType();
            List<User> contacts = gson.fromJson(response.getData(), userListType);
            System.out.println("\n--- Your Contacts ---");
            if (contacts.isEmpty()) {
                System.out.println("No contacts found.");
            } else {
                for (User contact : contacts) {
                    System.out.println("ID: " + contact.getId() + ", Username: " + contact.getUsername() + ", Name: " + contact.getFirstName() + " " + contact.getLastName() + (contact.isOnline() ? " (Online)" : ""));
                }
            }
        } else if (response != null) {
            System.out.println("Failed to get contacts: " + response.getMessage());
        }
    }

    private void removeContact(int contactUserId) {
        Map<String, Integer> params = new HashMap<>();
        params.put("contactUserId", contactUserId);
        Request request = new Request(Command.REMOVE_CONTACT, gson.toJson(params));
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            System.out.println("Contact removed successfully!");
        } else if (response != null) {
            System.out.println("Failed to remove contact: " + response.getMessage());
        }
    }

    private void blockUnblockUser(Scanner scanner) {
        System.out.println("\n--- Block/Unblock User ---");
        System.out.println("1. Block User");
        System.out.println("2. Unblock User");
        System.out.println("3. Back to main menu");
        System.out.print("Choose an option: ");
        String choice = scanner.nextLine();

        switch (choice) {
            case "1":
                System.out.print("Enter User ID to block: ");
                int blockUserId = getIntInput(scanner);
                if (blockUserId == -1) break;
                blockUser(blockUserId);
                break;
            case "2":
                System.out.print("Enter User ID to unblock: ");
                int unblockUserId = getIntInput(scanner);
                if (unblockUserId == -1) break;
                unblockUser(unblockUserId);
                break;
            case "3":
                break;
            default:
                System.out.println("Invalid option.");
        }
    }

    private void blockUser(int targetUserId) {
        Map<String, Integer> params = new HashMap<>();
        params.put("targetUserId", targetUserId);
        Request request = new Request(Command.BLOCK_USER, gson.toJson(params));
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            System.out.println("User blocked successfully!");
        } else if (response != null) {
            System.out.println("Failed to block user: " + response.getMessage());
        }
    }

    private void unblockUser(int targetUserId) {
        Map<String, Integer> params = new HashMap<>();
        params.put("targetUserId", targetUserId);
        Request request = new Request(Command.UNBLOCK_USER, gson.toJson(params));
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            System.out.println("User unblocked successfully!");
        } else if (response != null) {
            System.out.println("Failed to unblock user: " + response.getMessage());
        }
    }

    private void manageNotifications(Scanner scanner) {
        System.out.println("\n--- Notification Management ---");
        System.out.println("1. Get My Notifications");
        System.out.println("2. Mark Notification as Read");
        System.out.println("3. Delete Notification");
        System.out.println("4. Back to main menu");
        System.out.print("Choose an option: ");
        String choice = scanner.nextLine();

        switch (choice) {
            case "1":
                getUserNotifications();
                break;
            case "2":
                System.out.print("Enter Notification ID to mark as read: ");
                int notifIdToMark = getIntInput(scanner);
                if (notifIdToMark == -1) break;
                markNotificationAsRead(notifIdToMark);
                break;
            case "3":
                System.out.print("Enter Notification ID to delete: ");
                int notifIdToDelete = getIntInput(scanner);
                if (notifIdToDelete == -1) break;
                deleteNotification(notifIdToDelete);
                break;
            case "4":
                break;
            default:
                System.out.println("Invalid option.");
        }
    }

    private void getUserNotifications() {
        Request request = new Request(Command.GET_USER_NOTIFICATIONS, null);
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            Type notificationListType = new TypeToken<List<orgs.model.Notification>>() {}.getType();
            List<orgs.model.Notification> notifications = gson.fromJson(response.getData(), notificationListType);
            System.out.println("\n--- Your Notifications ---");
            if (notifications.isEmpty()) {
                System.out.println("No notifications found.");
            } else {
                for (orgs.model.Notification notif : notifications) {
                    System.out.println("ID: " + notif.getId() + ", Message: " + notif.getMessage() + ", Read: " + notif.isRead() );
                }
            }
        } else if (response != null) {
            System.out.println("Failed to get notifications: " + response.getMessage());
        }
    }

    private void markNotificationAsRead(int notificationId) {
        Map<String, Integer> params = new HashMap<>();
        params.put("notificationId", notificationId);
        Request request = new Request(Command.MARK_NOTIFICATION_AS_READ, gson.toJson(params));
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            System.out.println("Notification marked as read.");
        } else if (response != null) {
            System.out.println("Failed to mark notification as read: " + response.getMessage());
        }
    }

    private void deleteNotification(int notificationId) {
        Map<String, Integer> params = new HashMap<>();
        params.put("notificationId", notificationId);
        Request request = new Request(Command.DELETE_NOTIFICATION, gson.toJson(params));
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            System.out.println("Notification deleted successfully.");
        } else if (response != null) {
            System.out.println("Failed to delete notification: " + response.getMessage());
        }
    }


    // This method sends a request and then waits for its specific response from the queue
    private Response sendRequestAndAwaitResponse(Request request) {
        try {
            // Clear the queue before sending, to ensure we only get the response for this request
            // This is a simple approach. For more complex scenarios, you might use request IDs.
            responseQueue.clear();
            out.println(request.toJson());

            // Wait for a response for a certain period
            // Adjust the timeout as needed
            Response response = responseQueue.poll(5, TimeUnit.SECONDS);
            if (response == null) {
                System.err.println("Timeout: No response received from server.");
            }
            return response;
        } catch (InterruptedException e) {
            System.err.println("Request interrupted while waiting for response: " + e.getMessage());
            Thread.currentThread().interrupt(); // Restore interrupted status
            return null;
        } catch (Exception e) {
            System.err.println("Error sending request: " + e.getMessage());
            return null;
        }
    }

    private boolean login(String username, String password) {
        Map<String, String> loginData = new HashMap<>();
        loginData.put("username", username);
        loginData.put("password", password);

        Request request = new Request(Command.LOGIN, gson.toJson(loginData));
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            currentUser = gson.fromJson(response.getData(), User.class);
            return true;
        } else if (response != null) {
            System.out.println("Login Failed: " + response.getMessage());
        }
        return false;
    }

    private void sendMessage(int chatId, String content) {
        if (currentUser == null) {
            System.out.println("You must be logged in to send messages.");
            return;
        }
        Message message = new Message();
        message.setChatId(chatId);
        message.setContent(content);

        Request request = new Request(Command.SEND_MESSAGE, gson.toJson(message));
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            System.out.println("Message sent successfully!");
        } else if (response != null) {
            System.out.println("Failed to send message: " + response.getMessage());
        }
    }

    private void getChatMessages(int chatId) {
        if (currentUser == null) {
            System.out.println("You must be logged in to view messages.");
            return;
        }
        Map<String, Integer> params = new HashMap<>();
        params.put("chatId", chatId);
        params.put("limit", 20);

        Request request = new Request(Command.GET_CHAT_MESSAGES, gson.toJson(params));
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            Type messageListType = new TypeToken<List<Message>>() {}.getType();
            List<Message> messages = gson.fromJson(response.getData(), messageListType);
            System.out.println("\n--- Messages in Chat ID: " + chatId + " ---");
            if (messages.isEmpty()) {
                System.out.println("No messages found.");
            } else {
                for (Message msg : messages) {
                    // Ensure you can get sender's username if possible or just show ID
                    System.out.println(msg.getSentAt().toLocalTime() + " - From User " + msg.getSenderId() + ": " + msg.getContent());
                }
            }
        } else if (response != null) {
            System.out.println("Failed to retrieve messages: " + response.getMessage());
        }
    }

    private void createChat(String chatName) {
        if (currentUser == null) {
            System.out.println("You must be logged in to create chats.");
            return;
        }

        Chat newChat = new Chat();
        newChat.setChatName(chatName);
        newChat.setChatType("private");

        Request request = new Request(Command.CREATE_CHAT, gson.toJson(newChat));
        Response response = sendRequestAndAwaitResponse(request);

        if (response != null && response.isSuccess()) {
            Chat createdChat = gson.fromJson(response.getData(), Chat.class);
            System.out.println("Chat created successfully! Chat ID: " + createdChat.getId());
        } else if (response != null) {
            System.out.println("Failed to create chat: " + response.getMessage());
        }
    }

    private void closeConnection() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
            System.out.println("Connection closed.");
        } catch (IOException e) {
            System.err.println("Error closing client resources: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        ChatClient client = new ChatClient();
        client.startClient();
    }
}