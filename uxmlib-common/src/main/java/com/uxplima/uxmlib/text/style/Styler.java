package com.uxplima.uxmlib.text.style;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.MessageKey;
import org.jspecify.annotations.Nullable;

/**
 * Applies the whole style pass to a message catalog: once when the plugin loads, and again on a reload.
 *
 * <p>The pass is two pure steps over a template — the letters ({@link Typography}), then the tokens
 * ({@link StyleTokens}). Doing it at load rather than at render costs nothing per message, cannot be
 * forgotten at a call site, and is testable with no server.
 *
 * <p>Every key is written into the styled catalog for every language, with the fallback of the source catalog
 * already applied. That is what makes the default compiled into a key look exactly like the file: the default
 * is a template like any other and goes through the same two steps.
 *
 * <p>Hold one styler and give it a new palette through {@link #reload(Theme)} rather than building a second
 * one. A menu asks its styler for the theme each time it draws a tile, so a replaced styler repaints the chat
 * and leaves every open menu on the old colours — the half-repainted screen a reload is supposed to avoid.
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
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(locale, "locale");
        Theme snapshot = theme;
        boolean smallCaps = snapshot.smallCaps(locale);
        return StyleTokens.expand(Typography.apply(template, smallCaps), snapshot, smallCaps);
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
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(keys, "keys");
        Objects.requireNonNull(files, "files");
        Objects.requireNonNull(defaultLocale, "defaultLocale");
        Set<Locale> locales = new LinkedHashSet<>(files.keySet());
        locales.add(defaultLocale);
        Map<Locale, Map<String, String>> styled = new HashMap<>();
        for (Locale locale : locales) {
            styled.put(locale, templates(source, keys, files.get(locale), locale));
        }
        return new MessageCatalog(styled, defaultLocale);
    }

    private Map<String, String> templates(
            MessageCatalog source,
            Iterable<? extends MessageKey> keys,
            @Nullable Map<String, String> file,
            Locale locale) {
        Map<String, String> templates = new LinkedHashMap<>();
        for (MessageKey key : keys) {
            templates.put(key.path(), apply(source.template(key, locale), locale));
        }
        // The file second, so a path the key enum does not know is styled too: a plugin can build a key at
        // run time, and a line like that is the one line an operator wrote themselves.
        if (file != null) {
            file.forEach((path, template) -> templates.put(path, apply(template, locale)));
        }
        return templates;
    }
}
