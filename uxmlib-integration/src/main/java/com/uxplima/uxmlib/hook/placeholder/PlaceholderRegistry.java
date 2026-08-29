package com.uxplima.uxmlib.hook.placeholder;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import org.jspecify.annotations.Nullable;

/**
 * Holds the {@link PlaceholderProvider}s a plugin exposes through PlaceholderAPI and routes a request to the
 * right one by longest-prefix match. An instance, not static state, so each consumer owns its own set; the
 * shared {@code UxmPlaceholderExpansion} delegates every request to {@link #resolve(Player, String)}.
 *
 * <p>{@link #resolve(Player, String)} is deliberately Bukkit-light and free of any {@code me.clip} symbol so
 * the dispatch can be unit-tested without PlaceholderAPI on the path. It is exception-proof: a provider that
 * throws yields an empty value rather than propagating into PlaceholderAPI.
 */
public final class PlaceholderRegistry {

    private final Map<String, PlaceholderProvider> providers = new ConcurrentHashMap<>();

    /**
     * Register {@code provider} under {@code prefix} (the {@code <prefix>} in {@code %uxm_<prefix>_...%}).
     * A later registration with the same prefix replaces the earlier one. Returns this for chaining.
     */
    public PlaceholderRegistry register(String prefix, PlaceholderProvider provider) {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(provider, "provider");
        if (prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        providers.put(prefix, provider);
        return this;
    }

    /** Remove the provider registered under {@code prefix}; returns whether one was present. */
    public boolean unregister(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        return providers.remove(prefix) != null;
    }

    /** Whether any provider is registered. */
    public boolean isEmpty() {
        return providers.isEmpty();
    }

    /**
     * Resolve the part of a {@code %uxm_...%} placeholder after the {@code uxm_} root: i.e. an identifier of
     * the form {@code <prefix>_<params>} (or just {@code <prefix>}). Returns the provider's value, an empty
     * string if the provider returns {@code null} or throws, or {@code null} when no prefix matches so
     * PlaceholderAPI falls through to other expansions.
     */
    public @Nullable String resolve(@Nullable Player player, String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        String prefix = longestMatchingPrefix(identifier);
        if (prefix == null) {
            return null;
        }
        return invoke(providers.get(prefix), player, paramsAfter(prefix, identifier));
    }

    private @Nullable String longestMatchingPrefix(String identifier) {
        String best = null;
        for (String prefix : providers.keySet()) {
            if (matches(identifier, prefix) && (best == null || prefix.length() > best.length())) {
                best = prefix;
            }
        }
        return best;
    }

    private static boolean matches(String identifier, String prefix) {
        // A prefix matches either the whole identifier or its leading "<prefix>_" segment, never a partial
        // word: "eco" must not claim "economy_top".
        return identifier.equals(prefix) || identifier.startsWith(prefix + "_");
    }

    private static String paramsAfter(String prefix, String identifier) {
        return identifier.length() == prefix.length() ? "" : identifier.substring(prefix.length() + 1);
    }

    private static @Nullable String invoke(
            @Nullable PlaceholderProvider provider, @Nullable Player player, String params) {
        if (provider == null) {
            return null;
        }
        try {
            String value = provider.onRequest(player, params);
            return value == null ? "" : value;
        } catch (RuntimeException failure) {
            // A throwing provider must never break PlaceholderAPI's whole render; swallow to empty.
            return "";
        }
    }
}
