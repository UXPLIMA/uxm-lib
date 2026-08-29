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
 *
 * <p>A <b>base {@link TagResolver}</b> is merged into every render. It is where a house style lives: define
 * {@code <accent>} or {@code <value>} once and every template in every lang file may use them, with no pass
 * over the catalog rewriting anything. The templates a debug dump shows are then the ones an operator wrote,
 * not their expansion. A resolver passed to {@link #send} or {@link #render} wins over the base one of the
 * same name, so a call site can always override a house tag for its own line.
 *
 * <p><b>Reloading.</b> The templates, the channels and the base resolver are held together and swapped as one
 * by {@link #reload}, so {@code /reload} re-reads the words as well as the settings without rebuilding the
 * object graph that already holds this instance. A send in flight keeps the set it started with: a title
 * whose two halves came from different reloads would be exactly the mixed-language defect this class works to
 * avoid. The {@link LocaleSource} is not part of that set — it is a strategy rather than content, and a
 * consumer whose default locale is configurable implements one that reads its own config.
 */
public final class Messages {

    /**
     * The suffix a title's subtitle is addressed by in a lang file: the message's own path plus this, so
     * {@code join.welcome} takes its subtitle from {@code join.welcome.subtitle}.
     */
    public static final String SUBTITLE_SUFFIX = ".subtitle";

    private volatile Content content;

    /** A chat-only facade: every key is delivered as chat with the catalog's template. */
    public Messages(MessageCatalog catalog, LocaleSource locales) {
        this(catalog, locales, Map.of());
    }

    /**
     * @param channels per-key delivery channels, addressed by {@link MessageKey#path()}; a key absent here is
     *     delivered as chat. Copied defensively.
     */
    public Messages(MessageCatalog catalog, LocaleSource locales, Map<String, Message> channels) {
        this(catalog, locales, channels, TagResolver.empty());
    }

    /**
     * @param base tags every render resolves on top of the ones it is passed — a house style's colour roles,
     *     say. A call site's own resolver of the same name wins over it.
     */
    public Messages(MessageCatalog catalog, LocaleSource locales, Map<String, Message> channels, TagResolver base) {
        this.content = new Content(catalog, locales, channels, base);
    }

    /** Replace the templates alone, keeping the locale source, channels and base resolver already in use. */
    public void reload(MessageCatalog catalog) {
        Content current = content;
        reload(catalog, current.locales(), current.channels(), current.base());
    }

    /**
     * Replace everything a render reads, in one step. Sends that have already begun finish with the set they
     * started with; every send after this returns uses the new one. Pass all four even when only one changed:
     * that is what stops a reload leaving one half of what a player sees behind.
     */
    public void reload(MessageCatalog catalog, LocaleSource locales, Map<String, Message> channels, TagResolver base) {
        this.content = new Content(catalog, locales, channels, base);
    }

    /** Render {@code key} for {@code viewer}'s locale, substituting {@code resolvers}, and deliver it. */
    public void send(Audience viewer, MessageKey key, TagResolver... resolvers) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(resolvers, "resolvers");
        Content snapshot = content;
        Message channel = channel(snapshot, key);
        if (channel instanceof Message.TitleText title) {
            showTitle(snapshot, viewer, key, title, resolvers);
            return;
        }
        channel.send(viewer, render(snapshot, viewer, key, resolvers));
    }

    /** The rendered {@link Component} for {@code key} in {@code viewer}'s locale, without delivering it. */
    public Component render(Audience viewer, MessageKey key, TagResolver... resolvers) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(resolvers, "resolvers");
        return render(content, viewer, key, resolvers);
    }

    /** The catalog this facade currently reads from, for callers that need direct template access. */
    public MessageCatalog catalog() {
        return content.catalog();
    }

    private static Component render(Content snapshot, Audience viewer, MessageKey key, TagResolver[] resolvers) {
        Locale locale = snapshot.locales().localeOf(viewer);
        return parse(snapshot, snapshot.catalog().template(key, locale), resolvers);
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
    private static void showTitle(
            Content snapshot, Audience viewer, MessageKey key, Message.TitleText title, TagResolver[] resolvers) {
        MessageKey subtitleKey = subtitleKeyOf(key, title);
        Locale locale = snapshot.locales().localeOf(viewer);
        Locale fallback = snapshot.catalog().defaultLocale();
        if (showFrom(snapshot, viewer, locale, key, subtitleKey, title, resolvers)
                || showFrom(snapshot, viewer, fallback, key, subtitleKey, title, resolvers)) {
            return;
        }
        title.send(viewer, parse(snapshot, title.template(), resolvers), parse(snapshot, title.subtitle(), resolvers));
    }

    /** Show the title from {@code locale}'s own lang file, or report that it does not hold both halves. */
    private static boolean showFrom(
            Content snapshot,
            Audience viewer,
            Locale locale,
            MessageKey key,
            MessageKey subtitleKey,
            Message.TitleText title,
            TagResolver[] resolvers) {
        Optional<String> text = snapshot.catalog().find(key, locale);
        Optional<String> subtitle = snapshot.catalog().find(subtitleKey, locale);
        if (text.isEmpty() || subtitle.isEmpty()) {
            return false;
        }
        title.send(viewer, parse(snapshot, text.get(), resolvers), parse(snapshot, subtitle.get(), resolvers));
        return true;
    }

    /**
     * Parse one template with the base resolver behind the caller's own. MiniMessage gives the last resolver
     * it is handed the final say, so the base goes in first and a call site's {@code <name>} beats a house
     * {@code <name>} rather than being shadowed by it.
     */
    private static Component parse(Content snapshot, String template, TagResolver[] resolvers) {
        if (resolvers.length == 0) {
            return Text.mini(template, snapshot.base());
        }
        TagResolver[] all = new TagResolver[resolvers.length + 1];
        all[0] = snapshot.base();
        System.arraycopy(resolvers, 0, all, 1, resolvers.length);
        return Text.mini(template, TagResolver.resolver(all));
    }

    private static Message channel(Content snapshot, MessageKey key) {
        Message configured = snapshot.channels().get(key.path());
        return configured != null ? configured : new Message.Chat(key.defaultTemplate());
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

    /**
     * Everything a reload replaces, held as one value so a send never sees half of an old set and half of a
     * new one.
     */
    private record Content(
            MessageCatalog catalog, LocaleSource locales, Map<String, Message> channels, TagResolver base) {
        Content {
            Objects.requireNonNull(catalog, "catalog");
            Objects.requireNonNull(locales, "locales");
            Objects.requireNonNull(base, "base");
            channels = Map.copyOf(Objects.requireNonNull(channels, "channels"));
        }
    }
}
