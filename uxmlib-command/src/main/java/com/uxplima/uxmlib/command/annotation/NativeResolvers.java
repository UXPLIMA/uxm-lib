package com.uxplima.uxmlib.command.annotation;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver;
import io.papermc.paper.math.FinePosition;
import io.papermc.paper.registry.RegistryKey;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import org.jspecify.annotations.Nullable;

/**
 * Resolvers for the Bukkit types beyond player/world/material that 1.21 exposes natively, so they cost a
 * thin wrapper over a Paper {@link ArgumentTypes} rather than any NMS: a {@link Location} from
 * {@code finePosition} (resolved against the sender's world) and a {@link Sound} from the sound-event
 * registry. The {@link OfflinePlayer} here is the exception, and reads a plain word: see
 * {@link #offlinePlayerResolver()} for why it does not use Paper's player-profile argument. Installed
 * alongside the primitives by {@link BuiltinResolvers}. Split out of {@code BuiltinResolvers} so each file
 * stays within its size budget.
 */
final class NativeResolvers {

    private NativeResolvers() {}

    static void installInto(ParamResolvers r) {
        r.register(Location.class, locationResolver());
        r.register(OfflinePlayer.class, offlinePlayerResolver());
        r.register(Sound.class, soundResolver());
    }

    /** Resolve a {@link Location} from {@code x y z}, anchored to the world the command source is in. */
    private static ParamResolver<Location> locationResolver() {
        return new ParamResolver<>() {
            @Override
            public ArgumentType<?> argumentType(Arg arg) {
                return ArgumentTypes.finePosition();
            }

            @Override
            public boolean nativeArgument() {
                return true;
            }

            @Override
            public Location resolve(CommandContext<CommandSourceStack> context, String name) {
                FinePositionResolver positions = context.getArgument(name, FinePositionResolver.class);
                CommandSourceStack source = context.getSource();
                try {
                    FinePosition position = positions.resolve(source);
                    World world = source.getLocation().getWorld();
                    return position.toLocation(world);
                } catch (CommandSyntaxException failure) {
                    throw new IllegalArgumentException(failure.getMessage(), failure);
                }
            }
        };
    }

    /**
     * Resolve an {@link OfflinePlayer} from a UUID, or from a name this server already holds.
     *
     * <p>Only what the server has is read: the players online, then the profile cache. Paper's
     * {@code playerProfiles} argument accepts any name at all, and for one it does not hold it sends the
     * calling thread to Mojang for a lookup. Commands run on the main thread, so a mistyped name would
     * freeze the server for the length of a web request, and an unreachable Mojang would freeze it for the
     * length of a timeout. A player this server has never seen also has no stored data to act on, so
     * refusing the name costs nothing that was there to lose.
     *
     * <p>A UUID is accepted as well, because an operator reads one out of a log or a database row for a
     * player whose name the cache has dropped.
     */
    private static ParamResolver<OfflinePlayer> offlinePlayerResolver() {
        return new ParamResolver<>() {
            @Override
            public ArgumentType<?> argumentType(Arg arg) {
                return StringArgumentType.word();
            }

            @Override
            public @Nullable SuggestionSource suggestionSource() {
                return NativeResolvers::onlineNames;
            }

            @Override
            public OfflinePlayer resolve(CommandContext<CommandSourceStack> context, String name) {
                return known(context.getArgument(name, String.class));
            }
        };
    }

    static OfflinePlayer known(String typed) {
        UUID id = asUuid(typed);
        if (id != null) {
            return Bukkit.getOfflinePlayer(id);
        }
        Player online = Bukkit.getPlayerExact(typed);
        if (online != null) {
            return online;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(typed);
        if (cached == null) {
            throw new IllegalArgumentException("no such player: " + typed);
        }
        return cached;
    }

    private static @Nullable UUID asUuid(String typed) {
        try {
            return UUID.fromString(typed);
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    /**
     * Complete against the players online now.
     *
     * <p>A sender is offered only the players they can see, so a vanished player is not revealed by tab
     * completion. Typing the whole name still resolves: hiding somebody from a list is not the same as
     * refusing to act on them.
     */
    static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> onlineNames(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String typed = builder.getRemainingLowerCase();
        CommandSender sender = context.getSource().getSender();
        Player viewer = sender instanceof Player player ? player : null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (viewer != null && !viewer.canSee(player)) {
                continue;
            }
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(typed)) {
                builder.suggest(player.getName());
            }
        }
        return builder.buildFuture();
    }

    /** Resolve a {@link Sound} from the sound-event registry, with the key completion the client knows. */
    private static ParamResolver<Sound> soundResolver() {
        return new ParamResolver<>() {
            @Override
            public ArgumentType<?> argumentType(Arg arg) {
                return ArgumentTypes.resource(RegistryKey.SOUND_EVENT);
            }

            @Override
            public boolean nativeArgument() {
                return true;
            }

            @Override
            public Sound resolve(CommandContext<CommandSourceStack> context, String name) {
                return context.getArgument(name, Sound.class);
            }
        };
    }
}
