package com.uxplima.uxmlib.hook.placeholder;

import org.bukkit.entity.Player;

import org.jspecify.annotations.Nullable;

/**
 * A consumer-supplied resolver for one placeholder namespace. Registered under a {@code prefix}, it answers
 * the remainder of a {@code %uxm_<prefix>_<params>%} request: PlaceholderAPI strips the {@code uxm_} and
 * {@code prefix_}, and the {@link com.uxplima.uxmlib.hook.placeholder.PlaceholderRegistry} hands the rest to
 * {@link #onRequest(Player, String)}.
 *
 * <p>Returning {@code null} (or an empty string) means "I don't handle this": the registry yields an empty
 * value so the placeholder renders as nothing rather than breaking the line. The {@code player} may be
 * {@code null} when PlaceholderAPI resolves a player-independent placeholder.
 */
@FunctionalInterface
public interface PlaceholderProvider {

    /**
     * Resolve {@code params} (everything after this provider's prefix) for {@code player}. Return the value,
     * or {@code null} for an unrecognised request. Must not throw, but if it does, the registry catches it
     * and yields empty, so a buggy provider never breaks the whole expansion.
     */
    @Nullable String onRequest(@Nullable Player player, String params);
}
