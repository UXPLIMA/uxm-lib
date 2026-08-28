package com.uxplima.uxmlib.text.message;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmlib.text.Text;

/**
 * The i18n facade: resolves a viewer's locale, looks up the locale-specific template for a {@link MessageKey}
 * (three-tier fallback via {@link MessageCatalog}), renders it through MiniMessage with the supplied
 * placeholders, and delivers it over the channel configured for that key.
 *
 * <p>The channel comes from an admin-supplied {@code channels} map (a key mapped to a {@link Message} whose
 * variant selects chat/title/action-bar/boss-bar/silent); a key with no entry defaults to plain chat. The
 * template text always comes from the catalog, so a translator edits text and an operator edits the channel
 * independently. Constructor-injected, no static state.
 */
public final class Messages {

    /**
     * The suffix a title's subtitle is addressed by in a lang file: the message's own path plus this, so
     * {@code join.welcome} takes its subtitle from {@code join.welcome.subtitle}.
     */
    public static final String SUBTITLE_SUFFIX = ".subtitle";

    private final MessageCatalog catalog;
    private final LocaleSource locales;
    private final Map<String, Message> channels;

    /** A chat-only facade: every key is delivered as chat with the catalog's template. */
    public Messages(MessageCatalog catalog, LocaleSource locales) {
        this(catalog, locales, Map.of());
    }

    /**
     * @param channels per-key delivery channels, addressed by {@link MessageKey#path()}; a key absent here is
     *     delivered as chat. Copied defensively.
     */
    public Messages(MessageCatalog catalog, LocaleSource locales, Map<String, Message> channels) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.locales = Objects.requireNonNull(locales, "locales");
        this.channels = Map.copyOf(Objects.requireNonNull(channels, "channels"));
    }

    /** Render {@code key} for {@code viewer}'s locale, substituting {@code resolvers}, and deliver it. */
    public void send(Audience viewer, MessageKey key, TagResolver... resolvers) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(resolvers, "resolvers");
        Message channel = channel(key);
        if (channel instanceof Message.TitleText title) {
            showTitle(viewer, key, title, resolvers);
            return;
        }
        channel.send(viewer, render(viewer, key, resolvers));
    }

    /**
     * Show a title, taking both of its halves from the same place. A translator addresses the subtitle in
     * the lang file as the message's path plus {@value #SUBTITLE_SUFFIX}. The pair is looked for in the
     * viewer's own language first and the default locale second — the catalog's own tiers — and the first
     * tier that has <em>both</em> halves renders the title; when no tier does, both come from the operator's
     * {@code channels} entry, which is why a title entry must carry its {@code text}.
     *
     * <p>The choice is made per title rather than per half on purpose. Deciding each half on its own is what
     * puts a translated line above an untranslated one when a lang file has the title and not the subtitle,
     * and a half-translated title is the one outcome a player reads as broken. It is also made per locale, at
     * render time: one language's file missing a subtitle must not decide anything for the languages whose
     * files are complete.
     */
    private void showTitle(Audience viewer, MessageKey key, Message.TitleText title, TagResolver... resolvers) {
        MessageKey subtitleKey = subtitleKeyOf(key, title);
        Locale locale = locales.localeOf(viewer);
        if (showFrom(viewer, locale, key, subtitleKey, title, resolvers)
                || showFrom(viewer, catalog.defaultLocale(), key, subtitleKey, title, resolvers)) {
            return;
        }
        title.send(viewer, Text.mini(title.template(), resolvers), Text.mini(title.subtitle(), resolvers));
    }

    /** Show the title from {@code locale}'s own lang file, or report that it does not hold both halves. */
    private boolean showFrom(
            Audience viewer,
            Locale locale,
            MessageKey key,
            MessageKey subtitleKey,
            Message.TitleText title,
            TagResolver... resolvers) {
        Optional<String> text = catalog.find(key, locale);
        Optional<String> subtitle = catalog.find(subtitleKey, locale);
        if (text.isEmpty() || subtitle.isEmpty()) {
            return false;
        }
        title.send(viewer, Text.mini(text.get(), resolvers), Text.mini(subtitle.get(), resolvers));
        return true;
    }

    /**
     * The catalog key holding {@code key}'s subtitle. Only its path is ever read here — the halves are looked
     * up one tier at a time — so the operator's own subtitle stands in for the built-in default the interface
     * asks for.
     */
    private static MessageKey subtitleKeyOf(MessageKey key, Message.TitleText title) {
        return new SubtitleKey(key.path() + SUBTITLE_SUFFIX, title.subtitle());
    }

    /** The derived key for a title's subtitle; a record so two lookups for the same title compare equal. */
    private record SubtitleKey(String path, String defaultTemplate) implements MessageKey {}

    /** The rendered {@link Component} for {@code key} in {@code viewer}'s locale, without delivering it. */
    public Component render(Audience viewer, MessageKey key, TagResolver... resolvers) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(resolvers, "resolvers");
        Locale locale = locales.localeOf(viewer);
        String template = catalog.template(key, locale);
        return Text.mini(template, resolvers);
    }

    private Message channel(MessageKey key) {
        Message configured = channels.get(key.path());
        return configured != null ? configured : new Message.Chat(key.defaultTemplate());
    }

    /** The catalog this facade reads from, for callers that need direct template access. */
    public MessageCatalog catalog() {
        return catalog;
    }
}
