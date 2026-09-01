package com.uxplima.uxmlib.text.style;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.kyori.adventure.text.format.TextColor;

import org.spongepowered.configurate.ConfigurationNode;

/**
 * The look of a server, read from a config file, in three layers.
 *
 * <p><b>The palette</b> is the server's own colours, under whatever names the server likes: {@code sky},
 * {@code kirmizi}, {@code brand-2}. Nothing this library ships refers to a palette name, so any of them may be
 * renamed, removed or added to without breaking a message anybody wrote.
 *
 * <p><b>The roles</b> are the jobs a colour does: {@code body}, {@code value}, {@code good}. A message file
 * names a role and never a colour, so a server that wants a red interface edits one file and every message,
 * menu and item follows. A role's value is a palette name or a hex code. The map is open: a key written in the
 * file becomes a token, so a server may name a job this library never heard of and use it in its own files.
 *
 * <p><b>The wheel</b> is an ordered list of colours that decoration is taken from. A menu of twelve tiles asks
 * for twelve arcs and gets twelve pairs of neighbours, so nothing has to be named for a screen to read as
 * twelve headings rather than one heading twelve times.
 *
 * <p>The same file holds the glyphs the structure is drawn with and which languages are written in small
 * capitals. Both are values rather than mechanism, which is why they are read from a file instead of compiled
 * in.
 *
 * <p>A key the file leaves out keeps the shipped default, so an operator may write three lines instead of
 * forty, a language nobody has answered for keeps its own letters, and a role added in a later version cannot
 * break a file written against an earlier one.
 */
public final class Theme {

    /** The roles and their shipped colours, in the order the shipped file lists them. */
    private static final Map<String, String> DEFAULT_ROLES = defaultRoles();

    /** The glyphs the structure of a message or a tile is drawn with. */
    private static final Map<String, String> DEFAULT_GLYPHS = defaultGlyphs();

    /** The categories whose prefix is not the accent colour, mapped to the role that colours them. */
    private static final Map<String, String> DEFAULT_CATEGORIES =
            Map.of("error", "bad", "money", "good", "shop", "good", "event", "event");

    /**
     * The languages written in small capitals unless the file says otherwise: none of them.
     *
     * <p>Small capitals are a typeface, and a typeface is taste. A library that turned them on for English by
     * itself would repaint every message of a plugin that only wanted the colours, and the plugin's author
     * would have no line anywhere saying why. So the mechanism ships on and the look ships off: name a
     * language in {@code small-caps} and it is converted, and the file this library ships shows how.
     *
     * <p>Small capitals exist for the Latin alphabet only, so a language whose letters have no small-capital
     * form must not be named.
     */
    private static final Set<String> DEFAULT_SMALL_CAPS = Set.of();

    /** The role a colour lookup falls back to, and the colour an unlisted category prefix reads in. */
    private static final String BODY = "body";

    private static final String ACCENT = "accent";

    private static final String SEPARATOR = "separator";

    /** The block that held the roles before the palette existed. A file that still uses it keeps working. */
    private static final String LEGACY_ROLES = "colours";

    private final Map<String, TextColor> roles;
    private final List<TextColor> wheel;
    private final Map<String, List<TextColor>> gradients;
    private final Map<String, String> glyphs;
    private final Map<String, String> categories;
    private final Set<String> smallCapsLanguages;

    private Theme(
            Map<String, TextColor> roles,
            List<TextColor> wheel,
            Map<String, List<TextColor>> gradients,
            Map<String, String> glyphs,
            Map<String, String> categories,
            Set<String> smallCapsLanguages) {
        this.roles = Map.copyOf(roles);
        this.wheel = List.copyOf(wheel);
        this.gradients = Map.copyOf(gradients);
        this.glyphs = Map.copyOf(glyphs);
        this.categories = Map.copyOf(categories);
        this.smallCapsLanguages = Set.copyOf(smallCapsLanguages);
    }

    /** The shipped look, used when there is no file yet or when it is empty. */
    public static Theme defaults() {
        Map<String, TextColor> roles = new LinkedHashMap<>();
        DEFAULT_ROLES.forEach((role, hex) -> roles.put(role, parse(hex)));
        return new Theme(roles, List.of(), Map.of(), DEFAULT_GLYPHS, DEFAULT_CATEGORIES, DEFAULT_SMALL_CAPS);
    }

    /**
     * The look in {@code node}, with the shipped default behind every value the file leaves out.
     *
     * @throws IllegalArgumentException when the file holds something that is neither a colour nor a palette
     *     name, which is a defect an operator has to see at load rather than as a black message in the game
     */
    public static Theme from(ConfigurationNode node) {
        Objects.requireNonNull(node, "node");
        Map<String, TextColor> palette = palette(node);
        return new Theme(
                roles(node, palette),
                wheel(node, palette),
                gradients(node, palette),
                glyphs(node),
                categories(node),
                smallCaps(node.node("small-caps").childrenMap()));
    }

    /** The colour of {@code role}, or the body colour when this theme does not know the role. */
    public TextColor colour(String role) {
        Objects.requireNonNull(role, "role");
        TextColor found = roles.get(role);
        return found != null ? found : Objects.requireNonNull(roles.get(BODY), BODY);
    }

    /** Whether {@code role} is a role of this theme, which is what makes a token a token. */
    public boolean hasColour(String role) {
        Objects.requireNonNull(role, "role");
        return roles.containsKey(role);
    }

    /** The hex of {@code role} as MiniMessage writes it, for example {@code #38b6ff}. */
    public String hex(String role) {
        return colour(role).asHexString().toLowerCase(Locale.ROOT);
    }

    /**
     * The colours decoration is taken from, in the order the file writes them.
     *
     * <p>Empty unless the file names them. A library that shipped a wheel of its own would be choosing a look
     * for every server that never asked for one.
     */
    public List<TextColor> wheel() {
        return wheel;
    }

    /**
     * Two neighbouring colours of the wheel, or an empty list when the file names fewer than two.
     *
     * <p>This is what a screen full of tiles paints with. The caller passes the position of the tile, so tile
     * one and tile two differ without either of them naming a colour, and the wheel wraps, so a menu longer
     * than the wheel keeps working rather than running out.
     */
    public List<TextColor> arc(int index) {
        if (wheel.size() < 2) {
            return List.of();
        }
        int start = Math.floorMod(index, wheel.size());
        return List.of(wheel.get(start), wheel.get((start + 1) % wheel.size()));
    }

    /**
     * The stops of the gradient named {@code name}, in order, or an empty list when the file names none.
     *
     * <p>A gradient is how a heading reads as finished rather than merely coloured, but it is also the first
     * thing a server wants to turn off, so nothing here is a gradient unless the file says so. One stop means
     * a flat colour, which is how an operator switches one off without deleting the key.
     *
     * <p>{@code header} is the one name this library asks for. Every other name is one a file asked to be
     * painted with, and a server writes as many as its interface has moods.
     */
    public List<TextColor> gradient(String name) {
        Objects.requireNonNull(name, "name");
        return gradients.getOrDefault(name.toLowerCase(Locale.ROOT), List.of());
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
        return glyph(SEPARATOR);
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

    /**
     * Every colour the file names, under the file's own names. A palette entry is always a hex code.
     *
     * <p>The map is used while the file is read and then dropped. Nothing outside the file speaks these
     * names, so keeping them would only invite a call site to depend on one.
     */
    private static Map<String, TextColor> palette(ConfigurationNode node) {
        Map<String, TextColor> palette = new LinkedHashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> child :
                node.node("palette").childrenMap().entrySet()) {
            String hex = child.getValue().getString();
            if (hex != null) {
                palette.put(String.valueOf(child.getKey()).toLowerCase(Locale.ROOT), parse(hex));
            }
        }
        return palette;
    }

    /**
     * The roles: the shipped ones, then every role the file writes applied on top.
     *
     * <p>The map is open on purpose. A closed list would drop a role a server invented, silently, which is
     * how the file stopped being the server's and became ours. The two blocks are read in order, so a file
     * that still writes the old {@code colours} block keeps working and a {@code roles} entry wins.
     */
    private static Map<String, TextColor> roles(ConfigurationNode node, Map<String, TextColor> palette) {
        Map<String, TextColor> roles = new LinkedHashMap<>();
        DEFAULT_ROLES.forEach((role, hex) -> roles.put(role, parse(hex)));
        readColours(node.node(LEGACY_ROLES), palette, roles);
        readColours(node.node("roles"), palette, roles);
        return roles;
    }

    private static void readColours(
            ConfigurationNode node, Map<String, TextColor> palette, Map<String, TextColor> into) {
        for (Map.Entry<Object, ? extends ConfigurationNode> child :
                node.childrenMap().entrySet()) {
            String value = child.getValue().getString();
            if (value != null) {
                into.put(String.valueOf(child.getKey()), resolve(value, palette));
            }
        }
    }

    /** The wheel, in the order the file writes it. Each entry is a palette name or a hex code. */
    private static List<TextColor> wheel(ConfigurationNode node, Map<String, TextColor> palette) {
        List<TextColor> wheel = new ArrayList<>();
        for (ConfigurationNode child : node.node("wheel").childrenList()) {
            String value = child.getString();
            if (value != null) {
                wheel.add(resolve(value, palette));
            }
        }
        return wheel;
    }

    /**
     * The glyphs, reading {@code prefix.separator} as well as {@code glyphs.separator}. The separator lived
     * under the prefix block before the rest of the glyphs were configurable, so a file that still keeps it
     * there stands in for the shipped default instead of being quietly ignored; a {@code glyphs.separator}
     * still wins over both.
     */
    private static Map<String, String> glyphs(ConfigurationNode node) {
        String shippedSeparator = Objects.requireNonNull(DEFAULT_GLYPHS.get(SEPARATOR), SEPARATOR);
        String separator = node.node("prefix", SEPARATOR).getString(shippedSeparator);
        Map<String, String> glyphs = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : DEFAULT_GLYPHS.entrySet()) {
            String shipped = SEPARATOR.equals(entry.getKey()) ? separator : entry.getValue();
            glyphs.put(entry.getKey(), node.node("glyphs", entry.getKey()).getString(shipped));
        }
        return glyphs;
    }

    /** Every named list of stops the file writes, each kept in the order it writes them in. */
    private static Map<String, List<TextColor>> gradients(ConfigurationNode node, Map<String, TextColor> palette) {
        Map<String, List<TextColor>> gradients = new LinkedHashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> child :
                node.node("gradients").childrenMap().entrySet()) {
            List<TextColor> stops = new ArrayList<>();
            for (ConfigurationNode stop : child.getValue().childrenList()) {
                String value = stop.getString();
                if (value != null) {
                    stops.add(resolve(value, palette));
                }
            }
            if (!stops.isEmpty()) {
                gradients.put(String.valueOf(child.getKey()).toLowerCase(Locale.ROOT), List.copyOf(stops));
            }
        }
        return gradients;
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
     * language must not switch it off for every language it does not mention: that turns an operator adding
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

    /**
     * A colour written in the file: a name from the palette, or a hex code.
     *
     * <p>The palette is looked at first, so a server that calls a colour {@code sky} may write {@code sky}
     * everywhere and change the hex in one place.
     */
    private static TextColor resolve(String value, Map<String, TextColor> palette) {
        TextColor named = palette.get(value.toLowerCase(Locale.ROOT));
        return named != null ? named : parse(value);
    }

    private static TextColor parse(String hex) {
        TextColor parsed = TextColor.fromHexString(hex);
        if (parsed == null) {
            throw new IllegalArgumentException("not a colour and not a palette name: " + hex);
        }
        return parsed;
    }

    private static Map<String, String> defaultRoles() {
        Map<String, String> roles = new LinkedHashMap<>();
        roles.put(ACCENT, "#38b6ff");
        roles.put(BODY, "#ffffff");
        roles.put("subtext", "#dde8f0");
        roles.put("muted", "#93a4b3");
        roles.put("dim", "#6b7886");
        roles.put("icon", "#8a93a1");
        roles.put("crumb", "#565f6b");
        roles.put("value", "#8fd9ff");
        roles.put("good", "#5be38c");
        roles.put("bad", "#ff6b6b");
        roles.put("warn", "#ffc93c");
        roles.put("money", "#ffc93c");
        roles.put("level", "#ffc93c");
        roles.put("cta", "#ffc93c");
        roles.put("info", "#4fd6e8");
        roles.put("rank", "#b68cff");
        roles.put("event", "#ff8fd0");
        return Map.copyOf(roles);
    }

    private static Map<String, String> defaultGlyphs() {
        Map<String, String> glyphs = new LinkedHashMap<>();
        glyphs.put(SEPARATOR, "▶");
        glyphs.put("title", "◆");
        glyphs.put("description", "✎");
        glyphs.put("details", "≡");
        glyphs.put("row", "•");
        glyphs.put("status", "•");
        glyphs.put("action", "→");
        return Map.copyOf(glyphs);
    }
}
