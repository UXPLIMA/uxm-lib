package com.uxplima.uxmlib.text.language;

import java.util.Locale;
import java.util.Objects;

/**
 * The name of a language, in the words of the people who read it.
 *
 * <p>A menu that offers {@code tr} offers nothing a player recognises. The runtime knows that {@code tr} is
 * "Türkçe" to a Turkish reader and "Turkish" to an English one, and this asks it in the language itself,
 * because the list is read by the people who are looking for their own language in it.
 *
 * <p>This is a fallback and not a rule. A plugin that lets an operator name a language reads their file
 * first and comes here for the ones they did not name.
 */
public final class LanguageNames {

    private LanguageNames() {}

    /** The name of {@code locale}, never empty. */
    public static String of(Locale locale) {
        Objects.requireNonNull(locale, "locale");
        String language = locale.getDisplayLanguage(locale);
        if (language.isBlank()) {
            return locale.toLanguageTag();
        }
        String named = capitalised(language, locale);
        String country = locale.getDisplayCountry(locale);
        return country.isBlank() ? named : named + " (" + country + ")";
    }

    private static String capitalised(String word, Locale locale) {
        return word.substring(0, 1).toUpperCase(locale) + word.substring(1);
    }
}
