package com.uxplima.uxmlib.command.annotation;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import com.uxplima.uxmlib.common.Durations;

/**
 * Every sentence the command layer says to a sender on its own behalf: the refusals, the argument
 * rejections, the internal-error line, the help chrome. Implement it to answer in the sender's language;
 * ignore it and the library keeps saying what it always said.
 *
 * <p>Each method receives the {@code locale} of the sender being answered rather than a server-wide setting,
 * because a server has players who do not share one language. Each also receives the <em>values</em> — the
 * bad input, the allowed ones, the time left — never a finished English sentence, since no other language
 * reliably puts those words in the same order and a caller handed a rendered string would have to take it
 * apart to translate it.
 *
 * <p>Register one on the registry the rest of the command layer already comes from:
 *
 * <pre>{@code
 * ParamResolvers resolvers = ParamResolvers.withDefaults()
 *         .messages(new MyCatalogueBackedMessages(catalog))
 *         .locales(LocaleSource.ofDefault(Locale.forLanguageTag("tr")));
 * }</pre>
 *
 * <p>Every method has a default, so an implementation overrides only the lines it cares about. The defaults
 * are the English the library shipped before this seam existed, which is what makes adopting it optional.
 *
 * <p>The help page routes its whole line through here, colours and separator included, not only its chrome.
 * A generated help page is text a player reads that no plugin repository contains, so a style pass that
 * reads a plugin's own resources cannot see it and cannot fix it; the seam is the only place it can be
 * fixed from.
 */
public interface CommandMessages {

    /** The built-in English messages, used when a registry has none of its own. */
    static CommandMessages english() {
        return new CommandMessages() {};
    }

    /** A player-only command was run from the console or a command block. */
    default Component playerOnly(Locale locale) {
        Objects.requireNonNull(locale, "locale");
        return red("Only a player can run this command.");
    }

    /**
     * An argument was rejected by its resolver or a validator.
     *
     * @param argument the argument's declared name
     * @param input the raw text the sender gave for it
     * @param reason the resolver's own explanation; may be empty when it gave none
     */
    default Component invalidValue(Locale locale, String argument, String input, String reason) {
        Objects.requireNonNull(locale, "locale");
        Component message = red("Invalid value '" + input + "' for <" + argument + ">");
        return reason.isEmpty() ? message : message.append(red(": " + reason));
    }

    /**
     * An argument was rejected because it is not one of a known set — an enum constant, say.
     *
     * @param allowed the accepted values, spelled the way tab-completion offers them
     */
    default Component notOneOf(Locale locale, String argument, String input, List<String> allowed) {
        return invalidValue(locale, argument, input, "expected one of " + String.join(", ", allowed));
    }

    /** An argument was rejected with no per-argument context — a flag value, say. */
    default Component invalidArgument(Locale locale, String detail) {
        Objects.requireNonNull(locale, "locale");
        return red(detail.isEmpty() ? "Invalid argument." : detail);
    }

    /** The handler threw. The real cause is logged server-side; this is what the sender sees. */
    default Component internalError(Locale locale) {
        Objects.requireNonNull(locale, "locale");
        return red("An internal error occurred while running this command.");
    }

    /** The sender ran a {@code @Cooldown} branch again before its window expired. */
    default Component onCooldown(Locale locale, Duration remaining) {
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(remaining, "remaining");
        return red("You must wait " + Durations.format(remaining) + " before using this again.");
    }

    /** The header line of a help page, e.g. {@code /home help (1/3)}. */
    default Component helpHeader(Locale locale, String command, int page, int pages) {
        Objects.requireNonNull(locale, "locale");
        return Component.text("/" + command + " help (" + page + "/" + pages + ")", NamedTextColor.YELLOW);
    }

    /**
     * The clickable command on a help line, e.g. {@code /town create <name>}. The click and the hover are the
     * renderer's; the wording and the colour are yours.
     */
    default Component helpCommand(Locale locale, String command) {
        Objects.requireNonNull(locale, "locale");
        return Component.text(command, NamedTextColor.WHITE);
    }

    /**
     * What sits between a help line's command and its description. Its own method because punctuation is not
     * neutral: a server whose style guide bans a character cannot strip it from text that lives in this jar,
     * so the library has to let it be replaced rather than pick one for everybody.
     */
    default Component helpSeparator(Locale locale) {
        Objects.requireNonNull(locale, "locale");
        return Component.text(" - ", NamedTextColor.GRAY);
    }

    /**
     * A branch's {@code @Subcommand} description, as the help line and its hover show it. The words are the
     * consumer's own and are passed through unchanged; what this decides is how they are painted.
     */
    default Component helpDescription(Locale locale, String description) {
        Objects.requireNonNull(locale, "locale");
        return Component.text(description, NamedTextColor.GRAY);
    }

    /** The hover on a help entry that has no description of its own. */
    default Component helpFillHint(Locale locale) {
        Objects.requireNonNull(locale, "locale");
        return Component.text("Click to fill in this command");
    }

    /** The hover on a help page-navigation arrow. */
    default Component helpPageHint(Locale locale, int page) {
        Objects.requireNonNull(locale, "locale");
        return Component.text("Page " + page);
    }

    private static Component red(String text) {
        return Component.text(text, NamedTextColor.RED);
    }
}
