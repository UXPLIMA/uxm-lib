package com.uxplima.uxmlib.gui.style;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.text.GlyphWidthTable;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.style.Theme;

/**
 * Builds the lore of a menu tile in one fixed order:
 *
 * <pre>
 *  ◆ title            (from Tiles)
 *    breadcrumb
 *  (blank)
 *  ✎ description      the header, then the sentences
 *    what it does
 *  (blank)
 *  ≡ details          the header, then the facts
 *    ▪ label value
 *  (blank)
 *  → click to do the thing
 * </pre>
 *
 * <p>The structure is here and the words are in the message catalog, which is the split that matters: a
 * translator moves every word and can move none of the shape, so no tile ships without its breadcrumb, its
 * description header or its click line. The glyphs are structure rather than words, so they do not belong in
 * the catalog either — they come from the {@link Theme}, where a server can change them.
 *
 * <p>Blocks are separated by a blank line, and a block a caller never fills in takes no space. A description
 * a translator wrote over several lines is broken again here, so the line breaks are theirs.
 *
 * <p>Everything under a header lines up with the header's words, not with its glyph: a breadcrumb, a
 * description line and a bullet all start where the text above them starts. The indent is measured from the
 * glyph rather than typed, so a server that configures a wider one keeps the column.
 */
public final class Lore {

    private final Theme theme;
    private final List<List<Component>> blocks = new ArrayList<>();
    private List<Component> current = new ArrayList<>();

    private Lore(Theme theme) {
        this.theme = Objects.requireNonNull(theme, "theme");
    }

    /** A new lore for one tile, drawn with {@code theme}'s glyphs and colours. */
    public static Lore of(Theme theme) {
        return new Lore(theme);
    }

    /** The category or type line that sits under the title. */
    public Lore crumb(Component crumb) {
        Objects.requireNonNull(crumb, "crumb");
        line(Component.text(indentUnder("title")).append(crumb.colorIfAbsent(theme.colour("crumb"))));
        return block();
    }

    /** The description block: the header, then one line for each line the catalog wrote. */
    public Lore description(Component header, Component text) {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(text, "text");
        header("description", header);
        String indent = indentUnder("description");
        for (Component sentence : split(text)) {
            line(Component.text(indent).append(sentence.colorIfAbsent(theme.colour("subtext"))));
        }
        return block();
    }

    /** The details header. Call {@link #row} or {@link #status} after it. */
    public Lore details(Component header) {
        Objects.requireNonNull(header, "header");
        header("details", header);
        return this;
    }

    /** One fact: a label in the muted colour, then its value. */
    public Lore row(Component label, Component value) {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(value, "value");
        line(bullet("row")
                .append(label.colorIfAbsent(theme.colour("muted")))
                .append(Component.text(" "))
                .append(value.colorIfAbsent(theme.colour("value"))));
        return this;
    }

    /** One state: the shape of a fact, with a value that is a coloured word rather than a number. */
    public Lore status(Component label, Component value) {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(value, "value");
        line(bullet("status")
                .append(label.colorIfAbsent(theme.colour("muted")))
                .append(Component.text(" "))
                .append(value));
        return this;
    }

    /**
     * The click block, which closes the tile and names what a click does. A tile that answers two clicks — a
     * plain one and a shift one, say — writes them on two lines of the same catalog entry, so they stay one
     * block of air rather than two.
     */
    public Lore action(Component action) {
        Objects.requireNonNull(action, "action");
        block();
        for (Component sentence : split(action)) {
            line(Component.text(" " + theme.glyph("action") + " ", theme.colour("dim"))
                    .append(sentence.colorIfAbsent(theme.colour("subtext"))));
        }
        return block();
    }

    /** The lore as one component with a newline between the lines, which the item builder splits. */
    public Component build() {
        block();
        Component out = Component.empty();
        boolean first = true;
        for (List<Component> lines : blocks) {
            if (!first) {
                out = out.append(Component.newline()).append(Component.text(Tiles.PADDING));
            }
            for (int index = 0; index < lines.size(); index++) {
                if (!first || index > 0) {
                    out = out.append(Component.newline());
                }
                out = out.append(lines.get(index)).append(Component.text(Tiles.PADDING));
            }
            first = false;
        }
        return out;
    }

    /** A header: one glyph at the left margin, padded either side, then the words it introduces. */
    private void header(String name, Component header) {
        line(glyph(name).append(header.colorIfAbsent(theme.colour("info"))));
    }

    /**
     * A fact or a state. Its bullet sits where the header's <em>words</em> start rather than where the
     * header's glyph does, so the facts read as one column under the header instead of as a second margin.
     */
    private Component bullet(String name) {
        return Component.text(indentUnder("details") + theme.glyph(name) + " ", theme.colour("icon"));
    }

    /** One glyph of the theme, padded either side, in the colour furniture is drawn in. */
    private Component glyph(String name) {
        return Component.text(" " + theme.glyph(name) + " ", theme.colour("icon"));
    }

    /**
     * The leading spaces that land a line under the words of a header drawn with {@code glyphName}. A header
     * spends one space, its glyph and one more space before its text, and a lore line can only be indented
     * in whole spaces, so this is that width in spaces, rounded. Measured rather than typed: a server that
     * configures a wider glyph moves the column, and the indent follows it instead of being left behind.
     */
    private String indentUnder(String glyphName) {
        int prefix = 2 * GlyphWidthTable.SPACE_WIDTH + GlyphWidthTable.widthOf(theme.glyph(glyphName), false);
        return " ".repeat(Math.max(1, Math.round(prefix / (float) GlyphWidthTable.SPACE_WIDTH)));
    }

    private void line(Component line) {
        current.add(line);
    }

    private Lore block() {
        if (!current.isEmpty()) {
            blocks.add(current);
            current = new ArrayList<>();
        }
        return this;
    }

    /** One component per line of a catalog entry that was written over several lines. */
    private static List<Component> split(Component text) {
        List<Component> lines = new ArrayList<>();
        for (String line : Text.serialize(text).split("\n", -1)) {
            lines.add(Text.mini(line));
        }
        return lines;
    }
}
