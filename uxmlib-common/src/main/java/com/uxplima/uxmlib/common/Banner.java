package com.uxplima.uxmlib.common;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Renders the library's name and version as a small startup banner. The render is pure: it returns the
 * banner lines as {@link Component}s so a test can assert on them, and {@link #print} feeds those lines to
 * any sink (a {@code ComponentLogger}, an {@code Audience}, or a test collector) without coupling to a
 * concrete logger. Kept deliberately small: a couple of framed lines, not block ASCII art.
 */
public final class Banner {

    private static final String RULE = "==============================";

    private Banner() {}

    /** The banner for the library itself, at {@link UxmLib#VERSION}. */
    public static List<Component> lines() {
        return lines("uxmLib", UxmLib.VERSION);
    }

    /** The banner lines for {@code name} at {@code version}: a top rule, the titled line, and a bottom rule. */
    public static List<Component> lines(String name, String version) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        Component rule = Component.text(RULE, NamedTextColor.DARK_AQUA);
        Component title =
                Component.text(name, NamedTextColor.AQUA).append(Component.text(" v" + version, NamedTextColor.GRAY));
        return List.of(rule, title, rule);
    }

    /** Emit each banner line for {@code name} at {@code version} to {@code sink} in order. */
    public static void print(String name, String version, Consumer<Component> sink) {
        Objects.requireNonNull(sink, "sink");
        for (Component line : lines(name, version)) {
            sink.accept(line);
        }
    }
}
