package orgs.models2;

import orgs.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserSetting {
    // userSettingId is removed as userId is the primary key in the DB table
    private Long userId; // This is the PRIMARY KEY in your DB schema
    private String privacyPhoneNumber;
    private String privacyLastSeen;
    private String privacyProfilePhoto;
    // Removed fields not present in the MySQL schema:
    // private String privacyCalls;
    // private String privacyForwardedMessages;
    private String privacyGroupsAndChannels;
    private Boolean notificationsPrivateChats;
    private Boolean notificationsGroupChats;
    private Boolean notificationsChannels;
    // Removed fields not present in the MySQL schema:
    // private String notificationSound;
    // private String chatTheme;
    // private Integer chatTextSize;
    // private Timestamp updatedAt; // Removed as MySQL table does not have this column

    // Constructor for fetching/identifying
    public UserSetting(Long userId) {
        this.userId = userId;
    }

    // Full constructor matching the MySQL schema
    public UserSetting(Long userId, String privacyPhoneNumber, String privacyLastSeen, String privacyProfilePhoto,
                       String privacyGroupsAndChannels, Boolean notificationsPrivateChats,
                       Boolean notificationsGroupChats, Boolean notificationsChannels) {
        this.userId = userId;
        this.privacyPhoneNumber = privacyPhoneNumber;
        this.privacyLastSeen = privacyLastSeen;
        this.privacyProfilePhoto = privacyProfilePhoto;
        this.privacyGroupsAndChannels = privacyGroupsAndChannels;
        this.notificationsPrivateChats = notificationsPrivateChats;
        this.notificationsGroupChats = notificationsGroupChats;
        this.notificationsChannels = notificationsChannels;
    }

    // --- Getters and Setters (keep all existing ones, adjusting for removed fields) ---
    // You'll need to regenerate or manually update your IDE's getters/setters for the remaining fields
    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getPrivacyPhoneNumber() {
        return privacyPhoneNumber;
    }

    public void setPrivacyPhoneNumber(String privacyPhoneNumber) {
        this.privacyPhoneNumber = privacyPhoneNumber;
    }

    public String getPrivacyLastSeen() {
        return privacyLastSeen;
    }

    public void setPrivacyLastSeen(String privacyLastSeen) {
        this.privacyLastSeen = privacyLastSeen;
    }

    public String getPrivacyProfilePhoto() {
        return privacyProfilePhoto;
    }

    public void setPrivacyProfilePhoto(String privacyProfilePhoto) {
        this.privacyProfilePhoto = privacyProfilePhoto;
    }

    public String getPrivacyGroupsAndChannels() {
        return privacyGroupsAndChannels;
    }

    public void setPrivacyGroupsAndChannels(String privacyGroupsAndChannels) {
        this.privacyGroupsAndChannels = privacyGroupsAndChannels;
    }

    public Boolean getNotificationsPrivateChats() {
        return notificationsPrivateChats;
    }

    public void setNotificationsPrivateChats(Boolean notificationsPrivateChats) {
        this.notificationsPrivateChats = notificationsPrivateChats;
    }

    public Boolean getNotificationsGroupChats() {
        return notificationsGroupChats;
    }

    public void setNotificationsGroupChats(Boolean notificationsGroupChats) {
        this.notificationsGroupChats = notificationsGroupChats;
    }

    public Boolean getNotificationsChannels() {
        return notificationsChannels;
    }

    public void setNotificationsChannels(Boolean notificationsChannels) {
        this.notificationsChannels = notificationsChannels;
    }
    // --- End Getters and Setters ---


    /**
     * Saves a new user setting record to the database.
     * Since `user_id` is the primary key, this acts as an INSERT.
     * If a record for the `userId` already exists, this will likely throw a `SQLException`
     * due to a duplicate primary key constraint. Consider `INSERT ... ON DUPLICATE KEY UPDATE`
     * or checking for existence before saving for upsert functionality.
     *
     * @return true if the record was successfully inserted, false otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public boolean save() throws SQLException {
        // Updated SQL query to match the columns in your MySQL schema
        String sql = "INSERT INTO user_settings (user_id, privacy_phone_number, privacy_last_seen, privacy_profile_photo, " +
                "privacy_groups_and_channels, notifications_private_chats, notifications_group_chats, notifications_channels) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        // Use your MySQL DatabaseConnection singleton
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) { // No RETURN_GENERATED_KEYS needed for PK 'user_id'

            statement.setLong(1, userId);
            statement.setString(2, privacyPhoneNumber);
            statement.setString(3, privacyLastSeen);
            statement.setString(4, privacyProfilePhoto);
            statement.setString(5, privacyGroupsAndChannels);
            statement.setBoolean(6, notificationsPrivateChats);
            statement.setBoolean(7, notificationsGroupChats);
            statement.setBoolean(8, notificationsChannels);

            boolean isInserted = statement.executeUpdate() > 0;
            // No generated keys to retrieve as userId is the primary key we're setting
            return isInserted;
        }
    }

    /**
     * Updates an existing user setting record in the database.
     * It uses the `userId` to identify the record to update.
     *
     * @return true if the record was successfully updated, false otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public boolean update() throws SQLException {
        // Updated SQL query to match the columns in your MySQL schema
        String sql = "UPDATE user_settings SET " +
                "privacy_phone_number = ?, privacy_last_seen = ?, privacy_profile_photo = ?, " +
                "privacy_groups_and_channels = ?, notifications_private_chats = ?, notifications_group_chats = ?, " +
                "notifications_channels = ? " +
                "WHERE user_id = ?"; // WHERE clause uses user_id as PK

        // Use your MySQL DatabaseConnection singleton
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, privacyPhoneNumber);
            statement.setString(2, privacyLastSeen);
            statement.setString(3, privacyProfilePhoto);
            statement.setString(4, privacyGroupsAndChannels);
            statement.setBoolean(5, notificationsPrivateChats);
            statement.setBoolean(6, notificationsGroupChats);
            statement.setBoolean(7, notificationsChannels);
            statement.setLong(8, userId); // Set the user_id for the WHERE clause

            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Deletes a user setting record from the database.
     * It uses the `userId` to identify the record to delete.
     *
     * @return true if the record was successfully deleted, false otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public boolean delete() throws SQLException {
        String sql = "DELETE FROM user_settings WHERE user_id = ?"; // WHERE clause uses user_id as PK

        // Use your MySQL DatabaseConnection singleton
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, userId); // Set the user_id for the WHERE clause
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Retrieves user settings from the database for the given userId.
     *
     * @return A UserSetting object populated with data, or null if not found.
     * @throws SQLException if a database access error occurs.
     */
    public static UserSetting getByUserId(Long userId) throws SQLException {
        String sql = "SELECT user_id, privacy_phone_number, privacy_last_seen, privacy_profile_photo, " +
                "privacy_groups_and_channels, notifications_private_chats, notifications_group_chats, notifications_channels " +
                "FROM user_settings WHERE user_id = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, userId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new UserSetting(
                            resultSet.getLong("user_id"),
                            resultSet.getString("privacy_phone_number"),
                            resultSet.getString("privacy_last_seen"),
                            resultSet.getString("privacy_profile_photo"),
                            resultSet.getString("privacy_groups_and_channels"),
                            resultSet.getBoolean("notifications_private_chats"),
                            resultSet.getBoolean("notifications_group_chats"),
                            resultSet.getBoolean("notifications_channels")
                    );
                }
            }
        }
        return null; // Return null if no settings found for the user_id
    }
}