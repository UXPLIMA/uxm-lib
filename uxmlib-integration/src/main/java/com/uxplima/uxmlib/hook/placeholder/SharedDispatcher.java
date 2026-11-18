package com.uxplima.uxmlib.hook.placeholder;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import org.jspecify.annotations.Nullable;

/**
 * The expansion PlaceholderAPI holds as {@code uxm}, routing {@code <prefix>_<path>} to the member that joined with
 * that prefix. It gives the member table through {@link Supplier}, a JDK type, which is how a plugin with its own copy
 * of uxmLib finds and joins it. It names no required plugin, so PlaceholderAPI never takes it down with the plugin
 * that registered it; the plugins that stay register their own in its place.
 *
 * <p>{@link #persist()} is true, so {@code /papi reload} keeps it.
 */
final class SharedDispatcher extends PlaceholderExpansion
        implements Relational, Supplier<ConcurrentMap<String, PlaceholderExpansion>> {

    private final ConcurrentMap<String, PlaceholderExpansion> members;
    private final String host;
    private final String version;

    SharedDispatcher(ConcurrentMap<String, PlaceholderExpansion> members, String host, String version) {
        this.members = Objects.requireNonNull(members, "members");
        this.host = Objects.requireNonNull(host, "host");
        this.version = Objects.requireNonNull(version, "version");
    }

    /** The member table every plugin in the shared expansion writes to. */
    @Override
    public ConcurrentMap<String, PlaceholderExpansion> get() {
        return members;
    }

    @Override
    public String getIdentifier() {
        return SharedTable.IDENTIFIER;
    }

    @Override
    public String getAuthor() {
        return "UXPLIMA (" + host + ")";
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
        int cut = params.indexOf('_');
        @Nullable PlaceholderExpansion member = memberFor(params, cut);
        if (member == null) {
            return null;
        }
        try {
            return member.onRequest(requester, rest(params, cut));
        } catch (RuntimeException failure) {
            // One plugin's failing answer must never break the line PlaceholderAPI is drawing.
            return "";
        }
    }

    @Override
    public @Nullable String onPlaceholderRequest(@Nullable Player viewer, @Nullable Player target, String params) {
        int cut = params.indexOf('_');
        @Nullable PlaceholderExpansion member = memberFor(params, cut);
        if (viewer == null || target == null || !(member instanceof Relational relational)) {
            return null;
        }
        try {
            return relational.onPlaceholderRequest(viewer, target, rest(params, cut));
        } catch (RuntimeException failure) {
            return "";
        }
    }

    private @Nullable PlaceholderExpansion memberFor(String params, int cut) {
        String prefix = cut < 0 ? params : params.substring(0, cut);
        return members.get(prefix.toLowerCase(Locale.ROOT));
    }

    private static String rest(String params, int cut) {
        return cut < 0 ? "" : params.substring(cut + 1);
    }
}
