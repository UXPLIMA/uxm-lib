package com.uxplima.uxmlib.board;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A skin a plugin keeps for a player, as Mojang hands it out: the base64 {@code textures} property value, whose
 * skin URL is on {@code textures.minecraft.net}, and its signature when the plugin has one.
 *
 * <p>The board draws it through {@link BoardAnswers#headComponent}, as a head made from that property. A
 * {@code <head:...>} tag cannot carry one: it takes a name, an id or an asset path.
 *
 * @param value the {@code textures} property value; never blank
 * @param signature Mojang's signature of {@code value}, or {@code null} for an unsigned skin
 */
public record StoredSkin(String value, @Nullable String signature) {

    public StoredSkin {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("a stored skin needs its textures value");
        }
        if (signature != null && signature.isBlank()) {
            signature = null;
        }
    }

    /** An unsigned skin. */
    public static StoredSkin unsigned(String value) {
        return new StoredSkin(value, null);
    }
}
