package com.uxplima.uxmlib.hook.placeholder;

import java.util.Objects;

import org.bukkit.OfflinePlayer;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.jspecify.annotations.Nullable;

/**
 * The single shared PlaceholderAPI expansion that backs every registered {@link PlaceholderProvider}. It is
 * package-private and instantiated only from {@link PlaceholderExpansions#register}, which runs past a
 * PlaceholderAPI-present guard, so the {@code me.clip} superclass is never resolved on a server without the
 * plugin. Each request is routed by {@link PlaceholderRegistry#resolve}, which is itself exception-proof.
 *
 * <p>{@link #persist()} is {@code true} so the expansion survives {@code /papi reload} and keeps serving the
 * consumer's providers without re-registration.
 */
final class UxmPlaceholderExpansion extends PlaceholderExpansion {

    private final String identifier;
    private final String author;
    private final String version;
    private final PlaceholderRegistry registry;

    UxmPlaceholderExpansion(String identifier, String author, String version, PlaceholderRegistry registry) {
        this.identifier = Objects.requireNonNull(identifier, "identifier");
        this.author = Objects.requireNonNull(author, "author");
        this.version = Objects.requireNonNull(version, "version");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public String getAuthor() {
        return author;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer requester, String params) {
        // PlaceholderAPI passes the text after the identifier; the registry routes it by longest-prefix match.
        // The requester is handed on as it came: a provider that needs the live player narrows it itself,
        // and one that answers from stored data can serve a player who is not on the server.
        return registry.resolve(requester, params);
    }
}
