package com.uxplima.uxmlib.content;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Server;
import org.bukkit.entity.Entity;

/**
 * CustomMobs over RoseStacker.
 *
 * <p>RoseStacker is the count half and not the id half. A player who killed a stack of forty killed forty, and a plugin that pays for one of them is a plugin the server's players will notice within an hour.
 *
 * <p>Reflective, like every provider in this package: not one RoseStacker type is named in a signature or a
 * field, so not one of its classes loads on a server that does not run it.
 */
public final class RoseStackerCustomMobs implements CustomMobs {

    /** The Bukkit plugin name, which is the whole of the present guard. */
    static final String PLUGIN = "RoseStacker";

    /** The namespace an id of this vendor's carries. */
    static final String NAMESPACE = "rosestacker";

    private static final String API = "dev.rosewood.rosestacker.api.RoseStackerAPI";

    private final Server server;

    public RoseStackerCustomMobs(Server server) {
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
        return Reflected.method(API, "getStackedEntityType", "org.bukkit.entity.Entity")
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
        return Reflected.method(API, "getStackedEntitySize", "org.bukkit.entity.Entity")
                .flatMap(method -> Reflected.call(method, null, entity))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::intValue)
                .filter(count -> count > 0)
                .orElse(1);
    }
}
