package dev.visoftware.artiferrium;

import dev.architectury.platform.Platform;
import dev.visoftware.artiferrium.model.ServerData;
import dev.visoftware.artiferrium.service.HeartbeatService;
import dev.visoftware.artiferrium.service.AuthenticationService;
import dev.visoftware.artiferrium.service.AllowlistService;
import dev.visoftware.artiferrium.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import com.google.gson.JsonObject;

public final class Artiferrium {
    public static final String MOD_ID = "artiferrium";
    private static final Logger LOGGER = LoggerFactory.getLogger("Artiferrium");
    private static HeartbeatService heartbeatService;
    private static AuthenticationService authService;
    private static AllowlistService allowlistService;
    private static boolean isPrivateServer = false;
    private static boolean hasWarnedHeartbeatFailure = false;
    private static final String CONFIG_FOLDER = "visoftware";
    private static final String CONFIG_FILE = "artiferrium.toml";
    private static ServerData serverData;
    private static net.minecraft.server.MinecraftServer currentServer;

    private static boolean isOfflineMode() {
        if (!"SERVER".equals(Platform.getEnvironment().name())) {
            return false;
        }

        Path serverProperties = Platform.getGameFolder().resolve("server.properties");
        if (Files.exists(serverProperties)) {
            try {
                List<String> lines = Files.readAllLines(serverProperties);
                return lines.stream()
                    .map(String::trim)
                    .filter(line -> line.startsWith("online-mode="))
                    .map(line -> line.substring("online-mode=".length()))
                    .map(String::trim)
                    .map(Boolean::parseBoolean)
                    .findFirst()
                    .map(online -> !online)
                    .orElse(false);
            } catch (IOException e) {
                LOGGER.error("Failed to read server.properties", e);
            }
        }
        return false;
    }

    public static void init() {
        try {
            // Create config directory if it doesn't exist
            Path configFolder = Platform.getConfigFolder().resolve(CONFIG_FOLDER);
            Files.createDirectories(configFolder);

            // Load config
            Path configPath = configFolder.resolve(CONFIG_FILE);
            Config.get().load(configPath);

            // Set up logging level based on config
            configureLogging();

            // Only perform server-specific initialization if we're on a server
            if ("SERVER".equals(Platform.getEnvironment().name())) {
                authenticateAndInitialize();
            }

            // Register commands (they will only work on server anyway)
            dev.visoftware.artiferrium.command.ArtifferiumCommands.register();
        } catch (IOException e) {
            LOGGER.error("Failed to load config", e);
            System.exit(1);
        }
    }

    private static void configureLogging() {
        org.apache.logging.log4j.core.LoggerContext ctx =
            (org.apache.logging.log4j.core.LoggerContext) org.apache.logging.log4j.LogManager.getContext(false);
        org.apache.logging.log4j.core.config.Configuration config = ctx.getConfiguration();

        // Set Artiferrium logger level based on debug setting
        String loggerLevel = Config.get().isDebug() ? "DEBUG" : "INFO";
        org.apache.logging.log4j.Level level = org.apache.logging.log4j.Level.toLevel(loggerLevel);

        config.getLoggerConfig("Artiferrium").setLevel(level);
        ctx.updateLoggers();

        LOGGER.info("Logging level set to: {}", loggerLevel);
    }

    private static void shutdownWithError(String message) {
        LOGGER.error("╔════════════════════════════════════════════════════════════════╗");
        LOGGER.error("║                        ARTIFERRIUM CRITICAL ERROR                         ║");
        LOGGER.error("║───────────────────────────────��───────────────────────────────────────────║");
        String[] lines = message.split("\\n");
        for (String line : lines) {
            String paddedLine = String.format("║ %-69s ║", line);
            LOGGER.error(paddedLine);
        }
        LOGGER.error("║                                                                           ║");
        LOGGER.error("║                     THE SERVER WILL NOW SHUT DOWN                         ║");
        LOGGER.error("╚═══════════════════════════════════════════════════════════════════════════╝");

        try {
            Thread.sleep(2000); // Give time for the message to be seen
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.exit(1);
    }

    private static void authenticateAndInitialize() {
        String serverKey = Config.get().getServerKey();
        if (!serverKey.isEmpty()) {
            System.out.println("Attempting to authenticate with VI Software...");
            try {
                authService = new AuthenticationService();
                JsonObject response = authService.authenticate(serverKey);

                if (!authService.hasValidSession()) {
                    shutdownWithError("Failed to obtain valid session credentials\nPlease check your server key and try again.");
                }

                // Store server data and display welcome message
                serverData = new ServerData(response);
                isPrivateServer = serverData.isPrivate();

                LOGGER.info("╔════════════════════════════════════════════════════════════════╗");
                LOGGER.info("║                   ARTIFERRIUM SERVER INFO                      ║");
                LOGGER.info("║--------------------------------------------------------      ║");
                LOGGER.info("║ Server Name: {}", String.format("%-52s ║", serverData.getName()));
                if (!serverData.getDescription().isEmpty()) {
                    String desc = serverData.getDescription();
                    while (desc.length() > 52) {
                        LOGGER.info("║ {}", String.format("%-62s ║", desc.substring(0, 52)));
                        desc = desc.substring(52);
                    }
                    if (!desc.isEmpty()) {
                        LOGGER.info("║ {}", String.format("%-62s ║", desc));
                    }
                }
                LOGGER.info("║ Owner: {}", String.format("%-57s ║", serverData.getOwnerName()));
                LOGGER.info("║ Language: {}", String.format("%-54s ║", serverData.getLanguage()));
                LOGGER.info("║ Type: {}", String.format("%-58s ║", serverData.isPrivate() ? "Private" : "Public"));
                LOGGER.info("╚════════════════════════════════════════════════════════════════╝");

                // Get session credentials from response
                String sessionKey = response.get("sessionKey").getAsString();
                String sessionId = response.get("sessionId").getAsString();

                if (sessionKey == null || sessionId == null) {
                    shutdownWithError("Failed to obtain session credentials from server response");
                }

                // Initialize services with session credentials
                heartbeatService = new HeartbeatService(sessionKey, sessionId);

                // Initialize allowlist service if server is private
                if (isPrivateServer) {
                    if (isOfflineMode()) {
                        LOGGER.warn("╔═══════════════════════════════════���════════════════════════════╗");
                        LOGGER.warn("║                     ARTIFERRIUM WARNING                        ║");
                        LOGGER.warn("║--------------------------------------------------------      ║");
                        LOGGER.warn("║ Server is running in offline mode!                           ║");
                        LOGGER.warn("║ The allowlist will not provide effective access control      ║");
                        LOGGER.warn("║ as players can join with any username in offline mode.       ║");
                        LOGGER.warn("║                                                              ║");
                        LOGGER.warn("║ Consider enabling online mode in server.properties           ║");
                        LOGGER.warn("║ for proper player authentication and allowlist control.      ║");
                        LOGGER.warn("╚════════════════════════════════════════════════════════════════╝");
                    }

                    allowlistService = new AllowlistService(
                        sessionKey,
                        sessionId,
                        Platform.getConfigFolder(),
                        true
                    );
                }

                // Start the heartbeat scheduler
                heartbeatService.startHeartbeatScheduler(0);

            } catch (Exception e) {
                shutdownWithError("Authentication failed: " + e.getMessage() +
                    "\nPlease verify your server key and network connection.");
            }
        } else {
            shutdownWithError("Server key is not configured!\n" +
                "Please set your server key in config/visoftware/artiferrium.toml");
        }
    }

    public static void initializeServices() {
        if (!"SERVER".equals(Platform.getEnvironment().name())) {
            return;
        }

        LOGGER.info("Starting Artiferrium services...");

        // Start heartbeat service if we have session credentials
        if (heartbeatService != null) {
            heartbeatService.startHeartbeatScheduler(0);
        }

        LOGGER.info("Artiferrium services initialized");
    }

    public static boolean isPrivateServer() {
        return isPrivateServer;
    }

    public static boolean isPlayerAllowed(String playerUuid) {
        if (!isPrivateServer || allowlistService == null) return true;
        return allowlistService.isAllowed(playerUuid);
    }

    public static void shutdown() {
        if (allowlistService != null) {
            allowlistService.shutdown();
        }
        if (heartbeatService != null) {
            heartbeatService.stopHeartbeatScheduler();
        }
    }

    public static void updateServerData(int playerCount, long heartbeat) {
        if (serverData != null) {
            serverData.setPlayerCount(playerCount);
            serverData.setLastHeartbeat(heartbeat);
        }
    }

    public static ServerData getServerData() {
        return serverData;
    }

    public static void setCurrentServer(net.minecraft.server.MinecraftServer server) {
        currentServer = server;
    }

    public static int getCurrentPlayerCount() {
        if (currentServer != null) {
            return currentServer.getPlayerList().getPlayers().size();
        }
        return 0;
    }
}
