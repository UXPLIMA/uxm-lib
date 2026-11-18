package com.uxplima.uxmlib.hook.placeholder;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmlib.hook.PlaceholderApi;

/**
 * The one {@code %uxm_<prefix>_<path>%} expansion every UXM plugin shares. PlaceholderAPI keeps one expansion per
 * identifier and replaces the first with the second, so each plugin registering {@code uxm} on its own would blank
 * the others. Instead the first plugin to {@link #join} registers the expansion and every later one joins it with
 * its own prefix: {@code essentials}, {@code chat}, {@code skyblock}.
 *
 * <pre>{@code
 * PlaceholderRegistry registry = new PlaceholderRegistry()
 *         .register("balance", (player, params) -> ...);
 * SharedPlaceholders.join(plugin, "essentials", registry);   // %uxm_essentials_balance%
 * }</pre>
 *
 * <p>Each plugin shades its own uxmLib, relocated and perhaps at another version, so the plugins share no uxmLib
 * class: a service keyed by one, or a cast to one, would fail between them. They meet on types they all load from one
 * place instead. The expansion PlaceholderAPI holds as {@code uxm} hands out its member table as a
 * {@link java.util.function.Supplier} of a {@link java.util.concurrent.ConcurrentMap}, and each member is a
 * PlaceholderAPI {@code PlaceholderExpansion} that is never registered itself. No reflection is involved, and every
 * uxmLib version keeps that contract.
 *
 * <p>A plugin leaves when it is disabled, with nothing to call. When the plugin that registered the expansion goes,
 * the plugins that stay register their own over the same table, so their placeholders keep answering, and a
 * PlaceholderAPI that lost the expansion in a reload has it registered again. A prefix another plugin holds is
 * refused with a line in the log, and the plugin holding it keeps answering.
 */
public final class SharedPlaceholders {

    /** The identifier every UXM plugin shares. */
    public static final String IDENTIFIER = SharedTable.IDENTIFIER;

    /** A prefix is one word: the text up to the first underscore is what picks the plugin. */
    private static final Pattern PREFIX = Pattern.compile("[a-z0-9]+");

    private SharedPlaceholders() {}

    /**
     * Answer {@code %uxm_<prefix>_<path>%} through {@code registry}, which is asked with {@code <path>}, and the
     * relational {@code %rel_uxm_<prefix>_<path>%} through its relational provider.
     *
     * @param prefix the plugin's word, in lower-case letters and digits
     * @return how to leave before the plugin is disabled, or empty when PlaceholderAPI is not installed, another
     *     plugin holds the prefix, or {@code uxm} is held by an expansion that is not the shared one; the log says
     *     which
     */
    public static Optional<Runnable> join(Plugin plugin, String prefix, PlaceholderRegistry registry) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(registry, "registry");
        if (!PREFIX.matcher(prefix).matches()) {
            throw new IllegalArgumentException("a prefix is lower-case letters and digits, got: " + prefix);
        }
        if (!PlaceholderApi.isAvailable()) {
            return Optional.empty();
        }
        return SharedPlaceholderHook.join(plugin, prefix, registry);
    }
}
