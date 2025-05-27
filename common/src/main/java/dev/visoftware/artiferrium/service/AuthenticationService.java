package dev.visoftware.artiferrium.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.visoftware.artiferrium.constants.ApiConstants;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class AuthenticationService {
    private String sessionKey;
    private String sessionId;

    public JsonObject authenticate(String serverKey) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConstants.SERVER_AUTH_ENDPOINT))
                .header("Content-Type", "application/json")
                .header("serverkey", serverKey)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Authentication failed with status code: " + response.statusCode());
        }

        JsonObject jsonResponse = new Gson().fromJson(response.body(), JsonObject.class);
        if (!"OK".equals(jsonResponse.get("status").getAsString())) {
            throw new Exception("Authentication failed: " + jsonResponse.get("message").getAsString());
        }

        this.sessionKey = jsonResponse.get("sessionKey").getAsString();
        this.sessionId = jsonResponse.get("sessionId").getAsString();

        return jsonResponse;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public String getSessionId() {
        return sessionId;
    }

    public boolean hasValidSession() {
        return sessionKey != null && sessionId != null && !sessionKey.isEmpty() && !sessionId.isEmpty();
    }
}
