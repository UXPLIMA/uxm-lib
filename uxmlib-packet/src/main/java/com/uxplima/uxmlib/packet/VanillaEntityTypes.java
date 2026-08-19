package com.uxplima.uxmlib.packet;

import java.util.Objects;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

/**
 * Resolves an entity-type key to the server's own entity type.
 *
 * <p>Reading the registry rather than naming a constant is deliberate. The class the constants are declared on
 * has been renamed across Minecraft lines while the registry and the keys in it have not, so one lookup keeps
 * the packet builders off a moving part without needing a per-line adapter for it.
 */
public final class VanillaEntityTypes {

    private VanillaEntityTypes() {}

    /**
     * The entity type registered under {@code key}. A key with no namespace is read as {@code minecraft}, so
     * {@code "villager"} and {@code "minecraft:villager"} resolve alike.
     *
     * @throws IllegalArgumentException if the key is unparseable or nothing is registered under it
     */
    public static EntityType<?> of(String key) {
        Objects.requireNonNull(key, "key");
        Identifier id = key.indexOf(Identifier.NAMESPACE_SEPARATOR) < 0
                ? Identifier.withDefaultNamespace(key)
                : Identifier.tryParse(key);
        EntityType<?> type = id == null
                ? null
                : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null) {
            throw new IllegalArgumentException("Unknown entity type key: " + key);
        }
        return type;
    }
}
