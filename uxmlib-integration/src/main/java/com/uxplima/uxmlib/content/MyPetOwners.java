package com.uxplima.uxmlib.content;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.entity.Entity;

/**
 * {@link PetOwners} over MyPet.
 *
 * <p>MyPet's pets are entities of its own that Bukkit's own {@code Tameable} does not answer for, which is
 * why a plugin that checked only the vanilla question paid nothing for them.
 *
 * <p>Reflective, like every provider in this package.
 */
public final class MyPetOwners implements PetOwners {

    /** The Bukkit plugin name, which is the whole of the present guard. */
    static final String PLUGIN = "MyPet";

    private static final String BRIDGE = "de.Keyle.MyPet.api.entity.MyPetBukkitEntity";

    private final Server server;

    public MyPetOwners(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public boolean active() {
        return Reflected.enabled(server, PLUGIN) && Reflected.type(BRIDGE).isPresent();
    }

    @Override
    public Optional<UUID> ownerOf(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        if (!active()) {
            return Optional.empty();
        }
        Class<?> bridge = Reflected.type(BRIDGE).orElse(null);
        if (bridge == null || !bridge.isInstance(entity)) {
            return Optional.empty();
        }
        return Reflected.method(BRIDGE, "getMyPet")
                .flatMap(method -> Reflected.call(method, entity))
                .flatMap(MyPetOwners::ownerOfPet);
    }

    /** {@code pet.getOwner().getPlayerUUID()}, in two hops because neither type may be named here. */
    private static Optional<UUID> ownerOfPet(Object pet) {
        return Reflected.method(pet.getClass().getName(), "getOwner")
                .flatMap(method -> Reflected.call(method, pet))
                .flatMap(owner -> Reflected.method(owner.getClass().getName(), "getPlayerUUID")
                        .flatMap(method -> Reflected.call(method, owner)))
                .filter(UUID.class::isInstance)
                .map(UUID.class::cast);
    }
}
