package com.uxplima.uxmlib.packet.tablist;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A player-skin texture, expressed as the two pieces Mojang's profile property carries: the base64-encoded
 * texture value and its optional Yggdrasil signature. This is a pure value (no NMS, no profile type), so the
 * port and its tests stay server-free; the NMS builder turns it into a {@code GameProfile} {@code "textures"}
 * property when it assembles an add-entry packet.
 *
 * @param textureValue the base64-encoded texture payload (the {@code value} of the {@code textures} property)
 * @param signature the property's signature, or {@code null} for an unsigned texture
 */
public record TabSkin(String textureValue, @Nullable String signature) {

    /**
     * The {@code textures} value of the default classic ("Steve") skin: a base64-encoded
     * {@code {"textures":{"SKIN":{"url":…}}}} pointing at Mojang's long-standing default skin texture
     * ({@code textures.minecraft.net/texture/1a4af718…}). A fake player spawned through the add-entity packet on
     * 1.20.2+ links its body to its player-info entry's profile, and clients drop a profile that carries no
     * {@code textures} property, so a skinless NPC must still ship a texture to render at all. It is unsigned: a
     * synthetic NPC profile cannot bear Mojang's signature, which clients accept for an own-server entity.
     */
    public static final TabSkin DEFAULT = TabSkin.unsigned(
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWE0YWY3MTg0NTVkNGFhYjUyOGU3YTYxZjg2ZmEyNWU2YTM2OWQxNzY4ZGNiMTNmN2RmMzE5YTcxM2ViODEwYiJ9fX0=");

    public TabSkin {
        Objects.requireNonNull(textureValue, "textureValue");
    }

    /** A skin with no signature; the client accepts the texture unsigned for synthetic tab entries. */
    public static TabSkin unsigned(String textureValue) {
        return new TabSkin(textureValue, null);
    }

    /** {@code skin} when present, otherwise {@link #DEFAULT}, so a player-info ADD always carries a texture. */
    public static TabSkin orDefault(@Nullable TabSkin skin) {
        return skin != null ? skin : DEFAULT;
    }
}
