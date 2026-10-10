package com.uxplima.uxmlib.command.annotation;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The language tags a localized alias is filed under, read the way an operator writes them.
 *
 * <p>An operator writes {@code tr}, {@code pt_br} or {@code pt-BR} and means the same thing each time, so
 * every form is turned into one: the language in lower case and the country in upper case, joined by a
 * hyphen. A bare language covers every country it is spoken in. A tag with a country covers that country
 * only.
 */
final class LocaleTags {

    private static final Pattern SHAPE = Pattern.compile("[A-Za-z]{2,8}([-_][A-Za-z0-9]{1,8})*");

    private LocaleTags() {}

    /** The tag in its one written form, or empty when {@code raw} names no language. */
    static Optional<String> normalize(String raw) {
        Objects.requireNonNull(raw, "raw");
        String trimmed = raw.strip();
        if (!SHAPE.matcher(trimmed).matches()) {
            return Optional.empty();
        }
        Locale locale = Locale.forLanguageTag(trimmed.replace('_', '-'));
        return locale.getLanguage().isEmpty() ? Optional.empty() : Optional.of(locale.toLanguageTag());
    }

    /** Whether a player who reads {@code locale} is one of the readers {@code tag} names. */
    static boolean matches(String tag, Locale locale) {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(locale, "locale");
        Locale wanted = Locale.forLanguageTag(tag);
        if (!wanted.getLanguage().equalsIgnoreCase(locale.getLanguage())) {
            return false;
        }
        return wanted.getCountry().isEmpty() || wanted.getCountry().equalsIgnoreCase(locale.getCountry());
    }

    /**
     * One {@code tag=word} entry split in two, or empty when it is not one. The tag is normalised, so the
     * annotation may carry any form an operator wrote.
     */
    static Optional<Entry> entry(String raw) {
        Objects.requireNonNull(raw, "raw");
        int split = raw.indexOf('=');
        if (split <= 0) {
            return Optional.empty();
        }
        String word = raw.substring(split + 1).strip();
        if (word.isEmpty() || word.contains(" ")) {
            return Optional.empty();
        }
        return normalize(raw.substring(0, split)).map(tag -> new Entry(tag, word));
    }

    /** A word and the language it is for. */
    record Entry(String tag, String word) {

        Entry {
            Objects.requireNonNull(tag, "tag");
            Objects.requireNonNull(word, "word");
        }

        /** The annotation form, {@code tag=word}. */
        String written() {
            return tag + "=" + word;
        }
    }
}
