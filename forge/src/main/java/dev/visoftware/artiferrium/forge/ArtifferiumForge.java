package dev.visoftware.artiferrium.forge;

import dev.visoftware.artiferrium.Artiferrium;
import dev.visoftware.artiferrium.config.Config;
import dev.visoftware.artiferrium.service.HeartbeatService;
import dev.architectury.platform.forge.EventBuses;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod(Artiferrium.MOD_ID)
public class ArtifferiumForge {
    private static final Logger LOGGER = LoggerFactory.getLogger("Artiferrium");
    private static final String CONFIG_FOLDER = "visoftware";
    private static final String CONFIG_FILE = "artiferrium.toml";
    private static net.minecraft.server.MinecraftServer currentServer;

    @SuppressWarnings("removal")
    public ArtifferiumForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        EventBuses.registerModEventBus(Artiferrium.MOD_ID, modEventBus);

        loadConfig();

        Artiferrium.init();

        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        currentServer = event.getServer();
        HeartbeatService.setPlayerCountProvider(() ->
            currentServer != null ? currentServer.getPlayerList().getPlayers().size() : 0
        );

        Artiferrium.initializeServices();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        currentServer = null;
        Artiferrium.shutdown();
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (Artiferrium.isPrivateServer()) {
            String playerUuid = event.getEntity().getUUID().toString();
            String playerName = event.getEntity().getName().getString();
            if (!Artiferrium.isPlayerAllowed(playerUuid)) {
                LOGGER.warn("Access denied for player {} (UUID: {}) - Not in allowlist", playerName, playerUuid);
                if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                    serverPlayer.connection.disconnect(Component.literal(Config.get().getKickMessage()));
                }
            } else {
                LOGGER.info("Access granted for player {} (UUID: {}) - In allowlist", playerName, playerUuid);
            }
        }
    }

    private void loadConfig() {
        Path configFile = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FOLDER).resolve(CONFIG_FILE);
        try {
            Config.get().load(configFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config", e);
        }
    }
}
