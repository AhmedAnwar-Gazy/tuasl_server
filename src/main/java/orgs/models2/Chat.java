package orgs.models2;

import orgs.utils.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Chat {
    private Long chatId; // Maps to 'id' INT AUTO_INCREMENT PRIMARY KEY in MySQL
    private ChatType chatType;
    private String chatName;
    private String chatDescription;
    private String chatPictureUrl;
    private Long creatorUserId; // Maps to 'creator_id' INT
    private String publicLink;
    private Timestamp createdAt; // Maps to 'created_at' TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    private Timestamp updatedAt; // Maps to 'updated_at' TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP

    public enum ChatType {
        PRIVATE, GROUP, CHANNEL;

        public static ChatType fromString(String value) {
            for (ChatType type : ChatType.values()) {
                if (type.name().equalsIgnoreCase(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Invalid ChatType: " + value);
        }
    }

    public Chat() {}

    // Constructor for creating a new chat (ID and timestamps handled by DB)
    public Chat(ChatType chatType, String chatName, String chatDescription, String chatPictureUrl, Long creatorUserId, String publicLink) {
        this.chatType = chatType;
        this.chatName = chatName;
        this.chatDescription = chatDescription;
        this.chatPictureUrl = chatPictureUrl;
        this.creatorUserId = creatorUserId;
        this.publicLink = publicLink;
        // createdAt and updatedAt are handled by the database
    }

    // Full constructor for retrieving existing chat data from DB
    public Chat(Long chatId, ChatType chatType, String chatName, String chatDescription, String chatPictureUrl, Long creatorUserId, String publicLink, Timestamp createdAt, Timestamp updatedAt) {
        this.chatId = chatId;
        this.chatType = chatType;
        this.chatName = chatName;
        this.chatDescription = chatDescription;
        this.chatPictureUrl = chatPictureUrl;
        this.creatorUserId = creatorUserId;
        this.publicLink = publicLink;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // --- Getters and Setters ---
    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }
    public ChatType getChatType() { return chatType; }
    public void setChatType(ChatType chatType) { this.chatType = chatType; }
    public String getChatName() { return chatName; }
    public void setChatName(String chatName) { this.chatName = chatName; }
    public String getChatDescription() { return chatDescription; }
    public void setChatDescription(String chatDescription) { this.chatDescription = chatDescription; }
    public String getChatPictureUrl() { return chatPictureUrl; }
    public void setChatPictureUrl(String chatPictureUrl) { this.chatPictureUrl = chatPictureUrl; }
    public Long getCreatorUserId() { return creatorUserId; }
    public void setCreatorUserId(Long creatorUserId) { this.creatorUserId = creatorUserId; }
    public String getPublicLink() { return publicLink; }
    public void setPublicLink(String publicLink) { this.publicLink = publicLink; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    // --- Database Operations ---

    /**
     * Saves a new chat record to the database.
     * The `id`, `created_at`, and `updated_at` timestamps are handled by the database.
     *
     * @return true if the chat was successfully inserted, false otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public boolean save() throws SQLException {
        // Removed 'created_at' and 'updated_at' from INSERT statement as they are handled by DB defaults
        String sql = "INSERT INTO chats (chat_type, chat_name, chat_description, chat_picture_url, creator_id, public_link) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, chatType.name().toLowerCase());
            statement.setString(2, chatName);
            statement.setString(3, chatDescription);
            statement.setString(4, chatPictureUrl);
            statement.setLong(5, creatorUserId);
            statement.setString(6, publicLink);

            boolean isInserted = statement.executeUpdate() > 0;
            if (isInserted) {
                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        this.chatId = generatedKeys.getLong(1); // Set the auto-generated ID
                        // Optionally, fetch createdAt and updatedAt if needed immediately:
                        // Chat fetchedChat = Chat.findById(this.chatId);
                        // this.setCreatedAt(fetchedChat.getCreatedAt());
                        // this.setUpdatedAt(fetchedChat.getUpdatedAt());
                    }
                }
            }
            return isInserted;
        }
    }

    /**
     * Updates an existing chat record in the database.
     * The `updated_at` timestamp is handled automatically by the database.
     *
     * @return true if the chat was successfully updated, false otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public boolean update() throws SQLException {
        // Removed 'updated_at' from UPDATE statement as it's handled by DB's ON UPDATE CURRENT_TIMESTAMP
        String sql = "UPDATE chats SET chat_type = ?, chat_name = ?, chat_description = ?, chat_picture_url = ?, creator_id = ?, public_link = ? WHERE id = ?"; // Use 'id' as per MySQL schema
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, chatType.name().toLowerCase());
            statement.setString(2, chatName);
            statement.setString(3, chatDescription);
            statement.setString(4, chatPictureUrl);
            statement.setLong(5, creatorUserId);
            statement.setString(6, publicLink);
            statement.setLong(7, chatId); // Use chatId for the WHERE clause (maps to 'id' in DB)

            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Deletes a chat record from the database by its ID.
     *
     * @return true if the chat was successfully deleted, false otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public boolean delete() throws SQLException {
        String sql = "DELETE FROM chats WHERE id = ?"; // Use 'id' as per MySQL schema
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, chatId); // Use chatId for the WHERE clause (maps to 'id' in DB)
            return statement.executeUpdate() > 0;
        }
    }

    // --- Retrieval Methods ---

    /**
     * Retrieves a chat by its unique ID.
     *
     * @param id The ID of the chat.
     * @return A Chat object if found, null otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public static Chat findById(Long id) throws SQLException {
        String sql = "SELECT id, chat_type, chat_name, chat_description, chat_picture_url, creator_id, public_link, created_at, updated_at " +
                "FROM chats WHERE id = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new Chat(
                            resultSet.getLong("id"),
                            ChatType.fromString(resultSet.getString("chat_type")),
                            resultSet.getString("chat_name"),
                            resultSet.getString("chat_description"),
                            resultSet.getString("chat_picture_url"),
                            resultSet.getLong("creator_id"),
                            resultSet.getString("public_link"),
                            resultSet.getTimestamp("created_at"),
                            resultSet.getTimestamp("updated_at")
                    );
                }
            }
        }
        return null;
    }

    /**
     * Retrieves chats by their type.
     *
     * @param chatType The type of chat (PRIVATE, GROUP, CHANNEL).
     * @return A list of Chat objects matching the given type.
     * @throws SQLException if a database access error occurs.
     */
    public static List<Chat> findByChatType(ChatType chatType) throws SQLException {
        List<Chat> chats = new ArrayList<>();
        String sql = "SELECT id, chat_type, chat_name, chat_description, chat_picture_url, creator_id, public_link, created_at, updated_at " +
                "FROM chats WHERE chat_type = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, chatType.name().toLowerCase());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    chats.add(new Chat(
                            resultSet.getLong("id"),
                            ChatType.fromString(resultSet.getString("chat_type")),
                            resultSet.getString("chat_name"),
                            resultSet.getString("chat_description"),
                            resultSet.getString("chat_picture_url"),
                            resultSet.getLong("creator_id"),
                            resultSet.getString("public_link"),
                            resultSet.getTimestamp("created_at"),
                            resultSet.getTimestamp("updated_at")
                    ));
                }
            }
        }
        return chats;
    }

    /**
     * Retrieves a chat by its public link.
     *
     * @param publicLink The public link of the chat.
     * @return A Chat object if found, null otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public static Chat findByPublicLink(String publicLink) throws SQLException {
        String sql = "SELECT id, chat_type, chat_name, chat_description, chat_picture_url, creator_id, public_link, created_at, updated_at " +
                "FROM chats WHERE public_link = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, publicLink);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new Chat(
                            resultSet.getLong("id"),
                            ChatType.fromString(resultSet.getString("chat_type")),
                            resultSet.getString("chat_name"),
                            resultSet.getString("chat_description"),
                            resultSet.getString("chat_picture_url"),
                            resultSet.getLong("creator_id"),
                            resultSet.getString("public_link"),
                            resultSet.getTimestamp("created_at"),
                            resultSet.getTimestamp("updated_at")
                    );
                }
            }
        }
        return null;
    }
}