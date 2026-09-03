package com.uxplima.uxmlib.gui.config;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

import net.kyori.adventure.key.Key;

/**
 * One line of a {@code click} or an {@code open-actions} list, read into what it means.
 *
 * <p>Five verbs are the same in every menu of every plugin, so an operator who has written one menu can
 * write any of them: {@code close}, {@code open:<menu>}, {@code command:<line>}, {@code message:<line>}
 * and {@code sound:<name> <volume> <pitch>}. Anything else is a name a plugin registered, written
 * {@code <plugin>:<verb>} so that two plugins cannot claim the same word, with the rest of the line as its
 * argument.
 *
 * <p>Reading is where a mistake is caught. A verb nobody registered is refused as the file loads, with the
 * line in the message, rather than doing nothing under a player's cursor a week later.
 */
public sealed interface MenuAction {

    /** Shut the window. */
    record Close() implements MenuAction {}

    /** Open another menu of the same plugin, by the name it is filed under. */
    record OpenMenu(String menu) implements MenuAction {}

    /** Run a command as the viewer, with no leading slash. */
    record RunCommand(String line) implements MenuAction {}

    /** Send the viewer one line, written in MiniMessage. */
    record SendMessage(String line) implements MenuAction {}

    /**
     * Play a sound to the viewer.
     *
     * @param name either the vanilla key, {@code item.book.page_turn}, or the constant a server names the
     *     same sound by, {@code ITEM_BOOK_PAGE_TURN}. Both are read, because both are written in the menu
     *     files that already exist. A key needs no server to resolve; a constant is looked up when it plays.
     */
    record PlaySound(String name, float volume, float pitch) implements MenuAction {}

    /** Something the plugin does, under the name it registered. */
    record Named(String name, String argument) implements MenuAction {}

    /**
     * Read one line.
     *
     * @param knows whether a name is registered, so an unknown verb is refused here and not on a click
     * @throws IllegalArgumentException when the line is not an action this menu can run
     */
    static MenuAction read(String line, Predicate<String> knows) {
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(knows, "knows");

        String text = line.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("an action line is empty");
        }
        int colon = text.indexOf(':');
        String head = colon < 0 ? text : text.substring(0, colon);
        String rest = colon < 0 ? "" : text.substring(colon + 1).trim();

        return switch (head.toLowerCase(Locale.ROOT)) {
            case "close" -> new Close();
            case "open" -> new OpenMenu(required(rest, "open", text));
            case "command" -> new RunCommand(required(rest, "command", text));
            case "message" -> new SendMessage(required(rest, "message", text));
            case "sound" -> sound(required(rest, "sound", text), text);
            default -> named(text, colon, knows);
        };
    }

    /** Read every line of a list. */
    static List<MenuAction> readAll(List<String> lines, Predicate<String> knows) {
        Objects.requireNonNull(lines, "lines");
        return lines.stream().map(line -> read(line, knows)).toList();
    }

    private static MenuAction named(String text, int colon, Predicate<String> knows) {
        int space = text.indexOf(' ');
        String name = space < 0 ? text : text.substring(0, space);
        String argument = space < 0 ? "" : text.substring(space + 1).trim();
        if (colon < 0 || !knows.test(name)) {
            throw new IllegalArgumentException("'" + text
                    + "' is not an action. Write close, open:, command:, message:, sound:,"
                    + " or a name the plugin registered.");
        }
        return new Named(name, argument);
    }

    private static MenuAction sound(String rest, String text) {
        String[] parts = rest.split(" +", -1);
        String name = parts[0];
        if (!Key.parseable(name) && !isConstant(name)) {
            throw new IllegalArgumentException("'" + text + "' names the sound '" + name
                    + "'. Write the vanilla key, such as item.book.page_turn, or the constant"
                    + " ITEM_BOOK_PAGE_TURN.");
        }
        return new PlaySound(name, number(parts, 1, text), number(parts, 2, text));
    }

    /** Whether the name is the constant form: upper case letters, digits and underscores only. */
    private static boolean isConstant(String name) {
        if (name.isEmpty()) {
            return false;
        }
        for (int at = 0; at < name.length(); at++) {
            char letter = name.charAt(at);
            boolean allowed = letter == '_' || (letter >= 'A' && letter <= 'Z') || (letter >= '0' && letter <= '9');
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    private static float number(String[] parts, int at, String text) {
        if (at >= parts.length || parts[at].isEmpty()) {
            return 1.0F;
        }
        try {
            return Float.parseFloat(parts[at]);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException("'" + text + "' has a volume or a pitch that is not a number.");
        }
    }

    private static String required(String rest, String verb, String text) {
        if (rest.isEmpty()) {
            throw new IllegalArgumentException("'" + text + "' is a " + verb + " with nothing after it.");
        }
        return rest;
    }
}
