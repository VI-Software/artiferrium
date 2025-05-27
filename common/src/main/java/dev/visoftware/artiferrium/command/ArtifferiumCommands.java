package dev.visoftware.artiferrium.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.visoftware.artiferrium.service.AllowlistService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

public class ArtifferiumCommands {
    public static void register() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            dispatcher.register(
                literal("artiferrium")
                    .requires(source -> !source.isPlayer()) // Only allow console
                    .then(literal("reload")
                        .then(literal("allowlist")
                            .then(literal("api")
                                .executes(ArtifferiumCommands::reloadAllowlistFromApi)
                            )
                            .then(literal("cache")
                                .executes(ArtifferiumCommands::reloadAllowlistFromCache)
                            )
                        )
                    )
            );
        });
    }

    private static int reloadAllowlistFromApi(CommandContext<CommandSourceStack> context) {
        try {
            context.getSource().sendSuccess(() ->
                Component.literal("§6Warning: Frequent API refreshes may result in rate limits."), false);

            AllowlistService.refreshCachedAllowlist();

            context.getSource().sendSuccess(() ->
                Component.literal("§aSuccessfully refreshed the allowlist cache from VI Software."), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("§cFailed to refresh allowlist: " + e.getMessage()));
            return 0;
        }
    }

    private static int reloadAllowlistFromCache(CommandContext<CommandSourceStack> context) {
        try {
            context.getSource().sendSuccess(() ->
                Component.literal("§6Warning: Cache will be overwritten on next API refresh"), false);

            AllowlistService.reloadFromCache();

            context.getSource().sendSuccess(() ->
                Component.literal("§aSuccessfully reloaded allowlist from local cache at config/visoftware/allowlist-cache.json"), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("§cFailed to reload allowlist from cache: " + e.getMessage()));
            return 0;
        }
    }
}
