package com.uxplima.uxmlib.content;

import java.util.Locale;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;

/**
 * {@link SkillLevels} over McMMO.
 *
 * <p>The skill plugin the market has run for fifteen years, and the one a server most likely already has a
 * progression in when it installs ours. Reading it lets an operator price a row against work a player has
 * already done, rather than starting them at nothing in a second ladder.
 *
 * <p>Reflective, like every provider in this package: no McMMO type is named here, so none of its classes
 * loads on a server without it.
 */
public final class McMmoSkillLevels implements SkillLevels {

    /** The Bukkit plugin name, which is the whole of the present guard. */
    static final String PLUGIN = "mcMMO";

    private static final String API = "com.gmail.nossr50.api.ExperienceAPI";

    private final Server server;

    public McMmoSkillLevels(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public boolean active() {
        return Reflected.enabled(server, PLUGIN) && Reflected.type(API).isPresent();
    }

    @Override
    public OptionalInt levelOf(UUID player, String skill) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(skill, "skill");
        if (!active() || skill.isBlank()) {
            return OptionalInt.empty();
        }
        OfflinePlayer who = server.getOfflinePlayer(player);
        return Reflected.method(API, "getLevelOffline", "org.bukkit.OfflinePlayer", "java.lang.String")
                .flatMap(method -> Reflected.call(method, null, who, skill.toUpperCase(Locale.ROOT)))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::intValue)
                .map(OptionalInt::of)
                .orElseGet(OptionalInt::empty);
    }
}
