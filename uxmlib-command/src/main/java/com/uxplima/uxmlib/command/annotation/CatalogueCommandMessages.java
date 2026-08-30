package com.uxplima.uxmlib.command.annotation;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmlib.common.Durations;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.MessageKey;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * The command layer's lines, worded and painted by the consumer's own message catalog.
 *
 * <p>Every method answers with a {@link CommandLine} key, so these lines are translated with the rest of the
 * plugin and its palette owns them. A value that comes from outside (a command, a description, whatever the
 * sender typed) is inserted unparsed: it may hold angle brackets, and a usage line like
 * {@code /tags set <name>} must be shown and never obeyed.
 *
 * <p>The template is read straight off the catalog rather than through {@link Messages#render}, because the
 * command layer knows the sender's locale but has no {@code Audience} to ask. The catalog is already styled
 * when it is loaded, so this is a parse and nothing more.
 */
final class CatalogueCommandMessages implements CommandMessages {

    private final Messages messages;

    CatalogueCommandMessages(Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public Component playerOnly(Locale locale) {
        return line(CommandLine.PLAYER_ONLY, locale);
    }

    @Override
    public Component invalidValue(Locale locale, String argument, String input, String reason) {
        if (reason.isEmpty()) {
            return line(
                    CommandLine.INVALID_VALUE,
                    locale,
                    Text.placeholder("argument", argument),
                    Text.placeholder("input", input));
        }
        return line(
                CommandLine.INVALID_VALUE_WHY,
                locale,
                Text.placeholder("argument", argument),
                Text.placeholder("input", input),
                Text.placeholder("reason", reason));
    }

    @Override
    public Component notOneOf(Locale locale, String argument, String input, List<String> allowed) {
        return line(
                CommandLine.NOT_ONE_OF,
                locale,
                Text.placeholder("argument", argument),
                Text.placeholder("input", input),
                Text.placeholder("allowed", String.join(", ", allowed)));
    }

    @Override
    public Component invalidArgument(Locale locale, String detail) {
        return detail.isEmpty()
                ? line(CommandLine.BAD_ARGUMENT, locale)
                : line(CommandLine.INVALID_ARGUMENT, locale, Text.placeholder("detail", detail));
    }

    @Override
    public Component internalError(Locale locale) {
        return line(CommandLine.INTERNAL_ERROR, locale);
    }

    @Override
    public Component onCooldown(Locale locale, Duration remaining) {
        return line(CommandLine.ON_COOLDOWN, locale, Text.placeholder("time", Durations.format(remaining)));
    }

    @Override
    public Component helpHeader(Locale locale, String command, int page, int pages) {
        return line(
                CommandLine.HELP_HEADER,
                locale,
                Text.placeholder("command", command),
                Text.placeholder("page", String.valueOf(page)),
                Text.placeholder("pages", String.valueOf(pages)));
    }

    @Override
    public Component helpCommand(Locale locale, String command) {
        return line(CommandLine.HELP_COMMAND, locale, Text.placeholder("command", command));
    }

    @Override
    public Component helpSeparator(Locale locale) {
        return line(CommandLine.HELP_SEPARATOR, locale);
    }

    @Override
    public Component helpDescription(Locale locale, String description) {
        return line(CommandLine.HELP_DESCRIPTION, locale, Text.placeholder("description", description));
    }

    @Override
    public Component helpFillHint(Locale locale) {
        return line(CommandLine.HELP_FILL_HINT, locale);
    }

    @Override
    public Component helpPageHint(Locale locale, int page) {
        return line(CommandLine.HELP_PAGE_HINT, locale, Text.placeholder("page", String.valueOf(page)));
    }

    private Component line(MessageKey key, Locale locale, TagResolver... resolvers) {
        Objects.requireNonNull(locale, "locale");
        return Text.mini(messages.catalog().template(key, locale), resolvers);
    }
}
