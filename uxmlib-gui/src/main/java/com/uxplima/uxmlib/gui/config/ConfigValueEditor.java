package com.uxplima.uxmlib.gui.config;

import java.util.Objects;

import com.uxplima.uxmlib.config.HoconConfig;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * The parse-validate-set core behind {@link ConfigEditorGui}, kept separate so it is unit-testable with no
 * server and so the menu class stays within its size cap. Given the dotted {@code path} of a scalar config
 * key and the raw text a player typed, it coerces the text to the key's existing scalar type: an integer
 * stays an integer, a flag stays a boolean, then writes it and saves the file. Coercing to the current type
 * keeps a numeric setting numeric so later typed reads ({@code getInt}, {@code getBoolean}) still resolve.
 */
public final class ConfigValueEditor {

    private final HoconConfig config;

    public ConfigValueEditor(HoconConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Coerce {@code input} to the type the value at {@code path} currently holds and store it. Returns the
     * parsed value on success, or throws {@link IllegalArgumentException} when the input does not fit the type
     * (so the caller can tell the player and reopen the prompt). Does not save: see {@link #setAndSave}.
     */
    public Object coerceAndSet(String path, String input) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(input, "input");
        ConfigurationNode node = config.nodeAt((Object[]) path.split("\\."));
        Object coerced = coerce(node.raw(), input.trim());
        try {
            node.set(coerced);
        } catch (SerializationException failure) {
            throw new IllegalArgumentException("could not store value at " + path, failure);
        }
        return coerced;
    }

    /** Coerce, store, and persist in one step: {@link #coerceAndSet} then {@link HoconConfig#save()}. */
    public Object setAndSave(String path, String input) {
        Object coerced = coerceAndSet(path, input);
        config.save();
        return coerced;
    }

    // Match the current value's type so a typed read keeps resolving; an unrecognised current type, or a
    // currently-absent key, is treated as free text.
    private static Object coerce(Object current, String input) {
        if (current instanceof Boolean) {
            return parseBoolean(input);
        }
        if (current instanceof Integer || current instanceof Long) {
            return parseLong(input);
        }
        if (current instanceof Double || current instanceof Float) {
            return parseDouble(input);
        }
        return input;
    }

    private static boolean parseBoolean(String input) {
        if (input.equalsIgnoreCase("true")) {
            return true;
        }
        if (input.equalsIgnoreCase("false")) {
            return false;
        }
        throw new IllegalArgumentException("expected true or false, got: " + input);
    }

    private static long parseLong(String input) {
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException("expected a whole number, got: " + input, notANumber);
        }
    }

    private static double parseDouble(String input) {
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException("expected a number, got: " + input, notANumber);
        }
    }
}
