package com.uxplima.uxmlib.condition.action;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Fills a player's name into a line an operator wrote, where the name cannot change what the line does.
 *
 * <p>A caller that fills the name in as text before the engine reads the line makes the name part of the line. A
 * {@code [console]} line then runs a command with the name inside it, and a {@code [message]} line is read as markup
 * with the name inside it. The server checks a name before it lets the player in, but it can be told to check less,
 * and a name with a space adds an argument to a command, a name with a bracket opens a tag, a name with a quote
 * closes a tag's argument, and a name with a brace or a bar reaches the line's own grammar.
 *
 * <p>Escaping cannot cover all of those at once, because what is safe depends on where in the line the name lands. So
 * a name an account could not have is never filled in: the line that would carry it is not run. A name an account
 * could have holds none of those characters and is filled in as it is.
 *
 * <p>Every spelling a line may use is filled: {@code %player%}, {@code <player>} and {@code {player}}.
 */
public final class NameFill {

    /** The spellings of the player's name a line may use. */
    public static final List<String> TOKENS = List.of("%player%", "<player>", "{player}");

    /**
     * What an account name holds: letters, digits and underscores, up to sixteen, after at most one of the two marks a
     * Bedrock bridge puts in front of a name, a dot by default and a star in its older versions. Other marks are not
     * taken: a leading dash reads as a flag and a leading tilde as a relative coordinate in many commands.
     */
    private static final Pattern ACCOUNT = Pattern.compile("[.*]?[A-Za-z0-9_]{1,16}");

    private NameFill() {}

    /** Whether {@code name} is one an account could have, so it can stand inside any line unchanged. */
    public static boolean isAccountName(String name) {
        Objects.requireNonNull(name, "name");
        return ACCOUNT.matcher(name).matches();
    }

    /**
     * {@code line}, an effect line such as {@code [console] give %player% cake} or a plain command, with {@code name}
     * filled in.
     *
     * @return the filled line, or empty when the line names the player and the name is not one an account could have,
     *     which is a line that must not run
     */
    public static Optional<String> into(String line, String name) {
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(name, "name");
        boolean namesThePlayer = TOKENS.stream().anyMatch(line::contains);
        if (namesThePlayer && !isAccountName(name)) {
            return Optional.empty();
        }
        String filled = line;
        for (String token : TOKENS) {
            filled = filled.replace(token, name);
        }
        return Optional.of(filled);
    }
}
