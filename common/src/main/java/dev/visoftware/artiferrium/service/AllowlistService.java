package dev.visoftware.artiferrium.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.visoftware.artiferrium.constants.ApiConstants;
import dev.visoftware.artiferrium.model.AllowedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AllowlistService {
    private static final Logger LOGGER = LoggerFactory.getLogger("Artiferrium");
    private static final long REFRESH_INTERVAL = 15; // minutes
    private static final String CONFIG_FOLDER = "visoftware";
    private static final String CACHE_FILE = "allowlist-cache.json";
    private static AllowlistService INSTANCE;

    private final String sessionKey;
    private final String sessionId;
    private final Path cacheFilePath;
    private final Gson gson;
    private final HttpClient client;
    private final Set<String> allowedUuids;
    private final ScheduledExecutorService executor;
    private boolean isPrivateServer;

    public AllowlistService(String sessionKey, String sessionId, Path configDir, boolean isPrivateServer) {
        this.sessionKey = sessionKey;
        this.sessionId = sessionId;
        Path visoftwareConfigDir = configDir.resolve(CONFIG_FOLDER);
        try {
            Files.createDirectories(visoftwareConfigDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create config directory", e);
        }
        this.cacheFilePath = visoftwareConfigDir.resolve(CACHE_FILE);
        this.gson = new Gson();
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.allowedUuids = new HashSet<>();
        this.executor = Executors.newSingleThreadScheduledExecutor();
        this.isPrivateServer = isPrivateServer;
        INSTANCE = this;

        loadCachedData();

        if (isPrivateServer) {
            startPeriodicRefresh();
        }
    }

    public static void refreshCachedAllowlist() throws Exception {
        if (INSTANCE != null) {
            INSTANCE.refreshAllowlist();
        } else {
            throw new IllegalStateException("AllowlistService has not been initialized");
        }
    }

    public static void reloadFromCache() throws Exception {
        if (INSTANCE != null) {
            INSTANCE.loadCachedData();
        } else {
            throw new IllegalStateException("AllowlistService has not been initialized");
        }
    }

    private void startPeriodicRefresh() {
        executor.scheduleAtFixedRate(() -> {
            try {
                refreshAllowlist();
            } catch (Exception e) {
                LOGGER.error("Failed to refresh allowlist: {}", e.getMessage());
            }
        }, 0, REFRESH_INTERVAL, TimeUnit.MINUTES);
    }

    public void shutdown() {
        executor.shutdown();
    }

    private String normalizeUuid(String uuid) {
        return uuid.replace("-", "").toLowerCase();
    }

    public boolean isAllowed(String playerUuid) {
        if (!isPrivateServer) return true;

        String normalizedPlayerUuid = normalizeUuid(playerUuid);
        LOGGER.debug("Checking access for UUID: {} (normalized: {})", playerUuid, normalizedPlayerUuid);
        LOGGER.debug("Current allowlist: {}", allowedUuids);

        boolean isAllowed = allowedUuids.stream()
                .map(this::normalizeUuid)
                .anyMatch(uuid -> uuid.equals(normalizedPlayerUuid));

        LOGGER.debug("Access {} for UUID: {}", isAllowed ? "granted" : "denied", playerUuid);
        return isAllowed;
    }

    public void refreshAllowlist() throws Exception {
        if (!isPrivateServer) {
            throw new IllegalStateException("Cannot refresh allowlist on a public server");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConstants.SERVER_ALLOWLIST_ENDPOINT))
                .header("Content-Type", "application/json")
                .header("sessionkey", sessionKey)
                .header("sessionid", sessionId)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Failed to refresh allowlist. Status code: " + response.statusCode());
        }

        JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
        if (!"OK".equals(jsonResponse.get("status").getAsString())) {
            throw new Exception("Failed to refresh allowlist: " + jsonResponse.get("message").getAsString());
        }

        allowedUuids.clear();

        JsonArray allowedUsers = jsonResponse.getAsJsonArray("allowedUsers");
        List<AllowedUser> users = new ArrayList<>();

        for (JsonElement element : allowedUsers) {
            String uuid = element.getAsString();
            allowedUuids.add(uuid);
            users.add(new AllowedUser(uuid, null));
        }

        saveToCacheFile(users);

        LOGGER.info("Successfully refreshed allowlist cache. Total allowed players: " + allowedUuids.size());
    }

    private void loadCachedData() {
        if (Files.exists(cacheFilePath)) {
            try (Reader reader = Files.newBufferedReader(cacheFilePath)) {
                allowedUuids.clear();

                JsonObject cache = gson.fromJson(reader, JsonObject.class);
                JsonArray users = cache.getAsJsonArray("users");

                for (JsonElement element : users) {
                    JsonObject user = element.getAsJsonObject();
                    String uuid = user.get("uuid").getAsString();
                    JsonElement expiryElement = user.get("expiryDate");

                    uuid = normalizeUuid(uuid);
                    if (uuid.length() != 32) {
                        LOGGER.warn("Invalid UUID in cache file: {} (wrong length)", uuid);
                        continue;
                    }

                    if (expiryElement == null || expiryElement.isJsonNull() ||
                        LocalDateTime.parse(expiryElement.getAsString()).isAfter(LocalDateTime.now())) {
                        allowedUuids.add(uuid);
                        LOGGER.debug("Loaded UUID from cache: {}", uuid);
                    }
                }

                LOGGER.info("Loaded " + allowedUuids.size() + " allowed players from cache");
            } catch (IOException e) {
                LOGGER.error("Error loading allowlist cache", e);
            }
        }
    }

    private void saveToCacheFile(List<AllowedUser> users) {
        try {
            JsonObject cache = new JsonObject();
            JsonArray usersArray = new JsonArray();

            for (AllowedUser user : users) {
                JsonObject userObj = new JsonObject();
                userObj.addProperty("uuid", user.getUuid());
                if (user.getExpiryDate() != null) {
                    userObj.addProperty("expiryDate", user.getExpiryDate().toString());
                }
                usersArray.add(userObj);
            }

            cache.add("users", usersArray);

            try (Writer writer = Files.newBufferedWriter(cacheFilePath)) {
                gson.toJson(cache, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Error saving allowlist cache", e);
        }
    }
}
