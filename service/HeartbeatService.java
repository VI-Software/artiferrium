package dev.visoftware.artiferrium.service;

import dev.architectury.platform.Platform;
import dev.visoftware.artiferrium.constants.ApiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class HeartbeatService {
    private static final Logger LOGGER = LoggerFactory.getLogger("Artiferrium");
    private final HttpClient client;
    private final String sessionKey;
    private final String sessionId;
    private final Gson gson;
    private final ScheduledExecutorService scheduler;
    private final AtomicInteger playerCount;

    public HeartbeatService(String sessionKey, String sessionId) {
        this.sessionKey = sessionKey;
        this.sessionId = sessionId;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "Artiferrium-Heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        this.playerCount = new AtomicInteger(0);
    }

    public void startHeartbeatScheduler(int initialPlayerCount) {
        playerCount.set(initialPlayerCount);

        // First do an initial test heartbeat
        try {
            LOGGER.info("Testing connection with heartbeat...");
            testHeartbeat();
            LOGGER.info("Successfully connected to VI Software!");
        } catch (Exception e) {
            throw new RuntimeException("Initial heartbeat test failed: " + e.getMessage() +
                "\nPlease check your network connection and server status.");
        }

        // If test was successful, start the scheduler
        scheduler.scheduleAtFixedRate(() -> {
            try {
                sendHeartbeat(playerCount.get());
            } catch (Exception e) {
                LOGGER.error("Scheduled heartbeat failed", e);
            }
        }, 0, 30, TimeUnit.SECONDS);
    }

    public void updatePlayerCount(int newPlayerCount) {
        playerCount.set(newPlayerCount);
    }

    private void testHeartbeat() {
        sendHeartbeat(playerCount.get());
    }

    public void stopHeartbeatScheduler() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void sendHeartbeat(int playerCount) {
        try {
            LOGGER.debug("Sending heartbeat (players: {})", playerCount);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ApiConstants.SERVER_HEARTBEAT_ENDPOINT))
                    .header("Content-Type", "application/json")
                    .header("sessionkey", sessionKey)
                    .header("sessionid", sessionId)
                    .header("playercount", String.valueOf(playerCount))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            LOGGER.debug("Heartbeat response: {} - {}", response.statusCode(), response.body());

            if (response.statusCode() != 200) {
                String error = "Heartbeat failed with status " + response.statusCode();
                LOGGER.error(error + ": " + response.body());
                throw new RuntimeException(error);
            }

            JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
            if (!"OK".equals(jsonResponse.get("status").getAsString())) {
                String error = jsonResponse.has("message") ?
                    jsonResponse.get("message").getAsString() :
                    "Unknown error";
                LOGGER.error("Heartbeat failed: {}", error);
                throw new RuntimeException("Heartbeat failed: " + error);
            }

            LOGGER.debug("Heartbeat successful");
        } catch (Exception e) {
            String error = "Failed to send heartbeat: " + e.getMessage();
            LOGGER.error(error, e);
            throw new RuntimeException(error);
        }
    }
}
