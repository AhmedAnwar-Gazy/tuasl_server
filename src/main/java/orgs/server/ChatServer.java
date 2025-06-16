// src/orgs/server/ChatServer.java
package orgs.server;

import orgs.protocol.Command;
import orgs.protocol.Request;
import orgs.protocol.Response;
import orgs.dao.UserDao; // Example DAO usage
import orgs.dao.MessageDao; // Example DAO usage
import orgs.model.Message;
import orgs.model.User;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import orgs.utils.LocalDateTimeAdapter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatServer {
    private static final int PORT = 12345;
    private ExecutorService pool = Executors.newFixedThreadPool(10); // Thread pool for clients
    private UserDao userDao = new UserDao();
    private MessageDao messageDao = new MessageDao();
    private Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .serializeNulls() // Keep this if you want nulls serialized
            .create();
    // Map to keep track of logged-in users and their associated ClientHandlers
    // Key: userId, Value: ClientHandler (for sending messages to specific clients)
    private static final Map<Integer, ClientHandler> loggedInUsers = new ConcurrentHashMap<>();

    public ChatServer() {
        // Initialize DAOs or other server-wide resources if needed
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Chat Server started on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress().getHostAddress());
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                pool.execute(clientHandler);
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            pool.shutdown();
        }
    }

    private class ClientHandler implements Runnable {
        private Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;
        private int currentUserId = -1; // To store the ID of the logged-in user for this handler

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                String clientRequestJson;
                while ((clientRequestJson = in.readLine()) != null) {
                    System.out.println("Received from client " + clientSocket.getInetAddress().getHostAddress() + ": " + clientRequestJson);
                    Request request = gson.fromJson(clientRequestJson, Request.class);
                    processRequest(request);
                }
            } catch (IOException e) {
                if (currentUserId != -1) {
                    System.out.println("Client " + currentUserId + " disconnected.");
                    loggedInUsers.remove(currentUserId);
                    userDao.updateUserOnlineStatus(currentUserId, false); // Mark user offline
                } else {
                    System.out.println("Client disconnected unexpectedly: " + clientSocket.getInetAddress().getHostAddress() + " - " + e.getMessage());
                }
            } finally {
                try {
                    if (currentUserId != -1) {
                        loggedInUsers.remove(currentUserId);
                        userDao.updateUserOnlineStatus(currentUserId, false); // Ensure offline status on exit
                    }
                    if (in != null) in.close();
                    if (out != null) out.close();
                    if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing client handler resources: " + e.getMessage());
                }
            }
        }

        private void processRequest(Request request) {
            Response response;
            // Ensure user is logged in for most operations, except LOGIN and REGISTER
            if (currentUserId == -1 && request.getCommand() != Command.LOGIN && request.getCommand() != Command.REGISTER) {
                response = new Response(false, "Authentication required. Please log in.", null);
                out.println(response.toJson());
                return;
            }

            try {
                switch (request.getCommand()) {
                    // Existing commands
                    case LOGIN:
                        response = handleLogin(request.getPayload());
                        break;
                    case SEND_MESSAGE:
                        response = handleSendMessage(request.getPayload());
                        break;
                    case GET_CHAT_MESSAGES:
                        response = handleGetChatMessages(request.getPayload());
                        break;
                    case CREATE_CHAT:
                        response = handleCreateChat(request.getPayload());
                        break;

                    // --- New Commands (User Management) ---
                    case REGISTER:
                        response = handleRegister(request.getPayload());
                        break;
                    case GET_USER_PROFILE:
                        response = handleGetUserProfile(request.getPayload());
                        break;
                    case UPDATE_USER_PROFILE:
                        response = handleUpdateUserProfile(request.getPayload());
                        break;
                    case DELETE_USER:
                        response = handleDeleteUser(request.getPayload());
                        break;
                    case GET_ALL_USERS:
                        response = handleGetAllUsers();
                        break;

                    // --- New Commands (Chat Management) ---
                    case GET_USER_CHATS:
                        response = handleGetUserChats();
                        break;
                    case GET_CHAT_DETAILS:
                        response = handleGetChatDetails(request.getPayload());
                        break;
                    case UPDATE_CHAT:
                        response = handleUpdateChat(request.getPayload());
                        break;
                    case DELETE_CHAT:
                        response = handleDeleteChat(request.getPayload());
                        break;

                    // --- New Commands (Message Management) ---
                    // SEND_MESSAGE and GET_CHAT_MESSAGES already exist
                    case UPDATE_MESSAGE:
                        response = handleUpdateMessage(request.getPayload());
                        break;
                    case DELETE_MESSAGE:
                        response = handleDeleteMessage(request.getPayload());
                        break;
                    case MARK_MESSAGE_AS_READ:
                        response = handleMarkMessageAsRead(request.getPayload());
                        break;

                    // --- New Commands (Chat Participant Management) ---
                    case ADD_CHAT_PARTICIPANT:
                        response = handleAddChatParticipant(request.getPayload());
                        break;
                    case GET_CHAT_PARTICIPANTS:
                        response = handleGetChatParticipants(request.getPayload());
                        break;
                    case UPDATE_CHAT_PARTICIPANT:
                        response = handleUpdateChatParticipant(request.getPayload());
                        break;
                    case REMOVE_CHAT_PARTICIPANT:
                        response = handleRemoveChatParticipant(request.getPayload());
                        break;

                    // --- New Commands (Contact Management) ---
                    case ADD_CONTACT:
                        response = handleAddContact(request.getPayload());
                        break;
                    case GET_CONTACTS:
                        response = handleGetContacts();
                        break;
                    case REMOVE_CONTACT:
                        response = handleRemoveContact(request.getPayload());
                        break;
                    case BLOCK_USER:
                        response = handleBlockUser(request.getPayload());
                        break;
                    case UNBLOCK_USER:
                        response = handleUnblockUser(request.getPayload());
                        break;

                    // --- New Commands (Notification Management) ---
                    // CREATE_NOTIFICATION usually server-initiated
                    case GET_USER_NOTIFICATIONS:
                        response = handleGetUserNotifications();
                        break;
                    case MARK_NOTIFICATION_AS_READ:
                        response = handleMarkNotificationAsRead(request.getPayload());
                        break;
                    case DELETE_NOTIFICATION:
                        response = handleDeleteNotification(request.getPayload());
                        break;

                    default:
                        response = new Response(false, "Unknown command: " + request.getCommand(), null);
                }
            } catch (Exception e) {
                System.err.println("Error processing command " + request.getCommand() + ": " + e.getMessage());
                e.printStackTrace();
                response = new Response(false, "Server internal error: " + e.getMessage(), null);
            }
            out.println(response.toJson());
        }

        // --- Helper for broadcasting messages (simplistic example) ---
        private void notifyChatParticipants(int chatId, Response notificationResponse) {
            try {
                // In a real app, fetch participants of this chatId from DB
                // For demo, let's just get all logged-in users and send to them
                // You'd need ChatParticipantDao here to get actual participants
                // and then check if their IDs are in loggedInUsers.
                // For now, a simple broadcast to all connected clients (if it's a critical update)
                loggedInUsers.forEach((userId, handler) -> {
                    // Refine this: Only send if userId is a participant of chatId
                    if (handler.currentUserId != -1 && handler.out != null) {
                        handler.out.println(notificationResponse.toJson());
                    }
                });
            } catch (Exception e) {
                System.err.println("Error notifying chat participants: " + e.getMessage());
            }
        }


        // --- New Command Implementations (User Management) ---

        private Response handleRegister(String payload) {
            try {
                User newUser = gson.fromJson(payload, User.class);
                // Basic validation
                if (newUser.getUsername() == null || newUser.getUsername().isEmpty() ||
                        newUser.getPassword() == null || newUser.getPassword().isEmpty() ||
                        newUser.getPhoneNumber() == null || newUser.getPhoneNumber().isEmpty()) {
                    return new Response(false, "Missing required user registration fields.", null);
                }
                // Check if username or phone number already exists
                if (userDao.getUserByUsername(newUser.getUsername()).isPresent() ||
                        userDao.getUserByPhoneNumber(newUser.getPhoneNumber()).isPresent()) {
                    return new Response(false, "Username or phone number already registered.", null);
                }

                // IMPORTANT: Hash password before storing!
                // newUser.setPassword(PasswordHasher.hash(newUser.getPassword()));
                // For this example, we're taking plain text password (DO NOT DO IN PROD)

                int userId = userDao.createUser(newUser);
                if (userId != -1) {
                    newUser.setId(userId);
                    return new Response(true, "Registration successful!", gson.toJson(newUser));
                } else {
                    return new Response(false, "Failed to register user.", null);
                }
            } catch (Exception e) {
                System.err.println("Error during registration: " + e.getMessage());
                return new Response(false, "Server error during registration.", null);
            }
        }

        private Response handleGetUserProfile(String payload) {
            // Payload could be a userId or username
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> params = gson.fromJson(payload, type);
            String targetUserIdStr = params.get("userId");
            String targetUsername = params.get("username");

            Optional<User> userOptional;
            if (targetUserIdStr != null) {
                int targetUserId = Integer.parseInt(targetUserIdStr);
                userOptional = userDao.getUserById(targetUserId);
            } else if (targetUsername != null) {
                userOptional = userDao.getUserByUsername(targetUsername);
            } else {
                // If no specific user requested, return current user's profile
                userOptional = userDao.getUserById(currentUserId);
            }

            if (userOptional.isPresent()) {
                // Remove sensitive info like password before sending
                User user = userOptional.get();
                user.setPassword(null); // IMPORTANT: Never send password hash to client
                return new Response(true, "User profile retrieved.", gson.toJson(user));
            } else {
                return new Response(false, "User not found.", null);
            }
        }

        private Response handleUpdateUserProfile(String payload) {
            try {
                User updatedUser = gson.fromJson(payload, User.class);
                // Ensure the user is updating their own profile
                if (updatedUser.getId() != currentUserId) {
                    return new Response(false, "Unauthorized: You can only update your own profile.", null);
                }

                // Important: Only allow certain fields to be updated, not ID or password directly from this payload
                // If password update is needed, it should be a separate, secure operation.
                User existingUser = userDao.getUserById(currentUserId).orElse(null);
                if (existingUser == null) {
                    return new Response(false, "User not found for update.", null);
                }

                // Update fields that are allowed to be changed
                existingUser.setFirstName(updatedUser.getFirstName());
                existingUser.setLastName(updatedUser.getLastName());
                existingUser.setBio(updatedUser.getBio());
                existingUser.setProfilePictureUrl(updatedUser.getProfilePictureUrl());
                // Add logic for phone_number and username if they can be changed,
                // potentially with unique checks.

                boolean success = userDao.updateUser(existingUser);
                if (success) {
                    // Update current user object in handler if needed for subsequent operations
                    //currentUser = existingUser; // If you pass a `currentUser` object to ClientHandler's constructor
                    return new Response(true, "Profile updated successfully!", gson.toJson(existingUser));
                } else {
                    return new Response(false, "Failed to update profile.", null);
                }
            } catch (Exception e) {
                System.err.println("Error updating user profile: " + e.getMessage());
                return new Response(false, "Server error updating profile.", null);
            }
        }

        private Response handleDeleteUser(String payload) {
            // Payload should contain user ID or confirm it's current user
            Type type = new TypeToken<Map<String, Integer>>() {}.getType();
            Map<String, Integer> params = gson.fromJson(payload, type);
            int targetUserId = params.get("userId"); // Should be currentUserId, or admin

            // Only allow user to delete their own account for now
            if (targetUserId != currentUserId) {
                return new Response(false, "Unauthorized: You can only delete your own account.", null);
            }

            try {
                // Log out user first
                loggedInUsers.remove(currentUserId);
                userDao.updateUserOnlineStatus(currentUserId, false); // Mark offline
                this.currentUserId = -1; // Reset handler's user ID

                boolean success = userDao.deleteUser(targetUserId);
                if (success) {
                    // Also close client socket
                    try {
                        clientSocket.close();
                    } catch (IOException e) {
                        System.err.println("Error closing socket after user deletion: " + e.getMessage());
                    }
                    return new Response(true, "User account deleted successfully.", null);
                } else {
                    return new Response(false, "Failed to delete user account.", null);
                }
            } catch (Exception e) {
                System.err.println("Error deleting user account: " + e.getMessage());
                return new Response(false, "Server error deleting account.", null);
            }
        }

        private Response handleGetAllUsers() {
            // This is typically an admin function or for populating a contact list.
            // Be mindful of privacy and performance for large user bases.
            try {
                List<User> users = userDao.getAllUsers();
                // Strip sensitive data before sending
                users.forEach(u -> u.setPassword(null));
                return new Response(true, "All users retrieved.", gson.toJson(users));
            } catch (Exception e) {
                System.err.println("Error getting all users: " + e.getMessage());
                return new Response(false, "Server error retrieving users.", null);
            }
        }


        // --- New Command Implementations (Chat Management) ---

        private Response handleGetUserChats() {
            try {
                // Get all chats the current user is a participant of
                orgs.dao.ChatDao chatDao = new orgs.dao.ChatDao();
                List<orgs.model.Chat> chats = chatDao.getUserChats(currentUserId);
                return new Response(true, "User chats retrieved.", gson.toJson(chats));
            } catch (Exception e) {
                System.err.println("Error getting user chats: " + e.getMessage());
                return new Response(false, "Server error retrieving user chats.", null);
            }
        }

        private Response handleGetChatDetails(String payload) {
            try {
                Type type = new TypeToken<Map<String, Integer>>() {}.getType();
                Map<String, Integer> params = gson.fromJson(payload, type);
                int chatId = params.get("chatId");

                orgs.dao.ChatDao chatDao = new orgs.dao.ChatDao();
                Optional<orgs.model.Chat> chatOptional = chatDao.getChatById(chatId);

                // Check if user is a participant of this chat
                orgs.dao.ChatParticipantDao cpDao = new orgs.dao.ChatParticipantDao();
                if (!cpDao.isUserParticipant(chatId, currentUserId)) {
                    return new Response(false, "Unauthorized: You are not a participant of this chat.", null);
                }

                if (chatOptional.isPresent()) {
                    return new Response(true, "Chat details retrieved.", gson.toJson(chatOptional.get()));
                } else {
                    return new Response(false, "Chat not found.", null);
                }
            } catch (Exception e) {
                System.err.println("Error getting chat details: " + e.getMessage());
                return new Response(false, "Server error retrieving chat details.", null);
            }
        }

        private Response handleUpdateChat(String payload) {
            try {
                orgs.model.Chat updatedChat = gson.fromJson(payload, orgs.model.Chat.class);

                // Check if the current user is authorized to update this chat (e.g., creator or admin)
                orgs.dao.ChatDao chatDao = new orgs.dao.ChatDao();
                Optional<orgs.model.Chat> existingChatOptional = chatDao.getChatById(updatedChat.getId());

                if (!existingChatOptional.isPresent()) {
                    return new Response(false, "Chat not found.", null);
                }
                orgs.model.Chat existingChat = existingChatOptional.get();

                // Example authorization: only creator can update chat name/type
                if (existingChat.getCreatorId() != currentUserId) {
                    return new Response(false, "Unauthorized: Only the chat creator can update chat details.", null);
                }

                // Update only allowed fields (e.g., chatName, chatType)
                existingChat.setChatName(updatedChat.getChatName());
                existingChat.setChatType(updatedChat.getChatType()); // Be careful with changing chat type

                boolean success = chatDao.updateChat(existingChat);
                if (success) {
                    return new Response(true, "Chat updated successfully!", gson.toJson(existingChat));
                } else {
                    return new Response(false, "Failed to update chat.", null);
                }
            } catch (Exception e) {
                System.err.println("Error updating chat: " + e.getMessage());
                return new Response(false, "Server error updating chat.", null);
            }
        }

        private Response handleDeleteChat(String payload) {
            try {
                Type type = new TypeToken<Map<String, Integer>>() {}.getType();
                Map<String, Integer> params = gson.fromJson(payload, type);
                int chatId = params.get("chatId");

                orgs.dao.ChatDao chatDao = new orgs.dao.ChatDao();
                Optional<orgs.model.Chat> chatOptional = chatDao.getChatById(chatId);

                if (!chatOptional.isPresent()) {
                    return new Response(false, "Chat not found.", null);
                }
                orgs.model.Chat chatToDelete = chatOptional.get();

                // Authorization: Only the creator can delete the chat
                if (chatToDelete.getCreatorId() != currentUserId) {
                    return new Response(false, "Unauthorized: Only the chat creator can delete this chat.", null);
                }

                // Deleting a chat should also delete related messages and chat participants (CASCADE DELETE in DB or manually)
                boolean success = chatDao.deleteChat(chatId);
                if (success) {
                    return new Response(true, "Chat deleted successfully.", null);
                } else {
                    return new Response(false, "Failed to delete chat.", null);
                }
            } catch (Exception e) {
                System.err.println("Error deleting chat: " + e.getMessage());
                return new Response(false, "Server error deleting chat.", null);
            }
        }


        // --- New Command Implementations (Message Management) ---

        // handleSendMessage and handleGetChatMessages already exist

        private Response handleUpdateMessage(String payload) {
            try {
                Message updatedMessage = gson.fromJson(payload, Message.class);
                // Ensure current user is the sender of the message
                Optional<Message> existingMessageOptional = messageDao.getMessageById(updatedMessage.getId());
                if (!existingMessageOptional.isPresent()) {
                    return new Response(false, "Message not found.", null);
                }
                Message existingMessage = existingMessageOptional.get();

                if (existingMessage.getSenderId() != currentUserId) {
                    return new Response(false, "Unauthorized: You can only update your own messages.", null);
                }

                // Update content, and possibly edit_timestamp
                existingMessage.setContent(updatedMessage.getContent());
                existingMessage.setEditedAt(LocalDateTime.now());

                boolean success = messageDao.updateMessage(existingMessage);
                if (success) {
                    // Potentially notify chat participants of the message edit
                    // notifyChatParticipants(existingMessage.getChatId(), new Response(true, "Message updated", gson.toJson(existingMessage)));
                    return new Response(true, "Message updated successfully!", gson.toJson(existingMessage));
                } else {
                    return new Response(false, "Failed to update message.", null);
                }
            } catch (Exception e) {
                System.err.println("Error updating message: " + e.getMessage());
                return new Response(false, "Server error updating message.", null);
            }
        }

        private Response handleDeleteMessage(String payload) {
            try {
                Type type = new TypeToken<Map<String, Integer>>() {}.getType();
                Map<String, Integer> params = gson.fromJson(payload, type);
                int messageId = params.get("messageId");

                Optional<Message> messageOptional = messageDao.getMessageById(messageId);
                if (!messageOptional.isPresent()) {
                    return new Response(false, "Message not found.", null);
                }
                Message messageToDelete = messageOptional.get();

                // Authorization: Only sender can delete their message, or chat admin/creator
                if (messageToDelete.getSenderId() != currentUserId) {
                    // TODO: Implement logic to check if current user is chat admin/creator
                    return new Response(false, "Unauthorized: You can only delete your own messages.", null);
                }

                boolean success = messageDao.deleteMessage(messageId);
                if (success) {
                    // Potentially notify chat participants that a message was deleted
                    // notifyChatParticipants(messageToDelete.getChatId(), new Response(true, "Message deleted", gson.toJson(messageToDelete)));
                    return new Response(true, "Message deleted successfully.", null);
                } else {
                    return new Response(false, "Failed to delete message.", null);
                }
            } catch (Exception e) {
                System.err.println("Error deleting message: " + e.getMessage());
                return new Response(false, "Server error deleting message.", null);
            }
        }

        private Response handleMarkMessageAsRead(String payload) {
            try {
                Type type = new TypeToken<Map<String, Integer>>() {}.getType();
                Map<String, Integer> params = gson.fromJson(payload, type);
                int messageId = params.get("messageId");

                Optional<Message> messageOptional = messageDao.getMessageById(messageId);
                if (!messageOptional.isPresent()) {
                    return new Response(false, "Message not found.", null);
                }
                Message message = messageOptional.get();

                // Increment view count
                message.setViewCount(message.getViewCount() + 1);
                boolean success = messageDao.updateMessage(message); // Re-using updateMessage, or specific updateViewCount in DAO
                if (success) {
                    return new Response(true, "Message marked as read.", null);
                } else {
                    return new Response(false, "Failed to mark message as read.", null);
                }
            } catch (Exception e) {
                System.err.println("Error marking message as read: " + e.getMessage());
                return new Response(false, "Server error marking message as read.", null);
            }
        }


        // --- New Command Implementations (Chat Participant Management) ---

        private Response handleAddChatParticipant(String payload) {
            try {
                orgs.model.ChatParticipant participant = gson.fromJson(payload, orgs.model.ChatParticipant.class);

                // Basic validation: user and chat exist, and current user has permission
                orgs.dao.ChatDao chatDao = new orgs.dao.ChatDao();
                Optional<orgs.model.Chat> chatOptional = chatDao.getChatById(participant.getChatId());
                if (!chatOptional.isPresent()) {
                    return new Response(false, "Chat not found.", null);
                }
                orgs.model.Chat chat = chatOptional.get();

                // Authorization: Only chat creator/admin can add participants
                if (chat.getCreatorId() != currentUserId) {
                    return new Response(false, "Unauthorized: Only the chat creator can add participants.", null);
                }
                // Ensure target user exists
                if (!userDao.getUserById(participant.getUserId()).isPresent()) {
                    return new Response(false, "Target user for adding to chat not found.", null);
                }

                orgs.dao.ChatParticipantDao cpDao = new orgs.dao.ChatParticipantDao();
                int participantId = cpDao.createChatParticipant(participant);
                if (participantId != -1) {
                    participant.setId(participantId);
                    return new Response(true, "Participant added successfully!", gson.toJson(participant));
                } else {
                    return new Response(false, "Failed to add participant (possibly already exists).", null);
                }
            } catch (Exception e) {
                System.err.println("Error adding chat participant: " + e.getMessage());
                return new Response(false, "Server error adding participant.", null);
            }
        }

        private Response handleGetChatParticipants(String payload) {
            try {
                Type type = new TypeToken<Map<String, Integer>>() {}.getType();
                Map<String, Integer> params = gson.fromJson(payload, type);
                int chatId = params.get("chatId");

                // Authorization: Only participants of the chat can see other participants
                orgs.dao.ChatParticipantDao cpDao = new orgs.dao.ChatParticipantDao();
                if (!cpDao.isUserParticipant(chatId, currentUserId)) {
                    return new Response(false, "Unauthorized: You are not a participant of this chat.", null);
                }

                List<orgs.model.ChatParticipant> participants = cpDao.getChatParticipants(chatId);
                return new Response(true, "Chat participants retrieved.", gson.toJson(participants));
            } catch (Exception e) {
                System.err.println("Error getting chat participants: " + e.getMessage());
                return new Response(false, "Server error retrieving participants.", null);
            }
        }

        private Response handleUpdateChatParticipant(String payload) {
            try {
                orgs.model.ChatParticipant updatedParticipant = gson.fromJson(payload, orgs.model.ChatParticipant.class);

                orgs.dao.ChatParticipantDao cpDao = new orgs.dao.ChatParticipantDao();
                Optional<orgs.model.ChatParticipant> existingParticipantOptional = cpDao.getChatParticipantById(updatedParticipant.getId());

                if (!existingParticipantOptional.isPresent()) {
                    return new Response(false, "Chat participant not found.", null);
                }
                orgs.model.ChatParticipant existingParticipant = existingParticipantOptional.get();

                // Authorization: Only chat creator/admin can update participant roles
                orgs.dao.ChatDao chatDao = new orgs.dao.ChatDao();
                Optional<orgs.model.Chat> chatOptional = chatDao.getChatById(existingParticipant.getChatId());
                if (!chatOptional.isPresent() || chatOptional.get().getCreatorId() != currentUserId) {
                    return new Response(false, "Unauthorized: Only the chat creator can update participant roles.", null);
                }

                existingParticipant.setRole(updatedParticipant.getRole()); // e.g., "member", "admin"
                boolean success = cpDao.updateChatParticipant(existingParticipant);
                if (success) {
                    return new Response(true, "Chat participant role updated.", gson.toJson(existingParticipant));
                } else {
                    return new Response(false, "Failed to update participant role.", null);
                }
            } catch (Exception e) {
                System.err.println("Error updating chat participant: " + e.getMessage());
                return new Response(false, "Server error updating participant.", null);
            }
        }

        private Response handleRemoveChatParticipant(String payload) {
            try {
                Type type = new TypeToken<Map<String, Integer>>() {}.getType();
                Map<String, Integer> params = gson.fromJson(payload, type);
                int participantId = params.get("participantId"); // This is the ID of the participant entry
                int chatId = params.get("chatId"); // Required for authorization checks
                int userIdToRemove = params.get("userId"); // The actual user ID to remove

                orgs.dao.ChatParticipantDao cpDao = new orgs.dao.ChatParticipantDao();
                orgs.dao.ChatDao chatDao = new orgs.dao.ChatDao();
                Optional<orgs.model.Chat> chatOptional = chatDao.getChatById(chatId);

                if (!chatOptional.isPresent()) {
                    return new Response(false, "Chat not found.", null);
                }
                orgs.model.Chat chat = chatOptional.get();

                // Authorization: Only chat creator can remove others, or user can leave themselves
                if (chat.getCreatorId() != currentUserId && userIdToRemove != currentUserId) {
                    return new Response(false, "Unauthorized: Only the chat creator can remove others, or you can leave yourself.", null);
                }

                boolean success = cpDao.deleteChatParticipant(participantId); // Or delete by (chatId, userId) pair
                if (success) {
                    return new Response(true, "Participant removed successfully.", null);
                } else {
                    return new Response(false, "Failed to remove participant.", null);
                }
            } catch (Exception e) {
                System.err.println("Error removing chat participant: " + e.getMessage());
                return new Response(false, "Server error removing participant.", null);
            }
        }

        // --- New Command Implementations (Contact Management) ---
        // Requires a ContactDao and Contact model

        private Response handleAddContact(String payload) {
            try {
                Type type = new TypeToken<Map<String, Integer>>() {}.getType();
                Map<String, Integer> params = gson.fromJson(payload, type);
                int contactUserId = params.get("contactUserId");

                if (contactUserId == currentUserId) {
                    return new Response(false, "Cannot add yourself as a contact.", null);
                }
                if (!userDao.getUserById(contactUserId).isPresent()) {
                    return new Response(false, "Contact user not found.", null);
                }

                orgs.dao.ContactDao contactDao = new orgs.dao.ContactDao();
                // Check if already a contact
                if (contactDao.getContact(currentUserId, contactUserId).isPresent()) {
                    return new Response(false, "User is already in your contacts.", null);
                }

                orgs.model.Contact newContact = new orgs.model.Contact();
                newContact.setUserId(currentUserId);
                newContact.setContactUserId(contactUserId);

                int contactId = contactDao.createContact(newContact);
                if (contactId != -1) {
                    newContact.setId(contactId);
                    return new Response(true, "Contact added successfully!", gson.toJson(newContact));
                } else {
                    return new Response(false, "Failed to add contact.", null);
                }
            } catch (Exception e) {
                System.err.println("Error adding contact: " + e.getMessage());
                return new Response(false, "Server error adding contact.", null);
            }
        }

        private Response handleGetContacts() {
            try {
                orgs.dao.ContactDao contactDao = new orgs.dao.ContactDao();
                List<User> contacts = contactDao.getUserContacts(currentUserId);
                // Strip passwords
                contacts.forEach(u -> u.setPassword(null));
                return new Response(true, "Contacts retrieved.", gson.toJson(contacts));
            } catch (Exception e) {
                System.err.println("Error getting contacts: " + e.getMessage());
                return new Response(false, "Server error retrieving contacts.", null);
            }
        }

        private Response handleRemoveContact(String payload) {
            try {
                Type type = new TypeToken<Map<String, Integer>>() {}.getType();
                Map<String, Integer> params = gson.fromJson(payload, type);
                int contactUserId = params.get("contactUserId");

                orgs.dao.ContactDao contactDao = new orgs.dao.ContactDao();
                boolean success = contactDao.deleteContact(currentUserId, contactUserId);
                if (success) {
                    return new Response(true, "Contact removed successfully.", null);
                } else {
                    return new Response(false, "Failed to remove contact (not found or error).", null);
                }
            } catch (Exception e) {
                System.err.println("Error removing contact: " + e.getMessage());
                return new Response(false, "Server error removing contact.", null);
            }
        }

        private Response handleBlockUser(String payload) {
            try {
                Type type = new TypeToken<Map<String, Integer>>() {}.getType();
                Map<String, Integer> params = gson.fromJson(payload, type);
                int targetUserId = params.get("targetUserId");

                if (targetUserId == currentUserId) {
                    return new Response(false, "Cannot block yourself.", null);
                }
                if (!userDao.getUserById(targetUserId).isPresent()) {
                    return new Response(false, "Target user not found.", null);
                }

                orgs.dao.BlockedUserDao blockedUserDao = new orgs.dao.BlockedUserDao();
                orgs.model.BlockedUser blockedUser = new orgs.model.BlockedUser();
                blockedUser.setBlockerId(currentUserId);
                blockedUser.setBlockedId(targetUserId);
                blockedUser.setBlockedAt(LocalDateTime.now());

                int id = blockedUserDao.createBlockedUser(blockedUser);
                if (id != -1) {
                    return new Response(true, "User blocked successfully!", null);
                } else {
                    return new Response(false, "Failed to block user (possibly already blocked).", null);
                }
            } catch (Exception e) {
                System.err.println("Error blocking user: " + e.getMessage());
                return new Response(false, "Server error blocking user.", null);
            }
        }

        private Response handleUnblockUser(String payload) {
            try {
                Type type = new TypeToken<Map<String, Integer>>() {}.getType();
                Map<String, Integer> params = gson.fromJson(payload, type);
                int targetUserId = params.get("targetUserId");

                orgs.dao.BlockedUserDao blockedUserDao = new orgs.dao.BlockedUserDao();
                boolean success = blockedUserDao.deleteBlockedUser(currentUserId, targetUserId);
                if (success) {
                    return new Response(true, "User unblocked successfully!", null);
                } else {
                    return new Response(false, "Failed to unblock user (not found or error).", null);
                }
            } catch (Exception e) {
                System.err.println("Error unblocking user: " + e.getMessage());
                return new Response(false, "Server error unblocking user.", null);
            }
        }


        // --- New Command Implementations (Notification Management) ---

        private Response handleGetUserNotifications() {
            try {
                orgs.dao.NotificationDao notificationDao = new orgs.dao.NotificationDao();
                List<orgs.model.Notification> notifications = notificationDao.getNotificationsByUserId(currentUserId);
                return new Response(true, "Notifications retrieved.", gson.toJson(notifications));
            } catch (Exception e) {
                System.err.println("Error getting user notifications: " + e.getMessage());
                return new Response(false, "Server error retrieving notifications.", null);
            }
        }

        private Response handleMarkNotificationAsRead(String payload) {
            try {
                Type type = new TypeToken<Map<String, Integer>>() {}.getType();
                Map<String, Integer> params = gson.fromJson(payload, type);
                int notificationId = params.get("notificationId");

                orgs.dao.NotificationDao notificationDao = new orgs.dao.NotificationDao();
                Optional<orgs.model.Notification> notificationOptional = notificationDao.getNotificationById(notificationId);

                if (!notificationOptional.isPresent() || notificationOptional.get().getRecipientUserId() != currentUserId) {
                    return new Response(false, "Notification not found or unauthorized.", null);
                }

                boolean success = notificationDao.markNotificationAsRead(notificationId);
                if (success) {
                    return new Response(true, "Notification marked as read.", null);
                } else {
                    return new Response(false, "Failed to mark notification as read.", null);
                }
            } catch (Exception e) {
                System.err.println("Error marking notification as read: " + e.getMessage());
                return new Response(false, "Server error marking notification as read.", null);
            }
        }

        private Response handleDeleteNotification(String payload) {
            try {
                Type type = new TypeToken<Map<String, Integer>>() {}.getType();
                Map<String, Integer> params = gson.fromJson(payload, type);
                int notificationId = params.get("notificationId");

                orgs.dao.NotificationDao notificationDao = new orgs.dao.NotificationDao();
                Optional<orgs.model.Notification> notificationOptional = notificationDao.getNotificationById(notificationId);

                if (!notificationOptional.isPresent() || notificationOptional.get().getRecipientUserId() != currentUserId) {
                    return new Response(false, "Notification not found or unauthorized.", null);
                }

                boolean success = notificationDao.deleteNotification(notificationId);
                if (success) {
                    return new Response(true, "Notification deleted successfully.", null);
                } else {
                    return new Response(false, "Failed to delete notification.", null);
                }
            } catch (Exception e) {
                System.err.println("Error deleting notification: " + e.getMessage());
                return new Response(false, "Server error deleting notification.", null);
            }
        }


        private Response handleLogin(String payload) {
            // Payload should contain username and password (or phone_number and password)
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> loginData = gson.fromJson(payload, type);
            String username = loginData.get("username");
            String password = loginData.get("password"); // This should be a hashed password for real apps!

            Optional<User> userOptional = userDao.getUserByUsername(username);
            System.out.println("user : " + userOptional.toString());
            if (userOptional.isPresent()) {
                User user = userOptional.get();
                // In a real app, you'd verify hashed password:
                // if (PasswordHasher.verify(password, user.getPassword())) {
                if (user.getPassword().equals(password)) { // Simplified for demonstration
                    this.currentUserId = user.getId();
                    loggedInUsers.put(currentUserId, this);
                    userDao.updateUserOnlineStatus(currentUserId, true); // Mark user online
                    return new Response(true, "Login successful!", gson.toJson(user));
                } else {
                    return new Response(false, "Invalid username or password p.", null);
                }
            } else {
                return new Response(false, "Invalid username or password u.", null);
            }
        }

        private Response handleSendMessage(String payload) {
            if (currentUserId == -1) {
                return new Response(false, "Not logged in.", null);
            }

            try {
                Message message = gson.fromJson(payload, Message.class);
                message.setSenderId(currentUserId);
                message.setSentAt(LocalDateTime.now());
                message.setMessageType("text"); // Assuming text for this basic example
                message.setDeleted(false);
                message.setViewCount(0);

                int messageId = messageDao.createMessage(message);
                if (messageId != -1) {
                    message.setId(messageId); // Set the generated ID

                    // Simplified: Broadcast to all active handlers who are participants in the chat
                    // In a real system, you'd manage chat memberships and only send to relevant participants
                    loggedInUsers.forEach((userId, handler) -> {
                        // Check if the user is a participant in 'message.getChatId()'
                        // For simplicity, let's just assume all logged in users might see it if it's a "public" demo
                        // Or, better, fetch chat participants and iterate over them.
                        // For now, if the sender is sending to themselves, or just for demo:
                        // This is where you'd implement message delivery to other clients based on chat_id
                        // Example: A more advanced design would have a ChatService that notifies participants
                        if (handler.currentUserId != currentUserId) { // Don't send back to self (unless echo is desired)
                            handler.out.println(new Response(true, "New message received", gson.toJson(message)).toJson());
                        }
                    });

                    return new Response(true, "Message sent successfully!", gson.toJson(message));
                } else {
                    return new Response(false, "Failed to send message.", null);
                }
            } catch (Exception e) {
                System.err.println("Error handling send message: " + e.getMessage());
                e.printStackTrace();
                return new Response(false, "Server error sending message.", null);
            }
        }

        private Response handleGetChatMessages(String payload) {
            if (currentUserId == -1) {
                return new Response(false, "Not logged in.", null);
            }
            try {
                Type type = new TypeToken<Map<String, Integer>>() {}.getType();
                Map<String, Integer> params = gson.fromJson(payload, type);
                int chatId = params.get("chatId");
                int limit = params.getOrDefault("limit", 50); // Default to 50 messages

                List<Message> messages = messageDao.getMessagesByChatId(chatId, limit);
                // Return messages to client
                return new Response(true, "Messages retrieved successfully.", gson.toJson(messages));
            } catch (Exception e) {
                System.err.println("Error handling get chat messages: " + e.getMessage());
                e.printStackTrace();
                return new Response(false, "Server error retrieving messages.", null);
            }
        }

        private Response handleCreateChat(String payload) {
            if (currentUserId == -1) {
                return new Response(false, "Not logged in.", null);
            }
            try {
                // For simplicity, let's assume payload is a Chat object, but it could be more complex
                // (e.g., chat name, type, and list of initial participant user IDs)
                // This example assumes a simple private chat creation for now
                // In a real app, you'd also create entries in chat_participants
                // For a private chat, you'd likely ensure it doesn't already exist between two users
                orgs.model.Chat newChat = gson.fromJson(payload, orgs.model.Chat.class);
                newChat.setCreatorId(currentUserId); // Set the creator
                //newChat.setChatType("private"); // Example: assume private chat for simplicity

                int chatId = new orgs.dao.ChatDao().createChat(newChat);
                if (chatId != -1) {
                    newChat.setId(chatId);
                    // Also add creator as a participant
                    orgs.model.ChatParticipant creatorParticipant = new orgs.model.ChatParticipant();
                    creatorParticipant.setChatId(chatId);
                    creatorParticipant.setUserId(currentUserId);
                    creatorParticipant.setRole("creator");
                    new orgs.dao.ChatParticipantDao().createChatParticipant(creatorParticipant);

                    return new Response(true, "Chat created successfully!", gson.toJson(newChat));
                } else {
                    return new Response(false, "Failed to create chat.", null);
                }
            } catch (Exception e) {
                System.err.println("Error creating chat: " + e.getMessage());
                e.printStackTrace();
                return new Response(false, "Server error creating chat.", null);
            }
        }
    }

    public static void main(String[] args) {
        ChatServer server = new ChatServer();
        server.start();
    }
}