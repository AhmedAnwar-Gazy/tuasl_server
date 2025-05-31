package orgs.models2;

// 1. Changed import for MySQL database connection
import jdk.internal.foreign.SystemLookup;
import orgs.utils.DatabaseConnection;
import java.math.BigInteger;
import java.sql.*;

public class Media {
    private BigInteger mediaId;
    private BigInteger uploaderUserId;
    private String fileName;
    private String filePathOrUrl;
    private String mimeType;
    private BigInteger fileSizeBytes;
    private String thumbnailUrl;
    private Integer durationSeconds;
    private Integer width;
    private Integer height;
    private Timestamp uploadedAt;

    public Media() {}

    public Media(String fileName, String filePathOrUrl, String mimeType, BigInteger fileSizeBytes, Timestamp uploadedAt) {
        this.fileName = fileName;
        this.filePathOrUrl = filePathOrUrl;
        this.mimeType = mimeType;
        this.fileSizeBytes = fileSizeBytes;
        this.uploadedAt = uploadedAt;
    }

    public Media(BigInteger mediaId, BigInteger uploaderUserId, String fileName, String filePathOrUrl, String mimeType, BigInteger fileSizeBytes, String thumbnailUrl, Integer durationSeconds, Integer width, Integer height, Timestamp uploadedAt) {
        this.mediaId = mediaId;
        this.uploaderUserId = uploaderUserId;
        this.fileName = fileName;
        this.filePathOrUrl = filePathOrUrl;
        this.mimeType = mimeType;
        this.fileSizeBytes = fileSizeBytes;
        this.thumbnailUrl = thumbnailUrl;
        this.durationSeconds = durationSeconds;
        this.width = width;
        this.height = height;
        this.uploadedAt = uploadedAt;
    }

    // Getters and Setters (remain unchanged)
    public BigInteger getMediaId() { return mediaId; }
    public void setMediaId(BigInteger mediaId) { this.mediaId = mediaId; }
    public BigInteger getUploaderUserId() { return uploaderUserId; }
    public void setUploaderUserId(BigInteger uploaderUserId) { this.uploaderUserId = uploaderUserId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFilePathOrUrl() { return filePathOrUrl; }
    public void setFilePathOrUrl(String filePathOrUrl) { this.filePathOrUrl = filePathOrUrl; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public BigInteger getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(BigInteger fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    public Timestamp getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Timestamp uploadedAt) { this.uploadedAt = uploadedAt; }

    public boolean save() throws SQLException {
        String sql = "INSERT INTO media (uploader_user_id, file_name, file_path_or_url, mime_type, file_size_bytes, thumbnail_url, duration_seconds, width, height, uploaded_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        // 2. Changed to use DatabaseConnection
        SystemLookup DatabaseConnection;
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            // Handling of uploaderUserId: Setting to 0 if null.
            // If your MySQL 'uploader_user_id' column allows NULL and you want to store NULL,
            // you might prefer: statement.setObject(1, uploaderUserId != null ? uploaderUserId.longValue() : null);
            // Or: if (uploaderUserId != null) statement.setLong(1, uploaderUserId.longValue()); else statement.setNull(1, Types.BIGINT);
            // For consistency with original, keeping the 0 for null.
            if (uploaderUserId != null) {
                statement.setLong(1, uploaderUserId.longValue());
            } else {
                // Consider if 0 is an appropriate default or if the column should be nullable
                // and you should use statement.setNull(1, Types.BIGINT);
                // For this conversion, I'm keeping the logic as it was (0 for null uploaderUserId).
                statement.setLong(1, 0); // Or handle as per your DB schema (e.g., setNull)
            }
            statement.setString(2, fileName);
            statement.setString(3, filePathOrUrl);
            statement.setString(4, mimeType);
            statement.setLong(5, fileSizeBytes.longValue()); // Assuming fileSizeBytes is never null based on constructor
            statement.setString(6, thumbnailUrl);
            statement.setObject(7, durationSeconds); // setObject handles null Integers appropriately
            statement.setObject(8, width);
            statement.setObject(9, height);
            statement.setTimestamp(10, uploadedAt); // Assuming uploadedAt is never null based on constructor

            boolean isInserted = statement.executeUpdate() > 0;
            if (isInserted) {
                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        this.mediaId = BigInteger.valueOf(generatedKeys.getLong(1));
                    }
                }
            }
            return isInserted;
        }
    }

    public boolean update() throws SQLException {
        String sql = "UPDATE media SET uploader_user_id = ?, file_name = ?, file_path_or_url = ?, mime_type = ?, file_size_bytes = ?, thumbnail_url = ?, duration_seconds = ?, width = ?, height = ? WHERE media_id = ?";
        // 2. Changed to use DatabaseConnection
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = conn.prepareStatement(sql)) {

            if (uploaderUserId != null) {
                statement.setLong(1, uploaderUserId.longValue());
            } else {
                statement.setLong(1, 0); // Or handle as per your DB schema
            }
            statement.setString(2, fileName);
            statement.setString(3, filePathOrUrl);
            statement.setString(4, mimeType);
            statement.setLong(5, fileSizeBytes.longValue());
            statement.setString(6, thumbnailUrl);
            statement.setObject(7, durationSeconds);
            statement.setObject(8, width);
            statement.setObject(9, height);
            statement.setLong(10, mediaId.longValue()); // Assuming mediaId is never null for an update

            return statement.executeUpdate() > 0;
        }
    }

    public boolean delete() throws SQLException {
        String sql = "DELETE FROM media WHERE media_id = ?";
        // 2. Changed to use DatabaseConnection
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setLong(1, mediaId.longValue()); // Assuming mediaId is never null for a delete
            return statement.executeUpdate() > 0;
        }
    }
}