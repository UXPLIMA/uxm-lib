package com.uxplima.uxmlib.packet.tablist;

import java.util.Objects;
import java.util.UUID;

import net.kyori.adventure.text.Component;

import org.jspecify.annotations.Nullable;

/**
 * The complete public state needed to add a synthetic player-info entry, expressed without NMS types.
 *
 * <p>Unlike the older {@link TabEntry} convenience value, this model does not silently hard-code latency,
 * game mode, listed state or hat visibility. It is therefore suitable for any packet consumer that owns a
 * synthetic profile end to end, not only a particular tab-list layout implementation.
 *
 * @param id the synthetic profile id
 * @param displayName the component displayed by the client
 * @param listOrder the modern client-side list sort key
 * @param skin the profile texture, or {@code null} for a profile without one
 * @param name the profile name, or {@code null} to derive a safe name from {@code id}
 * @param listed whether the entry is visible in the player list
 * @param latency the latency value displayed by clients that render ping
 * @param gameMode the game mode carried by the player-info entry
 * @param showHat whether the skin's hat layer is shown
 */
public record PlayerInfoEntry(
        UUID id,
        Component displayName,
        int listOrder,
        @Nullable TabSkin skin,
        @Nullable String name,
        boolean listed,
        int latency,
        PlayerInfoGameMode gameMode,
        boolean showHat) {

    private static final int MAX_PROFILE_NAME_LENGTH = 16;

    public PlayerInfoEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(gameMode, "gameMode");
    }

    /** A listed synthetic entry with the conventional harmless protocol defaults. */
    public static PlayerInfoEntry of(UUID id, Component displayName) {
        return new PlayerInfoEntry(id, displayName, 0, null, null, true, 0, PlayerInfoGameMode.SURVIVAL, true);
    }

    /** Upgrade the backwards-compatible tab entry value without changing its historical defaults. */
    public static PlayerInfoEntry from(TabEntry entry) {
        Objects.requireNonNull(entry, "entry");
        return new PlayerInfoEntry(
                entry.id(),
                entry.displayName(),
                entry.listOrder(),
                entry.skin(),
                entry.name(),
                true,
                0,
                PlayerInfoGameMode.SURVIVAL,
                true);
    }

    /** A valid profile name, capped at Mojang's sixteen-character profile-name limit. */
    public String profileName() {
        String resolved = name != null ? name : id.toString();
        return resolved.length() <= MAX_PROFILE_NAME_LENGTH ? resolved : resolved.substring(0, MAX_PROFILE_NAME_LENGTH);
    }
}
