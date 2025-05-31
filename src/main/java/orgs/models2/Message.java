package orgs.models2;

import orgs.utils.DatabaseConnection;

import java.sql.*;

public class Message {
    private Long messageId; // Maps to 'id' INT AUTO_INCREMENT PRIMARY KEY in MySQL
    private Long chatId;
    private Long senderUserId;
    private String senderName; // This field is in Java class but NOT a column in your MySQL 'messages' table
    private String messageType;
    private String content;
    private Long mediaId;
    private Long repliedToMessageId;
    private Long forwardedFromUserId;
    private Long forwardedFromChatId;
    private Timestamp sentAt;      // Managed by DB default for INSERT
    private Timestamp editedAt;
    private Boolean isDeleted;
    private Integer viewCount;

    public Message(Long messageId) {
        this.messageId = messageId;
    }
    public Message(String content) {
        this.content = content;
    }

    // Constructor matching the MySQL schema for saving new messages
    public Message(Long chatId, Long senderUserId, String messageType, String content,
                   Long mediaId, Long repliedToMessageId, Long forwardedFromUserId,
                   Long forwardedFromChatId, Timestamp editedAt, Boolean isDeleted, Integer viewCount) {
        this.chatId = chatId;
        this.senderUserId = senderUserId;
        this.messageType = messageType;
        this.content = content;
        this.mediaId = mediaId;
        this.repliedToMessageId = repliedToMessageId;
        this.forwardedFromUserId = forwardedFromUserId;
        this.forwardedFromChatId = forwardedFromChatId;
        // sentAt is handled by the database
        this.editedAt = editedAt;
        this.isDeleted = isDeleted;
        this.viewCount = viewCount;
    }

    // Full constructor for retrieving messages from the database
    public Message(Long messageId, Long chatId, Long senderUserId, String messageType, String content, Long mediaId,
                   Long repliedToMessageId, Long forwardedFromUserId, Long forwardedFromChatId,
                   Timestamp sentAt, Timestamp editedAt, Boolean isDeleted, Integer viewCount) {
        this.messageId = messageId;
        this.chatId = chatId;
        this.senderUserId = senderUserId;
        this.messageType = messageType;
        this.content = content;
        this.mediaId = mediaId;
        this.repliedToMessageId = repliedToMessageId;
        this.forwardedFromUserId = forwardedFromUserId;
        this.forwardedFromChatId = forwardedFromChatId;
        this.sentAt = sentAt;
        this.editedAt = editedAt;
        this.isDeleted = isDeleted;
        this.viewCount = viewCount;
    }

    // --- Getters and Setters ---
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }

    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }

    public Long getSenderUserId() { return senderUserId; }
    public void setSenderUserId(Long senderUserId) { this.senderUserId = senderUserId; }

    public String getSenderName() { return senderName; } // This field is not mapped to a column in DB
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Long getMediaId() { return mediaId; }
    public void setMediaId(Long mediaId) { this.mediaId = mediaId; }

    public Long getRepliedToMessageId() { return repliedToMessageId; }
    public void setRepliedToMessageId(Long repliedToMessageId) { this.repliedToMessageId = repliedToMessageId; }

    public Long getForwardedFromUserId() { return forwardedFromUserId; }
    public void setForwardedFromUserId(Long forwardedFromUserId) { this.forwardedFromUserId = forwardedFromUserId; }

    public Long getForwardedFromChatId() { return forwardedFromChatId; }
    public void setForwardedFromChatId(Long forwardedFromChatId) { this.forwardedFromChatId = forwardedFromChatId; }

    public Timestamp getSentAt() { return sentAt; }
    public void setSentAt(Timestamp sentAt) { this.sentAt = sentAt; }

    public Timestamp getEditedAt() { return editedAt; }
    public void setEditedAt(Timestamp editedAt) { this.editedAt = editedAt; }

    public Boolean getDeleted() { return isDeleted; }
    public void setDeleted(Boolean deleted) { isDeleted = deleted; }

    public Integer getViewCount() { return viewCount; }
    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }

    // --- Database Operations ---

    /**
     * Saves a new message record to the database.
     * The `sent_at` timestamp is automatically handled by the database's `DEFAULT CURRENT_TIMESTAMP`.
     *
     * @return true if the message was successfully inserted, false otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public boolean save() throws SQLException {
        // Removed 'sent_at' from the INSERT statement as it's handled by DB default
        String sql = "INSERT INTO messages (chat_id, sender_id, message_type, content, media_id, replied_to_message_id, " +
                "forwarded_from_user_id, forwarded_from_chat_id, edited_at, is_deleted, view_count) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setLong(1, chatId);
            statement.setLong(2, senderUserId);
            statement.setString(3, messageType);
            statement.setString(4, content);
            statement.setObject(5, mediaId); // Use setObject for nullable Long
            statement.setObject(6, repliedToMessageId); // Use setObject for nullable Long
            statement.setObject(7, forwardedFromUserId); // Use setObject for nullable Long
            statement.setObject(8, forwardedFromChatId); // Use setObject for nullable Long
            statement.setTimestamp(9, editedAt);
            statement.setBoolean(10, isDeleted != null ? isDeleted : false); // Ensure boolean value
            statement.setInt(11, viewCount != null ? viewCount : 0); // Ensure int value

            boolean isInserted = statement.executeUpdate() > 0;
            if (isInserted) {
                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        this.messageId = generatedKeys.getLong(1); // Set the auto-generated ID
                        // Optionally, fetch the sentAt from DB if needed immediately after insert
                        // But generally, the DB's default is reliable.
                    }
                }
            }
            return isInserted;
        }
    }

    /**
     * Updates an existing message record in the database.
     * This method focuses on fields that are typically editable after sending.
     *
     * @return true if the message was successfully updated, false otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public boolean update() throws SQLException {
        // Using 'id' for WHERE clause as per MySQL schema
        String sql = "UPDATE messages SET content = ?, edited_at = ?, is_deleted = ?, view_count = ? WHERE id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, content);
            statement.setTimestamp(2, editedAt);
            statement.setBoolean(3, isDeleted != null ? isDeleted : false);
            statement.setInt(4, viewCount != null ? viewCount : 0);
            statement.setLong(5, messageId); // Use messageId for the WHERE clause (which maps to 'id' in DB)

            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Deletes a message record from the database by its ID.
     * Note: Your schema uses `is_deleted BOOLEAN DEFAULT FALSE` for soft deletes.
     * A "hard delete" with this method removes the record entirely.
     * For soft delete, you would typically use an update to set `is_deleted = TRUE`.
     *
     * @return true if the message was successfully deleted, false otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public boolean delete() throws SQLException {
        // Using 'id' for WHERE clause as per MySQL schema
        String sql = "DELETE FROM messages WHERE id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, messageId); // Use messageId for the WHERE clause (which maps to 'id' in DB)
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Performs a soft delete by setting `is_deleted` to true and updating `edited_at`.
     *
     * @return true if the message was successfully marked as deleted, false otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public boolean softDelete() throws SQLException {
        String sql = "UPDATE messages SET is_deleted = TRUE, edited_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, messageId);
            this.setDeleted(true); // Update the object state
            this.setEditedAt(new Timestamp(System.currentTimeMillis())); // Update object timestamp
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Retrieves a message by its unique ID.
     *
     * @param id The ID of the message.
     * @return A Message object if found, null otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public static Message findById(Long id) throws SQLException {
        String sql = "SELECT id, chat_id, sender_id, message_type, content, media_id, " +
                "replied_to_message_id, forwarded_from_user_id, forwarded_from_chat_id, " +
                "sent_at, edited_at, is_deleted, view_count " +
                "FROM messages WHERE id = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new Message(
                            resultSet.getLong("id"),
                            resultSet.getLong("chat_id"),
                            resultSet.getLong("sender_id"), // Corresponds to sender_user_id
                            resultSet.getString("message_type"),
                            resultSet.getString("content"),
                            (Long) resultSet.getObject("media_id"), // Use getObject for nullable Long
                            (Long) resultSet.getObject("replied_to_message_id"),
                            (Long) resultSet.getObject("forwarded_from_user_id"),
                            (Long) resultSet.getObject("forwarded_from_chat_id"),
                            resultSet.getTimestamp("sent_at"),
                            resultSet.getTimestamp("edited_at"),
                            resultSet.getBoolean("is_deleted"),
                            resultSet.getInt("view_count")
                    );
                }
            }
        }
        return null;
    }

    /**
     * Retrieves messages for a specific chat, ordered by sent time descending.
     *
     * @param chatId The ID of the chat.
     * @param limit The maximum number of messages to retrieve.
     * @param offset The starting offset for pagination.
     * @return A List of Message objects.
     * @throws SQLException if a database access error occurs.
     */
    public static java.util.List<Message> findByChatId(Long chatId, int limit, int offset) throws SQLException {
        java.util.List<Message> messages = new java.util.ArrayList<>();
        String sql = "SELECT id, chat_id, sender_id, message_type, content, media_id, " +
                "replied_to_message_id, forwarded_from_user_id, forwarded_from_chat_id, " +
                "sent_at, edited_at, is_deleted, view_count " +
                "FROM messages WHERE chat_id = ? ORDER BY sent_at DESC LIMIT ? OFFSET ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, chatId);
            statement.setInt(2, limit);
            statement.setInt(3, offset);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    messages.add(new Message(
                            resultSet.getLong("id"),
                            resultSet.getLong("chat_id"),
                            resultSet.getLong("sender_id"),
                            resultSet.getString("message_type"),
                            resultSet.getString("content"),
                            (Long) resultSet.getObject("media_id"),
                            (Long) resultSet.getObject("replied_to_message_id"),
                            (Long) resultSet.getObject("forwarded_from_user_id"),
                            (Long) resultSet.getObject("forwarded_from_chat_id"),
                            resultSet.getTimestamp("sent_at"),
                            resultSet.getTimestamp("edited_at"),
                            resultSet.getBoolean("is_deleted"),
                            resultSet.getInt("view_count")
                    ));
                }
            }
        }
        return messages;
    }
}