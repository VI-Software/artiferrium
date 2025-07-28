package dev.visoftware.artiferrium.fabric;

import dev.visoftware.artiferrium.Artiferrium;
import dev.visoftware.artiferrium.config.Config;
import dev.visoftware.artiferrium.service.HeartbeatService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ArtiferriumFabric implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Artiferrium");
    private static final String CONFIG_FOLDER = "visoftware";
    private static final String CONFIG_FILE = "artiferrium.toml";
    private static net.minecraft.server.MinecraftServer currentServer;

    @Override
    public void onInitialize() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FOLDER);
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create config directory", e);
        }

        loadConfig();

        Artiferrium.init();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            currentServer = server;
            HeartbeatService.setPlayerCountProvider(() ->
                currentServer != null ? currentServer.getPlayerList().getPlayers().size() : 0
            );

            Artiferrium.initializeServices();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            currentServer = null;
            Artiferrium.shutdown();
        });

        // join handler for private server access check
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (Artiferrium.isPrivateServer()) {
                ServerPlayer player = handler.getPlayer();
                String playerUuid = player.getUUID().toString();
                String playerName = player.getName().getString();

                if (!Artiferrium.isPlayerAllowed(playerUuid)) {
                    LOGGER.warn("Access denied for player {} (UUID: {}) - Not in allowlist", playerName, playerUuid);
                    handler.getPlayer().connection.disconnect(Component.literal(Config.get().getKickMessage()));
                } else {
                    LOGGER.info("Access granted for player {} (UUID: {}) - In allowlist", playerName, playerUuid);
                }
            }
        });
    }

    private void loadConfig() {
        Path configFile = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FOLDER).resolve(CONFIG_FILE);
        try {
            Config.get().load(configFile);
        } catch (IOException e) {
            LOGGER.error("Failed to load config", e);
            throw new RuntimeException("Failed to load config", e);
        }
    }
}
