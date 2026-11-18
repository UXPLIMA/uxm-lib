package com.uxplima.uxmlib.hook.placeholder;

import java.util.Objects;
import java.util.function.BiFunction;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import org.jspecify.annotations.Nullable;

/**
 * One plugin's prefix in the shared {@code uxm} table. It is a {@link PlaceholderExpansion} only because that is a type
 * every plugin shares, so the expansion of another plugin can call it; it is never registered with PlaceholderAPI.
 */
final class SharedMember extends PlaceholderExpansion implements Relational {

    private final String prefix;
    private final String owner;
    private final String version;
    private final BiFunction<@Nullable OfflinePlayer, String, @Nullable String> answer;
    private final Relational relational;

    SharedMember(
            String prefix,
            String owner,
            String version,
            BiFunction<@Nullable OfflinePlayer, String, @Nullable String> answer,
            Relational relational) {
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.version = Objects.requireNonNull(version, "version");
        this.answer = Objects.requireNonNull(answer, "answer");
        this.relational = Objects.requireNonNull(relational, "relational");
    }

    @Override
    public String getIdentifier() {
        return prefix;
    }

    @Override
    public String getAuthor() {
        return owner;
    }

    @Override
    public String getVersion() {
        return version;
    }

    /** The plugin that joined: the contract names a member's owner here. */
    @Override
    public String getRequiredPlugin() {
        return owner;
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer requester, String params) {
        return answer.apply(requester, params);
    }

    @Override
    public @Nullable String onPlaceholderRequest(@Nullable Player viewer, @Nullable Player target, String params) {
        if (viewer == null || target == null) {
            return null;
        }
        return relational.onPlaceholderRequest(viewer, target, params);
    }
}
