package com.uxplima.uxmlib.text.language;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * What a player may choose, and what happens when they choose it. This is the body of a language command
 * without the command: a plugin registers the node under its own root, with its own permission and its own
 * words, and calls this for everything that would otherwise be written once per plugin.
 *
 * <p>Nothing here renders anything. The library carries the mechanism and the shape of the answer, never the
 * wording and never the look.
 */
public final class LanguageChoices {

    private final Supplier<Set<Locale>> languages;

    private final Locale fallback;

    private final LanguageResolver resolver;

    /**
     * @param languages the files this plugin found, which is the set of languages that exist
     * @param settings read for the default, so a server with no file at all still offers one choice
     */
    public LanguageChoices(Languages languages, LanguageResolver resolver, LanguageSettings settings) {
        this(Objects.requireNonNull(languages, "languages")::locales, resolver, settings);
    }

    /**
     * The same, with the language set read on every call.
     *
     * <p>A plugin that reads its message folder again when an operator reloads passes its own reader here, so
     * a language file written while the server runs becomes a choice at the reload rather than at the next
     * restart.
     */
    public LanguageChoices(Supplier<Set<Locale>> languages, LanguageResolver resolver, LanguageSettings settings) {
        Objects.requireNonNull(settings, "settings");
        this.languages = Objects.requireNonNull(languages, "languages");
        this.fallback = settings.defaultLocale();
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /** The languages a player may pick, sorted by tag, never empty. */
    public List<Locale> available() {
        Set<Locale> found = languages.get();
        return found.isEmpty()
                ? List.of(fallback)
                : found.stream()
                        .sorted(Comparator.comparing(Locale::toLanguageTag))
                        .toList();
    }

    /**
     * The language {@code tag} names, or empty when this server does not have it.
     *
     * <p>An underscore reads as a hyphen and case does not matter, because a player types what they remember.
     * A tag with no country matches a language that has exactly one country, so {@code pt} reaches
     * {@code pt-BR} on a server that has only that one. It stays empty when two countries share the language,
     * since guessing between them would be worse than asking again.
     */
    public Optional<Locale> parse(String tag) {
        Objects.requireNonNull(tag, "tag");
        String wanted = tag.replace('_', '-').trim();
        if (wanted.isEmpty()) {
            return Optional.empty();
        }
        List<Locale> available = available();
        Optional<Locale> exact = available.stream()
                .filter(locale -> locale.toLanguageTag().equalsIgnoreCase(wanted))
                .findFirst();
        return exact.isPresent() ? exact : byLanguageAlone(available, wanted);
    }

    private static Optional<Locale> byLanguageAlone(List<Locale> available, String wanted) {
        List<Locale> sharing = available.stream()
                .filter(locale -> locale.getLanguage().equalsIgnoreCase(wanted))
                .toList();
        return sharing.size() == 1 ? Optional.of(sharing.getFirst()) : Optional.empty();
    }

    /** The tags that start with what a player has typed so far, for a completion. */
    public List<String> suggestions(String typed) {
        Objects.requireNonNull(typed, "typed");
        String prefix = typed.replace('_', '-');
        return available().stream()
                .map(Locale::toLanguageTag)
                .filter(tag -> tag.regionMatches(true, 0, prefix, 0, prefix.length()))
                .toList();
    }

    /** Record a choice. It reaches a network-wide provider too, when the server runs one. */
    public void choose(UUID player, Locale locale) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(locale, "locale");
        resolver.choose(player, locale);
    }

    /** Drop a choice, so the client language or the server default answers again. */
    public void reset(UUID player) {
        Objects.requireNonNull(player, "player");
        resolver.forget(player);
    }
}
