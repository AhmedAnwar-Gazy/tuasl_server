package orgs.models2;

import orgs.utils.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BlockedUser {
    // No 'blockId' as the primary key is composite (blocker_id, blocked_id) in MySQL
    private Long blockerUserId; // Maps to 'blocker_id' INT NOT NULL PRIMARY KEY
    private Long blockedUserId; // Maps to 'blocked_id' INT NOT NULL PRIMARY KEY
    private Timestamp blockedAt; // Maps to 'blocked_at' TIMESTAMP DEFAULT CURRENT_TIMESTAMP

    // Constructor for creating a new block entry
    public BlockedUser(Long blockerUserId, Long blockedUserId) {
        this.blockerUserId = blockerUserId;
        this.blockedUserId = blockedUserId;
        // blockedAt is handled by DB default
    }

    // Constructor for retrieving from DB
    public BlockedUser(Long blockerUserId, Long blockedUserId, Timestamp blockedAt) {
        this.blockerUserId = blockerUserId;
        this.blockedUserId = blockedUserId;
        this.blockedAt = blockedAt;
    }

    // --- Getters and Setters ---
    // No getter/setter for blockId as it's not a direct column
    public Long getBlockerUserId() {
        return blockerUserId;
    }
    public void setBlockerUserId(Long blockerUserId) {
        this.blockerUserId = blockerUserId;
    }

    public Long getBlockedUserId() {
        return blockedUserId;
    }
    public void setBlockedUserId(Long blockedUserId) {
        this.blockedUserId = blockedUserId;
    }

    public Timestamp getBlockedAt() {
        return blockedAt;
    }
    public void setBlockedAt(Timestamp blockedAt) {
        this.blockedAt = blockedAt;
    }

    // --- Database Operations ---

    /**
     * Saves a new blocked user entry to the database.
     * The `blocked_at` timestamp is automatically handled by the database.
     * This operation relies on the composite primary key `(blocker_id, blocked_id)`
     * to prevent duplicate entries.
     *
     * @return true if the entry was successfully inserted, false otherwise.
     * @throws SQLException if a database access error occurs (e.g., trying to block the same user twice).
     */
    public boolean save() throws SQLException {
        // SQL matching MySQL 'blocked_users' table columns ('blocker_id', 'blocked_id', 'blocked_at')
        // 'blocked_at' is omitted as it's handled by DB default
        String sql = "INSERT INTO blocked_users (blocker_id, blocked_id) VALUES (?, ?)";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) { // No RETURN_GENERATED_KEYS for composite PK

            statement.setLong(1, this.blockerUserId);
            statement.setLong(2, this.blockedUserId);

            boolean isInserted = statement.executeUpdate() > 0;

            // If you need the exact blocked_at timestamp in the object after saving,
            // you'd typically fetch the record back from the DB:
            // if (isInserted) {
            //     BlockedUser fetched = BlockedUser.findByBlockerAndBlocked(this.blockerUserId, this.blockedUserId);
            //     if (fetched != null) {
            //         this.setBlockedAt(fetched.getBlockedAt());
            //     }
            // }
            return isInserted;
        }
    }

    /**
     * Updates a blocked user entry.
     * Note: Given the composite primary key, updating `blocker_id` or `blocked_id`
     * effectively means deleting the old entry and inserting a new one.
     * This `update` method is provided for completeness but might not be typically used
     * for this table's structure unless you're updating a different column (like `blocked_at`,
     * though it has a default, you might override it).
     *
     * @return true if the entry was successfully updated, false otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public boolean update() throws SQLException {
        // Updated to reflect the composite primary key in the WHERE clause
        String sql = "UPDATE blocked_users SET blocked_at = ? WHERE blocker_id = ? AND blocked_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setTimestamp(1, this.blockedAt); // Update blockedAt if explicitly set
            statement.setLong(2, this.blockerUserId);
            statement.setLong(3, this.blockedUserId);

            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Deletes a blocked user entry from the database.
     * It identifies the entry using the composite primary key (`blocker_id`, `blocked_id`).
     *
     * @return true if the entry was successfully deleted, false otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public boolean delete() throws SQLException {
        String sql = "DELETE FROM blocked_users WHERE blocker_id = ? AND blocked_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, this.blockerUserId);
            statement.setLong(2, this.blockedUserId);
            return statement.executeUpdate() > 0;
        }
    }

    // --- Retrieval Methods ---

    /**
     * Retrieves a specific blocked user entry using both blocker and blocked user IDs.
     *
     * @param blockerId The ID of the user who initiated the block.
     * @param blockedId The ID of the user who is blocked.
     * @return A BlockedUser object if found, null otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public static BlockedUser findByBlockerAndBlocked(Long blockerId, Long blockedId) throws SQLException {
        String sql = "SELECT blocker_id, blocked_id, blocked_at FROM blocked_users WHERE blocker_id = ? AND blocked_id = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, blockerId);
            statement.setLong(2, blockedId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new BlockedUser(
                            resultSet.getLong("blocker_id"),
                            resultSet.getLong("blocked_id"),
                            resultSet.getTimestamp("blocked_at")
                    );
                }
            }
        }
        return null;
    }

    /**
     * Retrieves all users blocked by a specific blocker user.
     *
     * @param blockerId The ID of the user whose blocked list is to be retrieved.
     * @return A List of BlockedUser objects (representing the blocked users).
     * @throws SQLException if a database access error occurs.
     */
    public static List<BlockedUser> findBlockedUsersByBlocker(Long blockerId) throws SQLException {
        List<BlockedUser> blockedUsers = new ArrayList<>();
        String sql = "SELECT blocker_id, blocked_id, blocked_at FROM blocked_users WHERE blocker_id = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, blockerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    blockedUsers.add(new BlockedUser(
                            resultSet.getLong("blocker_id"),
                            resultSet.getLong("blocked_id"),
                            resultSet.getTimestamp("blocked_at")
                    ));
                }
            }
        }
        return blockedUsers;
    }

    /**
     * Retrieves all users who have blocked a specific user.
     *
     * @param blockedId The ID of the user whose 'blocked by' list is to be retrieved.
     * @return A List of BlockedUser objects (representing the blockers).
     * @throws SQLException if a database access error occurs.
     */
    public static List<BlockedUser> findBlockersOfUser(Long blockedId) throws SQLException {
        List<BlockedUser> blockers = new ArrayList<>();
        String sql = "SELECT blocker_id, blocked_id, blocked_at FROM blocked_users WHERE blocked_id = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, blockedId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    blockers.add(new BlockedUser(
                            resultSet.getLong("blocker_id"),
                            resultSet.getLong("blocked_id"),
                            resultSet.getTimestamp("blocked_at")
                    ));
                }
            }
        }
        return blockers;
    }
}