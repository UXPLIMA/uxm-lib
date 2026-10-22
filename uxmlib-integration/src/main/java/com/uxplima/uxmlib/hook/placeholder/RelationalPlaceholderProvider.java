package com.uxplima.uxmlib.hook.placeholder;

import org.bukkit.entity.Player;

import org.jspecify.annotations.Nullable;

/**
 * A resolver for the relational form of a namespace, {@code %rel_<identifier>_<params>%}, which knows two players:
 * the one a line is drawn for and the one it is about. A chat line, a tab row and a nametag are each drawn once for
 * every viewer, so a value that depends on who reads it (a word in the reader's language, whether the reader can see
 * the other player) belongs here rather than in a {@link PlaceholderProvider}, which knows only one of them.
 *
 * <p>Returning {@code null} means "no such placeholder", and the text stays as it was written. PlaceholderAPI asks
 * from whatever thread draws the line, chat included, so answer from memory.
 */
@FunctionalInterface
public interface RelationalPlaceholderProvider {

    /** Resolve {@code params} for {@code viewer} reading about {@code target}, or {@code null} when unknown. */
    @Nullable String onRequest(Player viewer, Player target, String params);
}
