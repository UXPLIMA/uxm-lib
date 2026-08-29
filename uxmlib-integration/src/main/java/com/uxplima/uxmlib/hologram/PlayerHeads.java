package com.uxplima.uxmlib.hologram;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

/**
 * Builds {@code PLAYER_HEAD} item stacks carrying a specific skin, for floating head holograms. The skin
 * is set through Paper's native {@link PlayerProfile}: by account UUID, or by a base64 {@code textures}
 * value (the payload of a skin's textures property), so no packets are involved. Spawn the result with
 * {@link Holograms#spawnItem}.
 */
final class PlayerHeads {

    private PlayerHeads() {}

    /** A head showing the skin of the account with {@code uuid}. */
    static ItemStack ofUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return withProfile(Bukkit.createProfile(uuid));
    }

    /** A head showing the skin described by the base64 {@code textures} value. */
    static ItemStack fromTexture(String base64) {
        Objects.requireNonNull(base64, "base64");
        if (base64.isBlank()) {
            throw new IllegalArgumentException("texture must not be blank");
        }
        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
        profile.setProperty(new ProfileProperty("textures", base64));
        return withProfile(profile);
    }

    /**
     * A head showing the skin at {@code skinUrl} (a {@code textures.minecraft.net} URL). The URL is wrapped
     * into the base64 {@code textures} envelope by {@link SkinTextures}, so this is just a convenience over
     * {@link #fromTexture(String)} for callers that hold a bare URL rather than a pre-encoded blob.
     */
    static ItemStack fromSkinUrl(String skinUrl) {
        return fromTexture(SkinTextures.encode(skinUrl));
    }

    private static ItemStack withProfile(PlayerProfile profile) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) Objects.requireNonNull(head.getItemMeta(), "skull meta");
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }
}
