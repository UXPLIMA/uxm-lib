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
 * <p>A name from a player the server let in ({@link #intoConnected}) is held to a wider shape: a Bedrock bridge may be
 * configured with another prefix than the default dot, and a proxy or an offline-mode network may let in names longer
 * than sixteen. It still holds none of the characters above, and never starts with a character a command reads as a
 * flag, a relative coordinate or a selector. A name from anywhere else (a vote, a stored row) keeps the strict shape.
 *
 * <p>Every spelling a line may use is filled: {@code %player%}, {@code <player>}, {@code {player}} and
 * {@code %player_name%}.
 */
public final class NameFill {

    /**
     * The spellings of the player's name a line may use. {@code %player_name%} is PlaceholderAPI's, and it is filled
     * here too because the engine would otherwise fill it later, as text, with no check at all.
     */
    public static final List<String> TOKENS = List.of("%player_name%", "%player%", "<player>", "{player}");

    /**
     * What an account name holds: letters, digits and underscores, up to sixteen, after at most one of the two marks a
     * Bedrock bridge puts in front of a name, a dot by default and a star in its older versions. Other marks are not
     * taken: a leading dash reads as a flag and a leading tilde as a relative coordinate in many commands.
     */
    private static final Pattern ACCOUNT = Pattern.compile("[.*]?[A-Za-z0-9_]{1,16}");

    /**
     * What the name of a player the server let in may hold: up to three marks a Bedrock bridge may be configured to put
     * in front of it, then letters, digits and underscores, up to thirty-two.
     */
    private static final Pattern CONNECTED = Pattern.compile("[.*#!+$=:]{0,3}[A-Za-z0-9_]{1,32}");

    /** The body of a name after a Bedrock bridge's own prefix. */
    private static final Pattern BODY = Pattern.compile("[A-Za-z0-9_]{1,32}");

    /** The longest prefix a Bedrock bridge's configuration is trusted with. */
    private static final int MAX_PREFIX = 8;

    /**
     * What no name may hold anywhere: whitespace adds an argument, and the rest open a tag, close an argument, reach
     * the line's grammar, or start a placeholder.
     */
    private static final String NEVER = "<>\"'{}|[]\\%";

    /** What a name may not start with: a flag, a relative or local coordinate, a selector. */
    private static final String NEVER_FIRST = "-~^@";

    private NameFill() {}

    /** Whether {@code name} is one an account could have, so it can stand inside any line unchanged. */
    public static boolean isAccountName(String name) {
        Objects.requireNonNull(name, "name");
        return ACCOUNT.matcher(name).matches();
    }

    /**
     * Whether {@code name}, the name of a player the server let in, can stand inside any line unchanged. With
     * {@code bedrockPrefix}, the prefix the server's Bedrock bridge is configured with (empty for none), a name that
     * starts with it is read as that prefix and a body of letters, digits and underscores.
     */
    public static boolean isConnectedName(String name, String bedrockPrefix) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(bedrockPrefix, "bedrockPrefix");
        if (CONNECTED.matcher(name).matches()) {
            return true;
        }
        return trustedPrefix(bedrockPrefix)
                && name.startsWith(bedrockPrefix)
                && BODY.matcher(name.substring(bedrockPrefix.length())).matches();
    }

    /** Whether a configured Bedrock prefix holds nothing a name may not. */
    private static boolean trustedPrefix(String prefix) {
        if (prefix.isEmpty() || prefix.length() > MAX_PREFIX || NEVER_FIRST.indexOf(prefix.charAt(0)) >= 0) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            char c = prefix.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c) || NEVER.indexOf(c) >= 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * As {@link #into}, for the name of a player the server let in, whose caller vouches for it: the name is held to
     * {@link #isConnectedName} with no Bedrock prefix beyond the marks that shape allows.
     */
    public static Optional<String> intoConnected(String line, String name) {
        return intoConnected(line, name, "");
    }

    /**
     * As {@link #into}, for the name of a player the server let in, whose caller vouches for it: the name is held to
     * {@link #isConnectedName} with {@code bedrockPrefix}, the prefix the server's Bedrock bridge is configured with.
     *
     * @return the filled line, or empty when the line names the player and the name could still change what it does
     */
    public static Optional<String> intoConnected(String line, String name, String bedrockPrefix) {
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(bedrockPrefix, "bedrockPrefix");
        return fill(line, name, isConnectedName(name, bedrockPrefix));
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
        return fill(line, name, isAccountName(name));
    }

    private static Optional<String> fill(String line, String name, boolean safe) {
        boolean namesThePlayer = TOKENS.stream().anyMatch(line::contains);
        if (namesThePlayer && !safe) {
            return Optional.empty();
        }
        String filled = line;
        for (String token : TOKENS) {
            filled = filled.replace(token, name);
        }
        return Optional.of(filled);
    }
}
