package com.uxplima.uxmlib.condition.action;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Fills a player's name into a line an operator wrote, where the name cannot change what the line does.
 *
 * <p>A caller that fills the name in as text before the engine reads the line makes the name part of the line. A
 * {@code [console]} line then runs a command with the name inside it, and a {@code [message]} line is read as markup
 * with the name inside it. The server checks a name before it lets the player in, but it can be told to check less,
 * and a name with a space adds an argument to a command while a name with a bracket opens a tag.
 *
 * <p>So a name an account could not have never reaches a command: the line that would carry it is not run. It reaches
 * any other line escaped, as text. A name an account could have is filled in as it is.
 *
 * <p>Every spelling a line may use is filled: {@code %player%}, {@code <player>} and {@code {player}}.
 */
public final class NameFill {

    /** The spellings of the player's name a line may use. */
    public static final List<String> TOKENS = List.of("%player%", "<player>", "{player}");

    /**
     * What an account name holds: letters, digits and underscores, up to sixteen, after at most one of the marks a
     * Bedrock bridge may put in front of a name.
     */
    private static final Pattern ACCOUNT = Pattern.compile("[.*!~+\\-]?[A-Za-z0-9_]{1,16}");

    private NameFill() {}

    /** Whether {@code name} is one an account could have, so it can stand inside a command unchanged. */
    public static boolean isAccountName(String name) {
        Objects.requireNonNull(name, "name");
        return ACCOUNT.matcher(name).matches();
    }

    /**
     * {@code line}, an effect line such as {@code [console] give %player% cake}, with {@code name} filled in.
     *
     * @return the filled line, or empty when the line runs a command and the name is not one an account could have,
     *     which is a line that must not run
     */
    public static Optional<String> intoEffect(String line, String name) {
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(name, "name");
        if (!namesThePlayer(line) || isAccountName(name)) {
            return Optional.of(fill(line, name));
        }
        return runsACommand(line)
                ? Optional.empty()
                : Optional.of(fill(line, MiniMessage.miniMessage().escapeTags(name)));
    }

    /**
     * {@code command}, a plain command with no verb in front of it, with {@code name} filled in.
     *
     * @return the filled command, or empty when it names the player and the name is not one an account could have
     */
    public static Optional<String> intoCommand(String command, String name) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(name, "name");
        if (namesThePlayer(command) && !isAccountName(name)) {
            return Optional.empty();
        }
        return Optional.of(fill(command, name));
    }

    private static boolean namesThePlayer(String line) {
        return TOKENS.stream().anyMatch(line::contains);
    }

    private static String fill(String line, String name) {
        String filled = line;
        for (String token : TOKENS) {
            filled = filled.replace(token, name);
        }
        return filled;
    }

    /** Whether the verb of {@code line} runs what follows as a command. A verb the engine does not know runs nothing. */
    private static boolean runsACommand(String line) {
        String trimmed = line.strip();
        int close = trimmed.indexOf(']');
        if (!trimmed.startsWith("[") || close < 0) {
            return false;
        }
        String keyword = trimmed.substring(1, close).strip();
        for (ActionType type : ActionType.values()) {
            if (type.prefix().equalsIgnoreCase(keyword)) {
                return type == ActionType.CONSOLE || type == ActionType.PLAYER;
            }
        }
        return false;
    }
}
