package orgs.models2;

import orgs.utils.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ChatParticipant {
    private Long chatParticipantId; // Maps to 'id' INT AUTO_INCREMENT PRIMARY KEY in MySQL
    private Long chatId;            // Maps to 'chat_id' INT
    private Long userId;            // Maps to 'user_id' INT
    private ChatParticipantRole role; // Maps to 'role' ENUM
    private Timestamp joinedAt;     // Maps to 'joined_at' TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    // private Timestamp mutedUntil; // NOT in MySQL schema
    // private boolean isPinned;     // NOT in MySQL schema
    private int unreadCount;
    private Long lastReadMessageId; // Maps to 'last_read_message_id' INT (nullable)

    // Enum matching your MySQL ENUM('member', 'admin', 'creator', 'subscriber')
    public enum ChatParticipantRole {
        MEMBER, ADMIN, CREATOR, SUBSCRIBER; // Changed MODERATOR to SUBSCRIBER to match DB schema

        public static ChatParticipantRole fromString(String value) {
            for (ChatParticipantRole role : ChatParticipantRole.values()) {
                if (role.name().equalsIgnoreCase(value)) {
                    return role;
                }
            }
            throw new IllegalArgumentException("Invalid ChatParticipantRole: " + value);
        }
    }

    public ChatParticipant() {}

    // Constructor for creating a new participant (ID and joinedAt handled by DB)
    public ChatParticipant(Long chatId, Long userId) {
        this.chatId = chatId;
        this.userId = userId;
        this.role = ChatParticipantRole.MEMBER; // Default role as per DB schema
        this.unreadCount = 0; // Default unread count
        // joinedAt and lastReadMessageId (null) handled by DB or default
    }

    // Full constructor for retrieving from DB
    public ChatParticipant(Long chatParticipantId, Long chatId, Long userId, ChatParticipantRole role, Timestamp joinedAt, int unreadCount, Long lastReadMessageId) {
        this.chatParticipantId = chatParticipantId;
        this.chatId = chatId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = joinedAt;
        // this.mutedUntil = mutedUntil; // Not in DB schema
        // this.isPinned = isPinned;     // Not in DB schema
        this.unreadCount = unreadCount;
        this.lastReadMessageId = lastReadMessageId;
    }

    // --- Getters and Setters ---
    public Long getChatParticipantId() { return chatParticipantId; }
    public void setChatParticipantId(Long chatParticipantId) { this.chatParticipantId = chatParticipantId; }
    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public ChatParticipantRole getRole() { return role; }
    public void setRole(ChatParticipantRole role) { this.role = role; }
    public Timestamp getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Timestamp joinedAt) { this.joinedAt = joinedAt; }
    // public Timestamp getMutedUntil() { return mutedUntil; } // Not in DB schema
    // public void setMutedUntil(Timestamp mutedUntil) { this.mutedUntil = mutedUntil; }
    // public boolean isPinned() { return isPinned; } // Not in DB schema
    // public void setPinned(boolean pinned) { isPinned = pinned; }
    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }
    public Long getLastReadMessageId() { return lastReadMessageId; }
    public void setLastReadMessageId(Long lastReadMessageId) { this.lastReadMessageId = lastReadMessageId; }

    // --- Database Operations ---

    /**
     * Saves a new chat participant record to the database.
     * The `id` and `joined_at` are handled by the database.
     *
     * @return true if the participant was successfully inserted, false otherwise.
     * @throws SQLException if a database access error occurs (e.g., duplicate participant entry).
     */
    public boolean save() throws SQLException {
        // SQL matching MySQL 'chat_participants' table columns
        // 'joined_at' is omitted as it's handled by DB default
        String sql = "INSERT INTO chat_participants (chat_id, user_id, role, unread_count, last_read_message_id) VALUES (?, ?, ?, ?, ?)";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setLong(1, chatId);
            statement.setLong(2, userId);
            statement.setString(3, role != null ? role.name().toLowerCase() : ChatParticipantRole.MEMBER.name().toLowerCase());
            statement.setInt(4, unreadCount);
            statement.setObject(5, lastReadMessageId); // Use setObject for nullable Long

            boolean isInserted = statement.executeUpdate() > 0;
            if (isInserted) {
                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        this.chatParticipantId = generatedKeys.getLong(1); // Set the auto-generated ID
                        // Optionally, fetch joinedAt if needed:
                        // this.setJoinedAt(ChatParticipant.findById(this.chatParticipantId).getJoinedAt());
                    }
                }
            }
            return isInserted;
        }
    }

    /**
     * Updates an existing chat participant record in the database.
     * It identifies the record using the `chatParticipantId`.
     *
     * @return true if the participant was successfully updated, false otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public boolean update() throws SQLException {
        // SQL matching MySQL 'chat_participants' table columns, using 'id' for WHERE clause
        String sql = "UPDATE chat_participants SET role = ?, unread_count = ?, last_read_message_id = ? WHERE id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, role != null ? role.name().toLowerCase() : ChatParticipantRole.MEMBER.name().toLowerCase());
            statement.setInt(2, unreadCount);
            statement.setObject(3, lastReadMessageId); // Use setObject for nullable Long
            statement.setLong(4, chatParticipantId); // Use chatParticipantId for the WHERE clause (maps to 'id' in DB)

            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Deletes a chat participant record from the database by its ID.
     *
     * @return true if the participant was successfully deleted, false otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public boolean delete() throws SQLException {
        String sql = "DELETE FROM chat_participants WHERE id = ?"; // Use 'id' as per MySQL schema
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, chatParticipantId); // Use chatParticipantId for the WHERE clause (maps to 'id' in DB)
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Retrieves a chat participant record by its unique ID.
     *
     * @param id The ID of the chat participant record.
     * @return A ChatParticipant object if found, null otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public static ChatParticipant findById(Long id) throws SQLException {
        String sql = "SELECT id, chat_id, user_id, role, unread_count, last_read_message_id, joined_at " +
                "FROM chat_participants WHERE id = ?";
        return executeQueryAndBuildChatParticipant(sql, id);
    }

    /**
     * Retrieves a specific chat participant entry by chat ID and user ID.
     * This is useful due to the UNIQUE (chat_id, user_id) constraint.
     *
     * @param chatId The ID of the chat.
     * @param userId The ID of the user.
     * @return A ChatParticipant object if found, null otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public static ChatParticipant findByChatIdAndUserId(Long chatId, Long userId) throws SQLException {
        String sql = "SELECT id, chat_id, user_id, role, unread_count, last_read_message_id, joined_at " +
                "FROM chat_participants WHERE chat_id = ? AND user_id = ?";
        return executeQueryAndBuildChatParticipant(sql, chatId, userId);
    }

    /**
     * Retrieves all participants for a given chat ID.
     *
     * @param chatId The ID of the chat.
     * @return A List of ChatParticipant objects.
     * @throws SQLException if a database access error occurs.
     */
    public static List<ChatParticipant> findByChatId(Long chatId) throws SQLException {
        List<ChatParticipant> participants = new ArrayList<>();
        String sql = "SELECT id, chat_id, user_id, role, unread_count, last_read_message_id, joined_at " +
                "FROM chat_participants WHERE chat_id = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, chatId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    participants.add(new ChatParticipant(
                            resultSet.getLong("id"),
                            resultSet.getLong("chat_id"),
                            resultSet.getLong("user_id"),
                            ChatParticipantRole.fromString(resultSet.getString("role")),
                            resultSet.getTimestamp("joined_at"),
                            resultSet.getInt("unread_count"),
                            resultSet.getLong("last_read_message_id")
                    ));
                }
            }
        }
        return participants;
    }

    /**
     * Helper method to execute a query and build a ChatParticipant object from the ResultSet.
     *
     * @param sql The SQL query to execute.
     * @param params The parameters for the PreparedStatement.
     * @return A ChatParticipant object or null.
     * @throws SQLException if a database access error occurs.
     */
    private static ChatParticipant executeQueryAndBuildChatParticipant(String sql, Object... params) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                if (params[i] instanceof Long) {
                    statement.setLong(i + 1, (Long) params[i]);
                } else if (params[i] instanceof String) {
                    statement.setString(i + 1, (String) params[i]);
                }
                // Add other types if necessary
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new ChatParticipant(
                            resultSet.getLong("id"),
                            resultSet.getLong("chat_id"),
                            resultSet.getLong("user_id"),
                            ChatParticipantRole.fromString(resultSet.getString("role")),
                            resultSet.getTimestamp("joined_at"),
                            resultSet.getInt("unread_count"),
                            resultSet.getLong("last_read_message_id")
                    );
                }
            }
        }
        return null;
    }
}