package com.uxplima.uxmlib.command.annotation;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

/**
 * The names of the players who are online, for an argument that takes one as a {@link String}.
 *
 * <p>A command that names a player as a {@code Player} gets Brigadier's own player argument and suggests for
 * free. A great many of ours cannot: they act on somebody who is offline (giving a key, setting a level,
 * reading a ledger), so the argument is a {@code String} and the plugin looks the name up itself. A
 * {@code String} argument suggests nothing at all, so those commands were typed blind.
 *
 * <p>Twenty five of them were, across five plugins, and the owner found it on 2026-09-08 typing a player's
 * name into {@code /cratesadmin key give} and getting no help. This is the one implementation they all take:
 * {@code @Arg("player") @SuggestUsing(PlayerNames.KEY) String player}.
 *
 * <p>It suggests only who is online, which is what a server can answer instantly and without a database. An
 * offline name still works when it is typed in full, because the resolution is the plugin's and this only
 * offers the easy half.
 */
public final class PlayerNames implements SuggestionSource {

    /** The key {@code ParamResolvers.withDefaults()} registers this under. */
    public static final String KEY = "uxmlib:players";

    @Override
    public CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String written = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (Player player : Bukkit.getOnlinePlayers()) {
            String name = player.getName();
            if (name.toLowerCase(Locale.ROOT).startsWith(written)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }
}
