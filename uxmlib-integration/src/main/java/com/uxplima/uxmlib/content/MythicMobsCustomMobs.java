package com.uxplima.uxmlib.content;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Server;
import org.bukkit.entity.Entity;

/**
 * CustomMobs over MythicMobs.
 *
 * <p>MythicMobs is the one every server in this market runs, and the one a jobs file most wants to price separately: a boss is not a zombie and paying the same for both is the defect. It stacks nothing, so the count question always answers one and the stacker beside it answers the rest.
 *
 * <p>Reflective, like every provider in this package: not one MythicMobs type is named in a signature or a
 * field, so not one of its classes loads on a server that does not run it.
 */
public final class MythicMobsCustomMobs implements CustomMobs {

    /** The Bukkit plugin name, which is the whole of the present guard. */
    static final String PLUGIN = "MythicMobs";

    /** The namespace an id of this vendor's carries. */
    static final String NAMESPACE = "mythicmobs";

    private static final String API = "io.lumine.mythic.bukkit.MythicBukkit";

    private final Server server;

    public MythicMobsCustomMobs(Server server) {
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
        return Reflected.method(API, "getMythicTypeFromEntity", "org.bukkit.entity.Entity")
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
