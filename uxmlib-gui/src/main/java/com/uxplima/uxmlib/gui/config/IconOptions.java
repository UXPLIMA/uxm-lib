package com.uxplima.uxmlib.gui.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.MessageKey;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * The parsed, still-mergeable description of a config-defined menu icon, before it becomes an
 * {@link ItemStack}. Splitting the parse from the build buys two things the bare {@link ItemStack} could
 * not give: a state variant can be {@linkplain #combine overlaid} on a base icon so only the fields that
 * differ are repeated in config, and the display text can be drawn from a lang file by key rather than
 * inlined: a {@code name-key} or {@code lore-key} is resolved through an {@link MessageCatalog} at build
 * time, keeping translation separate from the menu layout.
 *
 * <p>Every field is nullable and means "unspecified" when absent, so {@link #combine} can tell a value the
 * override sets apart from one it leaves to the base. A {@code null} field falls back to a sane default only
 * at {@link #toItem} time (material defaults to {@code STONE}, amount to one).
 */
public record IconOptions(
        @Nullable Material material,
        @Nullable String name,
        @Nullable List<String> lore,
        @Nullable Integer amount,
        @Nullable String nameKey,
        @Nullable String loreKey) {

    public IconOptions {
        lore = lore == null ? null : List.copyOf(lore);
    }

    /** An empty options object (every field unspecified), for callers that build one up by combining. */
    public static IconOptions empty() {
        return new IconOptions(null, null, null, null, null, null);
    }

    /**
     * Parse one icon spec from {@code node}: a {@code material}, optional MiniMessage {@code name}/{@code
     * lore}, an {@code amount}, and optional {@code name-key}/{@code lore-key} lang references. An absent
     * field stays {@code null} so the result still merges cleanly.
     */
    public static IconOptions read(ConfigurationNode node) {
        Objects.requireNonNull(node, "node");
        return new IconOptions(
                parseMaterial(node.node("material").getString()),
                node.node("name").getString(),
                readStringList(node.node("lore")),
                node.node("amount").virtual() ? null : node.node("amount").getInt(),
                node.node("name-key").getString(),
                node.node("lore-key").getString());
    }

    /**
     * Overlay {@code override} onto {@code base}: each field the override specifies wins, each it leaves
     * {@code null} keeps the base's. A name-key on the override replaces a base inline name (and vice versa),
     * since name and name-key are two sources for the same slot; the same holds for lore. Used to express a
     * stateful item's variant as just its differences from a shared base icon.
     */
    public static IconOptions combine(IconOptions base, IconOptions override) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(override, "override");
        return new IconOptions(
                pick(override.material, base.material),
                override.namePresent() ? override.name : base.name,
                override.lorePresent() ? override.lore : base.lore,
                pick(override.amount, base.amount),
                override.namePresent() ? override.nameKey : base.nameKey,
                override.lorePresent() ? override.loreKey : base.loreKey);
    }

    private boolean namePresent() {
        return name != null || nameKey != null;
    }

    private boolean lorePresent() {
        return lore != null || loreKey != null;
    }

    /**
     * Build the icon. Display text comes from {@code catalog} when a {@code name-key}/{@code lore-key} is set
     * (resolved for {@code locale}), else from the inline {@code name}/{@code lore}; an inline value with no
     * catalog still renders, so a menu needs no lang file unless it references one.
     */
    public ItemStack toItem(@Nullable MessageCatalog catalog, Locale locale) {
        Objects.requireNonNull(locale, "locale");
        Material resolved = material == null ? Material.STONE : material;
        ItemBuilder builder = ItemBuilder.of(resolved);
        Component resolvedName = resolveName(catalog, locale);
        if (resolvedName != null) {
            builder.name(resolvedName);
        }
        List<Component> resolvedLore = resolveLore(catalog, locale);
        if (!resolvedLore.isEmpty()) {
            builder.lore(resolvedLore);
        }
        if (amount != null && amount > 1) {
            builder.amount(amount);
        }
        return builder.build();
    }

    private @Nullable Component resolveName(@Nullable MessageCatalog catalog, Locale locale) {
        if (nameKey != null && catalog != null) {
            // The key path is the last-resort default, so an untranslated key shows the path rather than
            // throwing (MessageKey rejects a blank default): a visible "missing translation" cue in game.
            return Text.mini(catalog.template(MessageKey.of(nameKey, nameKey), locale));
        }
        return name == null ? null : Text.mini(name);
    }

    private List<Component> resolveLore(@Nullable MessageCatalog catalog, Locale locale) {
        List<String> source = lore;
        if (loreKey != null && catalog != null) {
            source = List.of(catalog.template(MessageKey.of(loreKey, loreKey), locale));
        }
        if (source == null) {
            return List.of();
        }
        List<Component> rendered = new ArrayList<>();
        for (String line : source) {
            rendered.add(Text.mini(line));
        }
        return rendered;
    }

    private static <T> @Nullable T pick(@Nullable T override, @Nullable T base) {
        return override != null ? override : base;
    }

    private static @Nullable Material parseMaterial(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        Material material = Material.matchMaterial(raw);
        if (material == null) {
            throw new IllegalArgumentException("unknown material in menu config: " + raw);
        }
        return material;
    }

    private static @Nullable List<String> readStringList(ConfigurationNode node) {
        if (node.virtual()) {
            return null;
        }
        try {
            return node.getList(String.class);
        } catch (SerializationException notAList) {
            return null;
        }
    }
}
