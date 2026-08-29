package com.uxplima.uxmlib.text;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java MiniMessage tag stripper, keyed by {@link ColorCapability}.
 *
 * <p>Used at the inbound boundary (chat listener / shortcut command) to
 * remove MiniMessage tags whose category the sender lacks permission for.
 * The format pattern itself is operator-controlled and not stripped: only
 * the player-supplied body passes through here.
 *
 * <p>Stripping leaves untouched:
 * <ul>
 *   <li>Tags whose category is in the {@code allowed} set.</li>
 *   <li>Unknown / non-style tags (placeholders, click/hover events,
 *       {@code <reset>}, {@code <newline>}): they're either structural
 *       or governed by a separate permission domain.</li>
 *   <li>Pre-tags {@code <pre>...</pre>} are NOT given special treatment;
 *       inner content is still walked. (Pre is a MiniMessage parsing
 *       concept; pre-stripping bypasses parsing entirely.)</li>
 * </ul>
 *
 * <p>Both opening and closing tags are stripped together so the rendered
 * MiniMessage stream stays well-formed. The matched substring is removed
 * outright rather than escaped because MiniMessage treats escapes
 * (a backslash in front of a tag) as a literal-mode signal which would
 * leak the tag text into chat.
 */
public final class ColorCapabilityStripper {

    private ColorCapabilityStripper() {}

    /** Vanilla 16 + the dye-named aliases: see {@code NamedTextColor}. */
    private static final Set<String> BASIC_COLOR_NAMES = Set.of(
            "black",
            "dark_blue",
            "dark_green",
            "dark_aqua",
            "dark_red",
            "dark_purple",
            "gold",
            "gray",
            "grey",
            "dark_gray",
            "dark_grey",
            "blue",
            "green",
            "aqua",
            "red",
            "light_purple",
            "yellow",
            "white");

    private static final Set<String> FORMATTING_TAGS =
            Set.of("bold", "b", "italic", "i", "em", "underlined", "u", "strikethrough", "st", "obfuscated", "obf");

    private static final Pattern TAG_PATTERN = Pattern.compile("<([^<>]+)>");

    private static final Pattern HEX_BODY = Pattern.compile("[0-9a-fA-F]{3,8}");

    /**
     * Strip every recognised styling tag (named colour, hex, gradient,
     * rainbow, formatting) from {@code text}, returning the visible /
     * "plain" form. Unknown tags (placeholders, structural tags) are
     * left untouched. Used by the identity layer to compute
     * {@link ColorCapability ColorCapability}-
     * independent invariants (length, blacklist match, tab-completion)
     * over the visible characters only.
     */
    public static String stripAllStyles(String text) {
        return strip(text, Set.of());
    }

    /**
     * Does {@code text} open with an explicit colour tag: a named colour,
     * hex, gradient, or rainbow? Returns {@code false} for plain text, for
     * formatting-only openers ({@code <bold>…}), and for any leading
     * whitespace (a tag must be the very first character).
     *
     * <p>Reuses {@link #categorize(String)} so the colour-tag decision has a
     * single source of truth shared with the cosmetics
     * {@code ChatColorApplier} (which prepends a preferred colour only when
     * the body does not already open with its own explicit colour).
     */
    public static boolean startsWithColourTag(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isEmpty() || text.charAt(0) != '<') {
            return false;
        }
        Matcher m = TAG_PATTERN.matcher(text);
        if (!m.find() || m.start() != 0) {
            return false;
        }
        ColorCapability cat = categorize(m.group(1));
        return cat == ColorCapability.BASIC
                || cat == ColorCapability.HEX
                || cat == ColorCapability.GRADIENT
                || cat == ColorCapability.RAINBOW;
    }

    /**
     * Strip every tag whose {@link ColorCapability} is NOT in {@code allowed}.
     * Returns {@code text} verbatim when {@code allowed} contains every
     * capability (full freedom: no scan needed).
     */
    public static String strip(String text, Set<ColorCapability> allowed) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(allowed, "allowed");
        if (allowed.size() == ColorCapability.values().length) {
            return text;
        }
        Matcher m = TAG_PATTERN.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        int last = 0;
        // Track the head + category of every kept open tag so a matching
        // close tag falls into the same category. The close-tag body
        // ("/color") may not carry its sibling's arg (e.g. "/color"
        // arrives bare while the open was "color:#ff8800"): without the
        // stack, a stripped <color:#hex> would leave a dangling </color>
        // in the output.
        Deque<OpenTag> openStack = new ArrayDeque<>();
        while (m.find()) {
            String inner = m.group(1);
            boolean isClose = inner.startsWith("/");
            String head = head(inner);
            ColorCapability cat;
            if (isClose) {
                OpenTag matched = popMatching(openStack, head);
                cat = matched != null ? matched.category : categorize(inner);
            } else {
                cat = categorize(inner);
                if (cat != null) {
                    openStack.push(new OpenTag(head, cat));
                }
            }
            out.append(text, last, m.start());
            if (cat == null || allowed.contains(cat)) {
                out.append(m.group());
            }
            // else: drop the matched tag entirely.
            last = m.end();
        }
        out.append(text, last, text.length());
        return out.toString();
    }

    private record OpenTag(String head, ColorCapability category) {}

    /**
     * Pop the most recent open tag whose head matches {@code closeHead}.
     * MiniMessage doesn't enforce strict nesting, so the match is
     * scanned-not-required: when nothing matches, return null and
     * the close tag is categorised by its bare body.
     */
    private static @org.jspecify.annotations.Nullable OpenTag popMatching(Deque<OpenTag> stack, String closeHead) {
        for (OpenTag o : stack) {
            if (o.head.equals(closeHead)) {
                stack.remove(o);
                return o;
            }
        }
        return null;
    }

    private static String head(String inner) {
        String body = inner.startsWith("/") ? inner.substring(1) : inner;
        int colon = body.indexOf(':');
        return (colon < 0 ? body : body.substring(0, colon)).toLowerCase(Locale.ROOT);
    }

    /**
     * Categorise the body of a {@code <...>} tag (i.e. the part between
     * angle brackets, with no leading slash on closes).
     *
     * @return the matching {@link ColorCapability}, or {@code null} when
     *     the tag is unknown / not a styling tag and should be left alone.
     */
    private static @org.jspecify.annotations.Nullable ColorCapability categorize(String inner) {
        String body = inner.startsWith("/") ? inner.substring(1) : inner;
        // Split off the first colon-separated arg so <color:red>, <gradient:...>
        // and <#abc:bold-aware> are all parseable.
        int colon = body.indexOf(':');
        String head = (colon < 0 ? body : body.substring(0, colon)).toLowerCase(Locale.ROOT);
        String firstArg = colon < 0 ? null : body.substring(colon + 1);

        // <#abcdef> shorthand for hex colours.
        if (head.startsWith("#") && HEX_BODY.matcher(head.substring(1)).matches()) {
            return ColorCapability.HEX;
        }
        // <color:NAME> / <colour:NAME> / <c:NAME>; HEX wins when arg is hex.
        if (head.equals("color") || head.equals("colour") || head.equals("c")) {
            if (firstArg != null) {
                String argHead = firstArg.split(":", -1)[0];
                if (argHead.startsWith("#")
                        && HEX_BODY.matcher(argHead.substring(1)).matches()) {
                    return ColorCapability.HEX;
                }
                if (BASIC_COLOR_NAMES.contains(argHead.toLowerCase(Locale.ROOT))) {
                    return ColorCapability.BASIC;
                }
            }
            // Unknown / no arg: conservative treat as BASIC so the gate
            // applies (else operators couldn't lock down `<color:foo>`).
            return ColorCapability.BASIC;
        }
        if (BASIC_COLOR_NAMES.contains(head)) {
            return ColorCapability.BASIC;
        }
        if (head.equals("gradient") || head.equals("gr")) {
            return ColorCapability.GRADIENT;
        }
        if (head.equals("rainbow") || head.equals("rb")) {
            return ColorCapability.RAINBOW;
        }
        if (FORMATTING_TAGS.contains(head)) {
            return ColorCapability.FORMATTING;
        }
        return null;
    }
}
