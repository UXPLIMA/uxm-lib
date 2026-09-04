package com.uxplima.uxmlib.text.style;

import java.util.List;

import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.Modifying;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * The {@code <caps>…</caps>} tag: what it holds is written in small capitals.
 *
 * <p>{@link Typography} converts a template, and it stops at a value on purpose: a name, a nickname and a
 * world are what a player wrote, and a library that rewrote those would be changing somebody's name. So the
 * letters pass runs before a value is inserted and never sees one.
 *
 * <p>That leaves a gap. A value is not always a player's own words. The name of an item, the word for a
 * state, the title of a category are the interface talking, and in a small-capital interface they are the
 * one thing left in ordinary letters. This tag closes it, and it closes it in the file: a line writes
 * {@code <caps><item></caps>} where the words are the interface's and writes {@code <player>} bare where
 * they are not. It is the mirror of {@code <plain>…</plain>}, which says the opposite about a template.
 *
 * <p>It follows the language. {@link Typography} removes the markers for a language that is not written in
 * small capitals, so an operator who turns them off in {@code theme.conf} turns off every one of these with
 * it, and a screen is never half converted.
 *
 * <p>Small capitals exist for the Latin alphabet only. A file in a language whose letters have no
 * small-capital form does not write this tag, which is why the choice is one line at a time and not one
 * switch for the server.
 */
public final class SmallCapsTag {

    /** The name a template writes, without its brackets. */
    public static final String NAME = "caps";

    /** The resolver a {@code MiniMessage} instance is built with. */
    public static final TagResolver RESOLVER = TagResolver.resolver(NAME, (arguments, context) -> modify());

    private SmallCapsTag() {}

    private static Modifying modify() {
        return (current, depth) -> current instanceof TextComponent text
                ? text.content(SmallCaps.of(text.content())).children(List.of())
                : current.children(List.of());
    }
}
