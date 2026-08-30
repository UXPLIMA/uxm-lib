package com.uxplima.uxmlib.hook.placeholder;

import org.bukkit.OfflinePlayer;

import org.jspecify.annotations.Nullable;

/**
 * A consumer-supplied resolver for one placeholder namespace. Registered under a {@code prefix}, it answers
 * the remainder of a {@code %uxm_<prefix>_<params>%} request: PlaceholderAPI strips the {@code uxm_} and
 * {@code prefix_}, and the {@link com.uxplima.uxmlib.hook.placeholder.PlaceholderRegistry} hands the rest to
 * {@link #onRequest(OfflinePlayer, String)}.
 *
 * <p>Returning {@code null} (or an empty string) means "I don't handle this": the registry yields an empty
 * value so the placeholder renders as nothing rather than breaking the line. The {@code player} may be
 * {@code null} when PlaceholderAPI resolves a player-independent placeholder.
 *
 * <p>The subject is an {@link OfflinePlayer} because that is what PlaceholderAPI asks about. A leaderboard
 * line, a tab-list entry and a hologram all name players who are not on the server, and a provider that
 * answers from stored data can serve them. A provider that needs the live player narrows the subject
 * itself:
 *
 * <pre>{@code
 * registry.register("kit", (player, params) ->
 *         player instanceof Player online ? online.getInventory().getHeldItemSlot() + "" : null);
 * }</pre>
 */
@FunctionalInterface
public interface PlaceholderProvider {

    /**
     * Resolve {@code params} (everything after this provider's prefix) for {@code player}, who may be
     * offline. Return the value, or {@code null} for an unrecognised request. Must not throw, but if it
     * does, the registry catches it and yields empty, so a buggy provider never breaks the whole expansion.
     *
     * <p>PlaceholderAPI calls this wherever the caller happens to be, including the main thread on every
     * scoreboard tick, so answer from memory. Never reach for the database, and never call an
     * {@link OfflinePlayer} method that goes to the network for a name.
     */
    @Nullable String onRequest(@Nullable OfflinePlayer player, String params);
}
