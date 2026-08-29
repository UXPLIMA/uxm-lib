package com.uxplima.uxmlib.text.style;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.kyori.adventure.text.format.TextColor;

import org.spongepowered.configurate.ConfigurationNode;

/**
 * A palette of named roles, read from a config file.
 *
 * <p>This is the one place a colour lives. A message file names a role — {@code <accent>}, {@code <value>},
 * {@code <bad>} — and never a hex code, so a server that wants a red interface edits one file and every
 * message, menu and item follows. It also keeps a palette from drifting across a suite of plugins: a line
 * that says {@code <body>} is the same white everywhere.
 *
 * <p>The same file holds the two other things a look is made of: the glyphs the structure is drawn with, and
 * which languages are written in small capitals. Both are values rather than mechanism, which is why they
 * are read from a file instead of compiled in.
 *
 * <p>A key the file leaves out keeps the shipped default, so an operator may write three lines instead of
 * forty, a language nobody has answered for keeps its own letters, and a role added in a later version
 * cannot break a file written against an earlier one.
 */
public final class Theme {

    /** The roles and their shipped colours, in the order the shipped file lists them. */
    private static final Map<String, String> DEFAULT_COLOURS = defaultColours();

    /** The glyphs the structure of a message or a tile is drawn with. */
    private static final Map<String, String> DEFAULT_GLYPHS = defaultGlyphs();

    /** The categories whose prefix is not the accent colour, mapped to the role that colours them. */
    private static final Map<String, String> DEFAULT_CATEGORIES =
            Map.of("error", "bad", "money", "good", "shop", "good", "event", "event");

    /**
     * The languages written in small capitals unless the file says otherwise. Small capitals exist for the
     * Latin alphabet only, so the safe default is English and nothing else: a language with letters of its
     * own keeps them until somebody who reads that language decides otherwise.
     */
    private static final Set<String> DEFAULT_SMALL_CAPS = Set.of("en");

    /** The role a colour lookup falls back to, and the colour an unlisted category prefix reads in. */
    private static final String BODY = "body";

    private static final String ACCENT = "accent";

    private final Map<String, TextColor> colours;
    private final Map<String, String> glyphs;
    private final Map<String, String> categories;
    private final Set<String> smallCapsLanguages;

    private Theme(
            Map<String, TextColor> colours,
            Map<String, String> glyphs,
            Map<String, String> categories,
            Set<String> smallCapsLanguages) {
        this.colours = Map.copyOf(colours);
        this.glyphs = Map.copyOf(glyphs);
        this.categories = Map.copyOf(categories);
        this.smallCapsLanguages = Set.copyOf(smallCapsLanguages);
    }

    /** The shipped palette, used when there is no file yet or when it is empty. */
    public static Theme defaults() {
        Map<String, TextColor> colours = new LinkedHashMap<>();
        DEFAULT_COLOURS.forEach((role, hex) -> colours.put(role, parse(hex)));
        return new Theme(colours, DEFAULT_GLYPHS, DEFAULT_CATEGORIES, DEFAULT_SMALL_CAPS);
    }

    /**
     * The palette in {@code node}, with the shipped default behind every value the file leaves out.
     *
     * @throws IllegalArgumentException when the file holds something that is not a colour, which is a defect
     *     an operator has to see at load rather than as a black message in the game
     */
    public static Theme from(ConfigurationNode node) {
        Objects.requireNonNull(node, "node");
        return new Theme(
                colours(node), glyphs(node), categories(node), smallCaps(node.node("small-caps").childrenMap()));
    }

    /** The colour of {@code role}, or the body colour when this theme does not know the role. */
    public TextColor colour(String role) {
        Objects.requireNonNull(role, "role");
        TextColor found = colours.get(role);
        return found != null ? found : Objects.requireNonNull(colours.get(BODY), BODY);
    }

    /** Whether {@code role} is a colour of this theme, which is what makes a token a token. */
    public boolean hasColour(String role) {
        Objects.requireNonNull(role, "role");
        return colours.containsKey(role);
    }

    /** The hex of {@code role} as MiniMessage writes it, for example {@code #38b6ff}. */
    public String hex(String role) {
        return colour(role).asHexString().toLowerCase(Locale.ROOT);
    }

    /**
     * The glyph named {@code name}, or an empty string when this theme does not know it. Empty rather than a
     * stand-in character: a glyph nobody configured should leave a gap, not print a question mark at a player.
     */
    public String glyph(String name) {
        Objects.requireNonNull(name, "name");
        return glyphs.getOrDefault(name, "");
    }

    /** The glyph between a message's category word and the sentence, short for {@code glyph("separator")}. */
    public String separator() {
        return glyph("separator");
    }

    /** The colour role of the category prefix {@code label} carries; anything unlisted reads in the accent. */
    public String categoryRole(String label) {
        Objects.requireNonNull(label, "label");
        return categories.getOrDefault(label.toLowerCase(Locale.ROOT), ACCENT);
    }

    /** Whether {@code locale} is written in small capitals. */
    public boolean smallCaps(Locale locale) {
        Objects.requireNonNull(locale, "locale");
        return smallCapsLanguages.contains(locale.getLanguage().toLowerCase(Locale.ROOT));
    }

    private static Map<String, TextColor> colours(ConfigurationNode node) {
        Map<String, TextColor> colours = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : DEFAULT_COLOURS.entrySet()) {
            colours.put(entry.getKey(), parse(node.node("colours", entry.getKey()).getString(entry.getValue())));
        }
        return colours;
    }

    /**
     * The glyphs, reading {@code prefix.separator} as well as {@code glyphs.separator}. The separator lived
     * under the prefix block before the rest of the glyphs were configurable, and an operator who moved their
     * separator there should not have it quietly ignored.
     */
    private static Map<String, String> glyphs(ConfigurationNode node) {
        Map<String, String> glyphs = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : DEFAULT_GLYPHS.entrySet()) {
            glyphs.put(entry.getKey(), node.node("glyphs", entry.getKey()).getString(entry.getValue()));
        }
        String legacy = node.node("prefix", "separator").getString();
        if (legacy != null && node.node("glyphs", "separator").virtual()) {
            glyphs.put("separator", legacy);
        }
        return glyphs;
    }

    private static Map<String, String> categories(ConfigurationNode node) {
        Map<String, String> categories = new HashMap<>(DEFAULT_CATEGORIES);
        for (Map.Entry<Object, ? extends ConfigurationNode> child :
                node.node("prefix", "categories").childrenMap().entrySet()) {
            String role = child.getValue().getString();
            if (role != null) {
                categories.put(String.valueOf(child.getKey()).toLowerCase(Locale.ROOT), role);
            }
        }
        return categories;
    }

    /**
     * The small-capitals languages: the shipped set, then each language the file names applied on top.
     *
     * <p>Merging rather than replacing is the whole of it. A file that switches conversion on for one
     * language must not switch it off for every language it does not mention — that turns an operator adding
     * French into English silently losing its own writing, in every plugin at once.
     */
    private static Set<String> smallCaps(Map<Object, ? extends ConfigurationNode> languages) {
        Set<String> smallCaps = new HashSet<>(DEFAULT_SMALL_CAPS);
        for (Map.Entry<Object, ? extends ConfigurationNode> child : languages.entrySet()) {
            String language = String.valueOf(child.getKey()).toLowerCase(Locale.ROOT);
            if (child.getValue().getBoolean()) {
                smallCaps.add(language);
            } else {
                smallCaps.remove(language);
            }
        }
        return smallCaps;
    }

    private static TextColor parse(String hex) {
        TextColor parsed = TextColor.fromHexString(hex);
        if (parsed == null) {
            throw new IllegalArgumentException("not a colour: " + hex);
        }
        return parsed;
    }

    private static Map<String, String> defaultColours() {
        Map<String, String> colours = new LinkedHashMap<>();
        colours.put(ACCENT, "#38b6ff");
        colours.put(BODY, "#ffffff");
        colours.put("subtext", "#dde8f0");
        colours.put("muted", "#93a4b3");
        colours.put("dim", "#6b7886");
        colours.put("icon", "#8a93a1");
        colours.put("crumb", "#565f6b");
        colours.put("value", "#8fd9ff");
        colours.put("good", "#5be38c");
        colours.put("bad", "#ff6b6b");
        colours.put("warn", "#ffc93c");
        colours.put("money", "#ffc93c");
        colours.put("level", "#ffc93c");
        colours.put("cta", "#ffc93c");
        colours.put("info", "#4fd6e8");
        colours.put("rank", "#b68cff");
        colours.put("event", "#ff8fd0");
        return Map.copyOf(colours);
    }

    private static Map<String, String> defaultGlyphs() {
        Map<String, String> glyphs = new LinkedHashMap<>();
        glyphs.put("separator", "▶");
        glyphs.put("title", "◆");
        glyphs.put("description", "✎");
        glyphs.put("details", "≡");
        glyphs.put("row", "▪");
        glyphs.put("status", "•");
        glyphs.put("action", "→");
        return Map.copyOf(glyphs);
    }
}
