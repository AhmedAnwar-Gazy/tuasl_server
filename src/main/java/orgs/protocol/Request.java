package orgs.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

// This class will be serialized to JSON and sent over the network
public class Request {
    private Command command;
    private String payload; // JSON string representing the data for the command

    public Request(Command command, String payload) {
        this.command = command;
        this.payload = payload;
    }

    public Command getCommand() {
        return command;
    }

    public String getPayload() {
        return payload;
    }

    // Helper for serialization to JSON string
    public String toJson() {
        Gson gson = new GsonBuilder().create();
        return gson.toJson(this);
    }

    // Helper for deserialization from JSON string
    public static Request fromJson(String jsonString) {
        Gson gson = new GsonBuilder().create();
        return gson.fromJson(jsonString, Request.class);
    }
}
