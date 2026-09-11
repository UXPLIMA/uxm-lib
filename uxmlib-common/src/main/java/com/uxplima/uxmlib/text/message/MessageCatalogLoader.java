package com.uxplima.uxmlib.text.message;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.spongepowered.configurate.ConfigurationNode;

/**
 * Builds a {@link MessageCatalog} from one Configurate tree per locale, flattening each tree into the
 * {@code path -> template} map the catalog resolves against. A tree is walked depth-first; every leaf becomes
 * one entry whose path is its dotted key chain, so {@code join { welcome = "..." }} yields the path
 * {@code join.welcome}.
 *
 * <p>A leaf is read through Configurate's own coercion, so it need not be quoted: {@code count = 7} and
 * {@code flag = true} become the templates {@code "7"} and {@code "true"}. Only a section contributes no
 * entry of its own: {@code channel { type = ..., text = ... }} yields {@code channel.type} and
 * {@code channel.text}, and nothing answers to {@code channel}. A lang file may therefore carry a structured
 * section beside its messages, but the flat lookup reaches its leaves, never the section itself.
 */
public final class MessageCatalogLoader {

    private MessageCatalogLoader() {}

    /**
     * Build a catalog from {@code trees}, one per locale.
     *
     * @param trees a locale mapped to that locale's loaded HOCON root node
     * @param defaultLocale the fallback locale (need not be present in {@code trees})
     */
    public static MessageCatalog fromNodes(Map<Locale, ConfigurationNode> trees, Locale defaultLocale) {
        Objects.requireNonNull(trees, "trees");
        Objects.requireNonNull(defaultLocale, "defaultLocale");
        Map<Locale, Map<String, String>> templates = new HashMap<>();
        for (var entry : trees.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "locale");
            Map<String, String> flat = new LinkedHashMap<>();
            flatten(entry.getValue(), "", flat);
            templates.put(entry.getKey(), flat);
        }
        return new MessageCatalog(templates, defaultLocale);
    }

    /**
     * One loaded tree, flattened to the {@code path -> template} map the catalog holds, as an immutable copy.
     *
     * <p>The same walk {@link #fromNodes} does, for a caller holding one tree rather than a set: a plugin's
     * language layer wants the flat map itself as well as the catalog built from it, and had its own copy of
     * this walk until the two were made one.
     */
    public static Map<String, String> flatten(ConfigurationNode node) {
        Objects.requireNonNull(node, "node");
        Map<String, String> flat = new LinkedHashMap<>();
        flatten(node, "", flat);
        return Map.copyOf(flat);
    }

    private static void flatten(ConfigurationNode node, String prefix, Map<String, String> out) {
        if (node.isMap()) {
            for (var child : node.childrenMap().entrySet()) {
                flatten(child.getValue(), join(prefix, String.valueOf(child.getKey())), out);
            }
            return;
        }
        String value = node.getString();
        if (value != null && !prefix.isEmpty()) {
            out.put(prefix, value);
        }
    }

    private static String join(String prefix, String key) {
        return prefix.isEmpty() ? key : prefix + "." + key;
    }
}
