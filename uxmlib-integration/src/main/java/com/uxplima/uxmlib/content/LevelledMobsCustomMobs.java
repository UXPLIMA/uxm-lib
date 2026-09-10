package com.uxplima.uxmlib.content;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Server;
import org.bukkit.entity.Entity;

/**
 * CustomMobs over LevelledMobs.
 *
 * <p>LevelledMobs gives an ordinary mob a level, so a level twenty zombie is worth pricing apart from a level one. The id it answers with is that level, namespaced, so a file writes levelledmobs:20 and means it.
 *
 * <p>Reflective, like every provider in this package: not one LevelledMobs type is named in a signature or a
 * field, so not one of its classes loads on a server that does not run it.
 */
public final class LevelledMobsCustomMobs implements CustomMobs {

    /** The Bukkit plugin name, which is the whole of the present guard. */
    static final String PLUGIN = "LevelledMobs";

    /** The namespace an id of this vendor's carries. */
    static final String NAMESPACE = "levelledmobs";

    private static final String API = "io.github.arcaneplugins.levelledmobs.LevelledMobs";

    private final Server server;

    public LevelledMobsCustomMobs(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public boolean active() {
        return Reflected.enabled(server, PLUGIN) && Reflected.type(API).isPresent();
    }

    @Override
    public Optional<String> idOf(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        if (!active()) {
            return Optional.empty();
        }
        return Reflected.method(API, "getLevelOfMob", "org.bukkit.entity.Entity")
                .flatMap(method -> Reflected.callForString(method, null, entity))
                .filter(id -> !id.isBlank())
                .map(id -> NAMESPACE + ":" + id);
    }

    @Override
    public int countOf(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        if (!active()) {
            return 1;
        }
        return Reflected.method(API, "stackSizeOf", "org.bukkit.entity.Entity")
                .flatMap(method -> Reflected.call(method, null, entity))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::intValue)
                .filter(count -> count > 0)
                .orElse(1);
    }
}
