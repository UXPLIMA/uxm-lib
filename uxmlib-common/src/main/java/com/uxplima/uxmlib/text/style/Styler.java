package com.uxplima.uxmlib.text.style;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.MessageKey;
import org.jspecify.annotations.Nullable;

/**
 * Applies the whole style pass to a message catalog: once when the plugin loads, and again on a reload.
 *
 * <p>The pass is two pure steps over a template: the letters ({@link Typography}), then the tokens
 * ({@link StyleTokens}). Doing it at load rather than at render costs nothing per message, cannot be
 * forgotten at a call site, and is testable with no server.
 *
 * <p>Every key is written into the styled catalog for every language, with the fallback of the source catalog
 * already applied. That is what makes the default compiled into a key look exactly like the file: the default
 * is a template like any other and goes through the same two steps.
 *
 * <p>One name can be both a colour role and a value, and {@code <level>} is the one that caught the estate out:
 * a role of {@code theme.conf} and the natural name for a level number. Where the pass runs at render time the
 * value wins, because both halves are in one hand there. Where it runs at load, over a catalogue, no value
 * exists yet: a plugin that knows its own value names says so through the {@link java.util.function.Predicate}
 * overloads, and whatever is not spared is written down so the collision is reported rather than swallowed.
 *
 * <p>Hold one styler and give it a new palette through {@link #reload(Theme)} rather than building a second
 * one. A menu asks its styler for the theme each time it draws a tile, so a replaced styler repaints the chat
 * and leaves every open menu on the old colours: the half-repainted screen a reload is supposed to avoid.
 */
public final class Styler {

    private volatile Theme theme;

    public Styler(Theme theme) {
        this.theme = Objects.requireNonNull(theme, "theme");
    }

    /** The palette this styler paints with. Menus need it for the parts of a tile that are not text. */
    public Theme theme() {
        return theme;
    }

    /** Take a new palette. Every holder of this styler sees it at once, which is the point. */
    public void reload(Theme theme) {
        this.theme = Objects.requireNonNull(theme, "theme");
    }

    /** One template, styled for {@code locale}. */
    public String apply(String template, Locale locale) {
        return apply(template, locale, name -> false);
    }

    /**
     * One template, styled for {@code locale}, told which names carry a value the caller is about to supply.
     *
     * <p>A value that was supplied wins. {@code <level>} is a role of {@code theme.conf} and it is also the
     * natural name for a level number, and where this pass runs at render time both halves are in one hand, so
     * there is no reason to lose one. A template that closes the token ({@code <value>50</value>}) is painting
     * with the role and keeps it; only the bare, unclosed shape yields.
     *
     * <p>Reach for it wherever the value is known at the moment the styling happens: a line an operator wrote in
     * a menu file, a tile drawn for one row. A catalogue is styled once at load, where no value exists yet, and
     * {@link #style(MessageCatalog, Iterable, Map, Locale, java.util.function.Predicate)} is what that case has
     * instead.
     */
    public String apply(String template, Locale locale, Predicate<String> supplied) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(supplied, "supplied");
        Theme snapshot = theme;
        boolean smallCaps = snapshot.smallCaps(locale);
        return StyleTokens.expand(Typography.apply(template, smallCaps), snapshot, smallCaps, supplied);
    }

    /**
     * {@code source} with every template styled, ready to hand to
     * {@link com.uxplima.uxmlib.text.message.Messages#reload(MessageCatalog)}.
     *
     * @param keys every key the plugin can show, which is usually its key enum
     * @param files what each language file holds, flattened to {@code a.b.c -> template}
     */
    public MessageCatalog style(
            MessageCatalog source,
            Iterable<? extends MessageKey> keys,
            Map<Locale, ? extends Map<String, String>> files,
            Locale defaultLocale) {
        return style(source, keys, files, defaultLocale, name -> false);
    }

    /**
     * The same, told which names the plugin supplies as values rather than meaning as colours.
     *
     * <p>A catalogue is styled once, at load, and no value exists at that moment, so this is the only place a
     * plugin can say that {@code level} is its level number and not the colour role of the same name. A name
     * this predicate answers for is left in the line for the value, on the same condition as everywhere else:
     * the line must not close the token, because a line that closes it is painting with the role.
     *
     * <p>Whatever it does not spare, it records. Every role token that was open to being read as a value and was
     * painted anyway is written into the catalogue against its path, so the facade that renders the line can
     * hold it up against the values a caller actually supplies and say that the two collided. That is the half
     * of this that needs no plugin to do anything: a plugin that never calls this overload still gets told,
     * once, which line and which name, instead of a number quietly not being there.
     *
     * @param supplied answers true for a name the plugin fills in as a value at render time
     */
    public MessageCatalog style(
            MessageCatalog source,
            Iterable<? extends MessageKey> keys,
            Map<Locale, ? extends Map<String, String>> files,
            Locale defaultLocale,
            Predicate<String> supplied) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(keys, "keys");
        Objects.requireNonNull(files, "files");
        Objects.requireNonNull(defaultLocale, "defaultLocale");
        Objects.requireNonNull(supplied, "supplied");
        Set<Locale> locales = new LinkedHashSet<>(files.keySet());
        locales.add(defaultLocale);
        Map<Locale, Map<String, String>> styled = new HashMap<>();
        Map<String, Set<String>> shadowed = new LinkedHashMap<>();
        for (Locale locale : locales) {
            styled.put(locale, templates(source, keys, files.get(locale), locale, supplied, shadowed));
        }
        return new MessageCatalog(styled, defaultLocale, shadowed);
    }

    private Map<String, String> templates(
            MessageCatalog source,
            Iterable<? extends MessageKey> keys,
            @Nullable Map<String, String> file,
            Locale locale,
            Predicate<String> supplied,
            Map<String, Set<String>> shadowed) {
        Map<String, String> templates = new LinkedHashMap<>();
        for (MessageKey key : keys) {
            String template = source.template(key, locale);
            record(shadowed, key.path(), template, supplied);
            templates.put(key.path(), apply(template, locale, supplied));
        }
        // The file second, so a path the key enum does not know is styled too: a plugin can build a key at
        // run time, and a line like that is the one line an operator wrote themselves.
        if (file != null) {
            file.forEach((path, template) -> {
                record(shadowed, path, template, supplied);
                templates.put(path, apply(template, locale, supplied));
            });
        }
        return templates;
    }

    /**
     * Note the role tokens this pass is about to eat at {@code path} that a value could have been meant by.
     *
     * <p>Kept across the languages of one catalogue, because a translator who writes the token in one language
     * and not in another has still written it, and the line that has it is the line that will be wrong.
     */
    private void record(Map<String, Set<String>> shadowed, String path, String template, Predicate<String> supplied) {
        Set<String> shaped = StyleTokens.valueShapedRoles(template, theme);
        for (String role : shaped) {
            if (!supplied.test(role)) {
                shadowed.computeIfAbsent(path, any -> new LinkedHashSet<>()).add(role);
            }
        }
    }
}
