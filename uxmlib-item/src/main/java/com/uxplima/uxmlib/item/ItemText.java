package com.uxplima.uxmlib.item;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.inventory.ItemStack;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * One item, as text, both ways.
 *
 * <p>{@link ItemWriter} writes an item into a node and {@link ItemConfig} reads one back out, and every
 * plugin that wanted to keep an item in a database wrote the same twenty lines to get from a node to a
 * string. This is those twenty lines, once.
 *
 * <p>The text is HOCON in the vocabulary an operator already reads in a menu file, not a base64 blob. That
 * costs a few bytes per row and buys the thing a blob takes away: an operator looking at a table during an
 * incident can see what is in it, and does not have to start the plugin to find out.
 *
 * <p>Reading is total. A row written by a newer version, an item whose material a resource pack removed, a
 * column somebody edited by hand: each of those is empty rather than an exception, because the alternative
 * is one bad row stopping a plugin from loading anything.
 */
public final class ItemText {

    private ItemText() {}

    /**
     * Write {@code stack} as text.
     *
     * @throws IllegalArgumentException if the item cannot be written, which means a broken stack rather than
     *     an unusual one
     */
    public static String write(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        StringWriter text = new StringWriter();
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                .sink(() -> new BufferedWriter(text))
                .build();
        try {
            CommentedConfigurationNode node = loader.createNode();
            ItemWriter.write(stack, node);
            loader.save(node);
        } catch (ConfigurateException failure) {
            throw new IllegalArgumentException("item cannot be written as text: " + stack.getType(), failure);
        }
        return text.toString();
    }

    /**
     * Read back what {@link #write} wrote, or nothing.
     *
     * <p>Blank text is nothing rather than an error: a column that holds "no item" is a real state and every
     * caller would otherwise write the same emptiness check.
     */
    public static Optional<ItemStack> read(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isBlank()) {
            return Optional.empty();
        }
        try {
            CommentedConfigurationNode node = HoconConfigurationLoader.builder()
                    .source(() -> new BufferedReader(new StringReader(text)))
                    .build()
                    .load();
            return Optional.of(ItemConfig.load(node).build());
        } catch (ConfigurateException | IllegalArgumentException failure) {
            return Optional.empty();
        }
    }
}
