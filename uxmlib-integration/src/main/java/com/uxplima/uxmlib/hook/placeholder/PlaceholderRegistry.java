package com.uxplima.uxmlib.hook.placeholder;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.OfflinePlayer;

import org.jspecify.annotations.Nullable;

/**
 * Holds the {@link PlaceholderProvider}s a plugin exposes through PlaceholderAPI and routes a request to the
 * right one by longest-prefix match, or to the {@link #fallback} when no prefix claims it. An instance, not static state, so each consumer owns its own set; the
 * shared {@code UxmPlaceholderExpansion} delegates every request to {@link #resolve(OfflinePlayer, String)}.
 *
 * <p>{@link #resolve(OfflinePlayer, String)} is deliberately Bukkit-light and free of any {@code me.clip} symbol so
 * the dispatch can be unit-tested without PlaceholderAPI on the path. It is exception-proof: a provider that
 * throws yields an empty value rather than propagating into PlaceholderAPI.
 */
public final class PlaceholderRegistry {

    private final Map<String, PlaceholderProvider> providers = new ConcurrentHashMap<>();
    private volatile @Nullable PlaceholderProvider fallback;

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

    /**
     * Register {@code provider} as the answer for every identifier no prefix claims, replacing any earlier
     * fallback. Returns this for chaining.
     *
     * <p>This is what a plugin with flat placeholders wants: {@code %uxmtags_has%} and {@code %uxmtags_id%}
     * carry no namespace of their own, so there is no prefix to route on, and the provider switches on the
     * whole parameter itself.
     *
     * <p>A fallback that returns {@code null} means "no such placeholder", and the expansion says so by
     * answering {@code null}: PlaceholderAPI then leaves the text exactly as the operator wrote it, which is
     * how they find their own typo. That is the one place where {@code null} does not become an empty value,
     * because a prefixed provider has already claimed its namespace and a blank is the right answer there.
     */
    public PlaceholderRegistry fallback(PlaceholderProvider provider) {
        Objects.requireNonNull(provider, "provider");
        this.fallback = provider;
        return this;
    }

    /** Remove the provider registered under {@code prefix}; returns whether one was present. */
    public boolean unregister(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        return providers.remove(prefix) != null;
    }

    /** Whether any provider is registered, fallback included. */
    public boolean isEmpty() {
        return providers.isEmpty() && fallback == null;
    }

    /**
     * Resolve the part of a {@code %uxm_...%} placeholder after the {@code uxm_} root: i.e. an identifier of
     * the form {@code <prefix>_<params>} (or just {@code <prefix>}). Returns the provider's value, an empty
     * string if the provider returns {@code null} or throws, or {@code null} when no prefix matches so
     * PlaceholderAPI falls through to other expansions.
     */
    public @Nullable String resolve(@Nullable OfflinePlayer player, String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        String prefix = longestMatchingPrefix(identifier);
        if (prefix == null) {
            return answerFallback(player, identifier);
        }
        return invoke(providers.get(prefix), player, paramsAfter(prefix, identifier));
    }

    /**
     * The fallback's answer, kept as it came: {@code null} for a placeholder it does not know, so
     * PlaceholderAPI leaves the text written as it is.
     */
    private @Nullable String answerFallback(@Nullable OfflinePlayer player, String identifier) {
        PlaceholderProvider provider = fallback;
        if (provider == null) {
            return null;
        }
        try {
            return provider.onRequest(player, identifier);
        } catch (RuntimeException failure) {
            // A throwing provider must never break PlaceholderAPI's whole render; swallow to empty.
            return "";
        }
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
            @Nullable PlaceholderProvider provider, @Nullable OfflinePlayer player, String params) {
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
