package dev.visoftware.artiferrium.service;

import dev.architectury.platform.Platform;
import dev.visoftware.artiferrium.Artiferrium;
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
import java.util.concurrent.atomic.AtomicBoolean;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class HeartbeatService {
    private static final Logger LOGGER = LoggerFactory.getLogger("Artiferrium");
    private static PlayerCountProvider playerCountProvider = () -> 0; // Default provider returns 0
    private final HttpClient client;
    private final String sessionKey;
    private final String sessionId;
    private final Gson gson;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean hasWarnedConnectionFailure;
    private static final int MAX_RETRY_INTERVAL = 300; // Maximum retry interval in seconds (5 minutes)
    private int currentRetryInterval = 30; // Start with normal interval

    public static void setPlayerCountProvider(PlayerCountProvider provider) {
        playerCountProvider = provider;
    }

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
        this.hasWarnedConnectionFailure = new AtomicBoolean(false);
    }

    public void startHeartbeatScheduler(int initialPlayerCount) {
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
                // Get current player count from the platform-specific provider
                int currentPlayers = playerCountProvider.getCurrentPlayerCount();
                sendHeartbeat(currentPlayers);

                // If we successfully sent a heartbeat after a failure, log the recovery
                if (hasWarnedConnectionFailure.compareAndSet(true, false)) {
                    LOGGER.info("╔════════════════════════════════════════════════════════════════╗");
                    LOGGER.info("║                     ARTIFERRIUM NOTICE                         ║");
                    LOGGER.info("║--------------------------------------------------------      ║");
                    LOGGER.info("║ Connection to VI Software services has been restored!         ║");
                    LOGGER.info("║                                                              ║");
                    LOGGER.info("║ Heartbeat service resumed normal operation.                  ║");
                    LOGGER.info("╚════════════════════════════════════════════════════════════════╝");
                }

                // Reset retry interval on successful heartbeat
                currentRetryInterval = 30;
            } catch (Exception e) {
                // Only show the warning once when we first detect the failure
                if (hasWarnedConnectionFailure.compareAndSet(false, true)) {
                    LOGGER.warn("╔════════════════════════════════════════════════════════════════╗");
                    LOGGER.warn("║                     ARTIFERRIUM WARNING                        ║");
                    LOGGER.warn("║--------------------------------------------------------      ║");
                    LOGGER.warn("║ Connection to VI Software services has been lost!             ║");
                    LOGGER.warn("║                                                              ║");
                    LOGGER.warn("║ The server will continue to run, but some features may be    ║");
                    LOGGER.warn("║ unavailable until connection is restored.                    ║");
                    LOGGER.warn("║                                                              ║");
                    LOGGER.warn("║ Attempting to reconnect...                                   ║");
                    LOGGER.warn("╚════════════════════════════════════════════════════════════════╝");
                }

                LOGGER.debug("Heartbeat failed: {}", e.getMessage());

                // Implement exponential backoff for retries
                if (currentRetryInterval < MAX_RETRY_INTERVAL) {
                    currentRetryInterval = Math.min(currentRetryInterval * 2, MAX_RETRY_INTERVAL);
                }

                // Schedule retry with new interval
                scheduler.schedule(() -> {
                    try {
                        // Get current player count for retry attempt
                        sendHeartbeat(Artiferrium.getCurrentPlayerCount());
                    } catch (Exception retryException) {
                        LOGGER.debug("Retry attempt failed: {}", retryException.getMessage());
                    }
                }, currentRetryInterval, TimeUnit.SECONDS);
            }
        }, 0, 30, TimeUnit.SECONDS);
    }

    private void testHeartbeat() {
        sendHeartbeat(Artiferrium.getCurrentPlayerCount());
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
