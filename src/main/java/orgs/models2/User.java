package orgs.models2;

import orgs.utils.DatabaseConnection;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;

public class User {
    private Long userId; // Changed from BigInteger to Long to better match MySQL INT AUTO_INCREMENT
    private String phoneNumber;
    private String password;
    private String username;
    private String firstName;
    private String lastName;
    private String bio;
    private String profilePictureUrl;
    private String hashedPassword;    // Present in Java class, but NOT in your MySQL 'users' table schema
    private String twoFactorSecret;   // Present in Java class, but NOT in your MySQL 'users' table schema
    private Timestamp lastSeenAt;
    private boolean isOnline;
    private Timestamp createdAt;     // Managed by DB default for INSERT
    private Timestamp updatedAt;     // Managed by DB default for UPDATE


    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public User() {}

    // Constructor for new user creation (without auto-generated ID and DB-managed timestamps)
    public User(String phoneNumber,String password, String username, String firstName, String lastName, String bio, String profilePictureUrl,
            /* String hashedPassword, String twoFactorSecret, */ // Removed as per DB schema
                Timestamp lastSeenAt, boolean isOnline) {
        this.password = password;
        this.phoneNumber = phoneNumber;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.bio = bio;
        this.profilePictureUrl = profilePictureUrl;
        // this.hashedPassword = hashedPassword; // Removed as per DB schema
        // this.twoFactorSecret = twoFactorSecret; // Removed as per DB schema
        this.lastSeenAt = lastSeenAt;
        this.isOnline = isOnline;
        // createdAt and updatedAt are handled by the database
    }

    // Full constructor for retrieving existing user data
    public User(Long userId, String phoneNumber,String password, String username, String firstName, String lastName, String bio,
                String profilePictureUrl, /* String hashedPassword, String twoFactorSecret, */
                Timestamp lastSeenAt, boolean isOnline, Timestamp createdAt, Timestamp updatedAt) {
        this.userId = userId;
        this.phoneNumber = phoneNumber;
        this.password= password;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.bio = bio;
        this.profilePictureUrl = profilePictureUrl;
        // this.hashedPassword = hashedPassword; // Removed as per DB schema
        // this.twoFactorSecret = twoFactorSecret; // Removed as per DB schema
        this.lastSeenAt = lastSeenAt;
        this.isOnline = isOnline;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // --- Getters and Setters (Updated for Long userId and removed fields) ---
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getProfilePictureUrl() { return profilePictureUrl; }
    public void setProfilePictureUrl(String profilePictureUrl) { this.profilePictureUrl = profilePictureUrl; }
    public String getHashedPassword() { return hashedPassword; } // Field remains, but not mapped to DB
    public void setHashedPassword(String hashedPassword) { this.hashedPassword = hashedPassword; }
    public String getTwoFactorSecret() { return twoFactorSecret; } // Field remains, but not mapped to DB
    public void setTwoFactorSecret(String twoFactorSecret) { this.twoFactorSecret = twoFactorSecret; }
    public Timestamp getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Timestamp lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    // --- Database Operations ---

    /**
     * Saves a new user record to the database.
     * The `created_at` and `updated_at` timestamps are handled by the database.
     *
     * @return true if the user was successfully inserted, false otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public boolean save() throws SQLException {
        // Removed 'hashed_password', 'two_factor_secret', 'created_at', 'updated_at' from INSERT statement
        String sql = "INSERT INTO users (phone_number,password, username, first_name, last_name, bio, profile_picture_url, last_seen_at, is_online) " +
                "VALUES (?,?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, phoneNumber);
            statement.setString(2,password);
            statement.setString(3, username);
            statement.setString(4, firstName);
            statement.setString(5, lastName);
            statement.setString(6, bio);
            statement.setString(7, profilePictureUrl);
            statement.setTimestamp(8, lastSeenAt);
            statement.setBoolean(9, isOnline); // Use setBoolean for MySQL BOOLEAN type

            boolean isInserted = statement.executeUpdate() > 0;
            if (isInserted) {
                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        this.userId = generatedKeys.getLong(1); // Set the auto-generated ID
                    }
                }
            }
            return isInserted;
        }
    }

    /**
     * Updates an existing user record in the database.
     * The `updated_at` timestamp is handled automatically by the database.
     *
     * @return true if the user was successfully updated, false otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public boolean update() throws SQLException {
        // Removed 'hashed_password', 'two_factor_secret', 'updated_at' from UPDATE statement
        String sql = "UPDATE users SET phone_number = ?, username = ?, first_name = ?, last_name = ?, bio = ?, " +
                "profile_picture_url = ?, last_seen_at = ?, is_online = ? " +
                "WHERE id = ?"; // Use 'id' as per MySQL schema

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, phoneNumber);
            statement.setString(2, username);
            statement.setString(3, firstName);
            statement.setString(4, lastName);
            statement.setString(5, bio);
            statement.setString(6, profilePictureUrl);
            statement.setTimestamp(7, lastSeenAt);
            statement.setBoolean(8, isOnline); // Use setBoolean for MySQL BOOLEAN type
            statement.setLong(9, userId); // Use userId for the WHERE clause

            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Deletes a user record from the database.
     *
     * @return true if the user was successfully deleted, false otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public boolean delete() throws SQLException {
        String sql = "DELETE FROM users WHERE id = ?"; // Use 'id' as per MySQL schema
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, userId); // Use userId for the WHERE clause
            return statement.executeUpdate() > 0;
        }
    }

    // --- Retrieval Methods ---

    /**
     * Retrieves a user by their ID.
     *
     * @param id The ID of the user.
     * @return A User object if found, null otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public static User findById(Long id) throws SQLException {
        String sql = "SELECT id, phone_number, username, first_name, last_name, bio, profile_picture_url, " +
                "last_seen_at, is_online, created_at, updated_at " +
                "FROM users WHERE id = ?";
        return executeQueryAndBuildUser(sql, id);
    }

    /**
     * Retrieves a user by their phone number.
     *
     * @param phoneNumber The phone number of the user.
     * @return A User object if found, null otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public static User findByPhoneNumber(String phoneNumber) throws SQLException {
        String sql = "SELECT id, password,phone_number, username, first_name, last_name, bio, profile_picture_url, " +
                "last_seen_at, is_online, created_at, updated_at " +
                "FROM users WHERE phone_number = ?";
        return executeQueryAndBuildUser(sql, phoneNumber);
    }

    /**
     * Retrieves a user by their username.
     *
     * @param username The username of the user.
     * @return A User object if found, null otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public static User findByUsername(String username) throws SQLException {
        String sql = "SELECT id, phone_number, username, first_name, last_name, bio, profile_picture_url, " +
                "last_seen_at, is_online, created_at, updated_at " +
                "FROM users WHERE username = ?";
        return executeQueryAndBuildUser(sql, username);
    }

    /**
     * Helper method to execute a query and build a User object from the ResultSet.
     *
     * @param sql The SQL query to execute.
     * @param params The parameters for the PreparedStatement (e.g., Long id, String phoneNumber).
     * @return A User object or null.
     * @throws SQLException if a database access error occurs.
     */
    private static User executeQueryAndBuildUser(String sql, Object... params) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                if (params[i] instanceof Long) {
                    statement.setLong(i + 1, (Long) params[i]);
                } else if (params[i] instanceof String) {
                    statement.setString(i + 1, (String) params[i]);
                }
                // Add more types if needed (e.g., Integer, Boolean, Timestamp)
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    User user = new User();
                    user.setUserId(resultSet.getLong("id"));
                    user.setPassword(resultSet.getString("password"));
                    user.setPhoneNumber(resultSet.getString("phone_number"));
                    user.setUsername(resultSet.getString("username"));
                    user.setFirstName(resultSet.getString("first_name"));
                    user.setLastName(resultSet.getString("last_name"));
                    user.setBio(resultSet.getString("bio"));
                    user.setProfilePictureUrl(resultSet.getString("profile_picture_url"));
                    user.setLastSeenAt(resultSet.getTimestamp("last_seen_at"));
                    user.setOnline(resultSet.getBoolean("is_online")); // Use getBoolean for MySQL BOOLEAN type
                    user.setCreatedAt(resultSet.getTimestamp("created_at"));
                    user.setUpdatedAt(resultSet.getTimestamp("updated_at"));
                    // Note: hashedPassword and twoFactorSecret are not in DB schema, so not retrieved here
                    return user;
                }
            }
        }
        return null;
    }


        public String getPasswordHash() {
            if (hashedPassword == null || hashedPassword.isEmpty()) {
                return null;
            }

            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] encodedHash = digest.digest(hashedPassword.getBytes());
                return bytesToHex(encodedHash);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Hashing algorithm not found", e);
            }
        }




    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}