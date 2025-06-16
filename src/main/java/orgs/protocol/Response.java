package orgs.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

// This class will be serialized to JSON and sent over the network
public class Response {
    private boolean success;
    private String message;
    private String data; // JSON string representing the response data (e.g., list of messages, user info)

    public Response(boolean success, String message, String data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getData() {
        return data;
    }

    // Helper for serialization to JSON string
    public String toJson() {
        Gson gson = new GsonBuilder().create();
        return gson.toJson(this);
    }

    // Helper for deserialization from JSON string
    public static Response fromJson(String jsonString) {
        Gson gson = new GsonBuilder().create();
        return gson.fromJson(jsonString, Response.class);
    }
}
