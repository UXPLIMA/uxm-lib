package com.uxplima.uxmlib.text.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Ties the catalog, locale source and delivery together: a player's own locale drives which template is
 * rendered, the supplied placeholders are substituted, and the per-key channel routes the send.
 */
class MessagesTest {

    private static final MessageKey WELCOME = MessageKey.of("join.welcome", "<green>Welcome <name>");

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** A house-style tag: everything after {@code <accent>} is blue, the way a real style layer defines it. */
    private static TagResolver accent() {
        return TagResolver.resolver("accent", Tag.styling(NamedTextColor.BLUE));
    }

    /** Whether any component in the tree carries {@code color}; MiniMessage may nest the styled part. */
    private static boolean paintedWith(Component component, NamedTextColor color) {
        if (color.equals(component.color())) {
            return true;
        }
        return component.children().stream().anyMatch(child -> paintedWith(child, color));
    }

    private MessageCatalog catalog() {
        return new MessageCatalog(
                Map.of(
                        Locale.GERMAN, Map.of("join.welcome", "<green>Willkommen <name>"),
                        Locale.ENGLISH, Map.of("join.welcome", "<green>Welcome <name>")),
                Locale.ENGLISH);
    }

    @Test
    void rendersThePlayerLocaleTemplateWithPlaceholders() {
        PlayerMock player = server.addPlayer();
        player.setLocale(Locale.GERMAN);
        Messages messages = new Messages(catalog(), LocaleSource.ofDefault(Locale.ENGLISH));

        Component rendered = messages.render(player, WELCOME, Text.placeholder("name", "Steve"));

        assertThat(Text.plain(rendered)).isEqualTo("Willkommen Steve");
    }

    @Test
    void sendDeliversChatByDefault() {
        PlayerMock player = server.addPlayer();
        player.setLocale(Locale.ENGLISH);
        Messages messages = new Messages(catalog(), LocaleSource.ofDefault(Locale.ENGLISH));

        messages.send(player, WELCOME, Text.placeholder("name", "Alex"));

        assertThat(Text.plain(player.nextComponentMessage())).isEqualTo("Welcome Alex");
    }

    @Test
    void aConfiguredActionBarChannelRoutesTheSameText() {
        PlayerMock player = server.addPlayer();
        player.setLocale(Locale.ENGLISH);
        Map<String, Message> channels = Map.of(WELCOME.path(), new Message.ActionBar("<unused>"));
        Messages messages = new Messages(catalog(), LocaleSource.ofDefault(Locale.ENGLISH), channels);

        messages.send(player, WELCOME, Text.placeholder("name", "Alex"));

        assertThat(player.nextComponentMessage()).isNull();
        assertThat(Text.plain(player.nextActionBar())).isEqualTo("Welcome Alex");
    }

    @Test
    void aConfiguredTitleChannelRendersBothTitleAndItsSubtitle() {
        // MockBukkit does not round-trip Adventure showTitle into a readable queue, so capture the Title at
        // the Adventure layer. With no subtitle in the catalog both halves come from the operator's entry.
        CapturingTitleAudience viewer = new CapturingTitleAudience();
        Message.TitleText channel = new Message.TitleText(
                "<yellow>Hello <name>", "<gray>welcome <name>", Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO);
        Map<String, Message> channels = Map.of(WELCOME.path(), channel);
        Messages messages = new Messages(catalog(), LocaleSource.ofDefault(Locale.ENGLISH), channels);

        messages.send(viewer, WELCOME, Text.placeholder("name", "Alex"));

        net.kyori.adventure.title.Title shown = viewer.shown();
        assertThat(Text.plain(shown.title())).isEqualTo("Hello Alex");
        assertThat(Text.plain(shown.subtitle())).isEqualTo("welcome Alex");
    }

    private static Message.TitleText titleChannel() {
        return new Message.TitleText(
                "<yellow>Hello <name>", "<gray>welcome <name>", Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO);
    }

    private static Messages germanMessages(MessageCatalog catalog) {
        Map<String, Message> channels = Map.of(WELCOME.path(), titleChannel());
        return new Messages(catalog, LocaleSource.ofDefault(Locale.GERMAN), channels);
    }

    @Test
    void aTitleWhoseBothHalvesAreTranslatedComesEntirelyFromTheCatalog() {
        CapturingTitleAudience viewer = new CapturingTitleAudience();
        MessageCatalog catalog = new MessageCatalog(
                Map.of(
                        Locale.GERMAN,
                        Map.of(
                                "join.welcome", "<green>Willkommen <name>",
                                "join.welcome.subtitle", "<gray>schön dich zu sehen")),
                Locale.GERMAN);

        germanMessages(catalog).send(viewer, WELCOME, Text.placeholder("name", "Alex"));

        net.kyori.adventure.title.Title shown = viewer.shown();
        assertThat(Text.plain(shown.title())).isEqualTo("Willkommen Alex");
        assertThat(Text.plain(shown.subtitle())).isEqualTo("schön dich zu sehen");
    }

    @Test
    void aTitleTranslatedWithoutItsSubtitleFallsBackWholeRatherThanAnsweringInTwoLanguages() {
        CapturingTitleAudience viewer = new CapturingTitleAudience();
        MessageCatalog catalog = new MessageCatalog(
                Map.of(Locale.GERMAN, Map.of("join.welcome", "<green>Willkommen <name>")), Locale.GERMAN);

        germanMessages(catalog).send(viewer, WELCOME, Text.placeholder("name", "Alex"));

        net.kyori.adventure.title.Title shown = viewer.shown();
        // Not "Willkommen Alex" over an English subtitle: the title is the unit, so both halves fall back.
        assertThat(Text.plain(shown.title())).isEqualTo("Hello Alex");
        assertThat(Text.plain(shown.subtitle())).isEqualTo("welcome Alex");
    }

    @Test
    void aDeliberatelyEmptySubtitleInTheCatalogCountsAsTranslated() {
        CapturingTitleAudience viewer = new CapturingTitleAudience();
        MessageCatalog catalog = new MessageCatalog(
                Map.of(
                        Locale.GERMAN,
                        Map.of(
                                "join.welcome", "<green>Willkommen <name>",
                                "join.welcome.subtitle", "")),
                Locale.GERMAN);

        germanMessages(catalog).send(viewer, WELCOME, Text.placeholder("name", "Alex"));

        net.kyori.adventure.title.Title shown = viewer.shown();
        // Empty is a choice a translator made; missing is an omission. They must not look the same.
        assertThat(Text.plain(shown.title())).isEqualTo("Willkommen Alex");
        assertThat(Text.plain(shown.subtitle())).isEmpty();
    }

    @Test
    void oneLocaleMissingItsSubtitleDoesNotDecideForALocaleThatHasIt() {
        MessageCatalog catalog = new MessageCatalog(
                Map.of(
                        Locale.ENGLISH,
                        Map.of(
                                "join.welcome", "<green>Welcome <name>",
                                "join.welcome.subtitle", "<gray>good to see you"),
                        Locale.GERMAN,
                        Map.of(
                                "join.welcome", "<green>Willkommen <name>",
                                "join.welcome.subtitle", "<gray>schön dich zu sehen"),
                        Locale.FRENCH,
                        Map.of("join.welcome", "<green>Bienvenue <name>")),
                Locale.ENGLISH);
        Map<String, Message> channels = Map.of(WELCOME.path(), titleChannel());

        CapturingTitleAudience french = new CapturingTitleAudience();
        new Messages(catalog, LocaleSource.ofDefault(Locale.FRENCH), channels)
                .send(french, WELCOME, Text.placeholder("name", "Alex"));
        CapturingTitleAudience german = new CapturingTitleAudience();
        new Messages(catalog, LocaleSource.ofDefault(Locale.GERMAN), channels)
                .send(german, WELCOME, Text.placeholder("name", "Alex"));

        // The half-finished French file costs French its own translation and falls to the default locale as
        // a pair; the finished German one is untouched by it.
        assertThat(Text.plain(french.shown().title())).isEqualTo("Welcome Alex");
        assertThat(Text.plain(french.shown().subtitle())).isEqualTo("good to see you");
        assertThat(Text.plain(german.shown().title())).isEqualTo("Willkommen Alex");
        assertThat(Text.plain(german.shown().subtitle())).isEqualTo("schön dich zu sehen");
    }

    /** A minimal Audience that records the {@link net.kyori.adventure.title.Title} shown to it. */
    private static final class CapturingTitleAudience implements net.kyori.adventure.audience.Audience {
        private net.kyori.adventure.title.@org.jspecify.annotations.Nullable Title shown;

        @Override
        public void showTitle(net.kyori.adventure.title.Title title) {
            this.shown = title;
        }

        net.kyori.adventure.title.Title shown() {
            return java.util.Objects.requireNonNull(shown, "no title was shown");
        }
    }

    @Test
    void aBaseResolverIsAvailableToEveryTemplateWithoutRewritingAnyOfThem() {
        PlayerMock player = server.addPlayer();
        player.setLocale(Locale.ENGLISH);
        MessageCatalog catalog = new MessageCatalog(
                Map.of(Locale.ENGLISH, Map.of("join.welcome", "<accent>Welcome <name>")), Locale.ENGLISH);
        Messages messages = new Messages(catalog, LocaleSource.ofDefault(Locale.ENGLISH), Map.of(), accent());

        Component rendered = messages.render(player, WELCOME, Text.placeholder("name", "Alex"));

        assertThat(Text.plain(rendered)).isEqualTo("Welcome Alex");
        assertThat(paintedWith(rendered, NamedTextColor.BLUE)).isTrue();
    }

    @Test
    void aCallSiteResolverWinsOverTheBaseOneOfTheSameName() {
        PlayerMock player = server.addPlayer();
        player.setLocale(Locale.ENGLISH);
        MessageCatalog catalog =
                new MessageCatalog(Map.of(Locale.ENGLISH, Map.of("join.welcome", "<who>")), Locale.ENGLISH);
        Messages messages = new Messages(
                catalog, LocaleSource.ofDefault(Locale.ENGLISH), Map.of(), Text.placeholder("who", "the house"));

        Component rendered = messages.render(player, WELCOME, Text.placeholder("who", "this line"));

        assertThat(Text.plain(rendered)).isEqualTo("this line");
    }

    @Test
    void theBaseResolverReachesBothHalvesOfATitle() {
        CapturingTitleAudience viewer = new CapturingTitleAudience();
        MessageCatalog catalog = new MessageCatalog(
                Map.of(
                        Locale.ENGLISH,
                        Map.of(
                                "join.welcome", "<accent>Welcome",
                                "join.welcome.subtitle", "<accent>good to see you")),
                Locale.ENGLISH);
        Messages messages = new Messages(
                catalog, LocaleSource.ofDefault(Locale.ENGLISH), Map.of(WELCOME.path(), titleChannel()), accent());

        messages.send(viewer, WELCOME);

        net.kyori.adventure.title.Title shown = viewer.shown();
        assertThat(paintedWith(shown.title(), NamedTextColor.BLUE)).isTrue();
        assertThat(paintedWith(shown.subtitle(), NamedTextColor.BLUE)).isTrue();
    }

    @Test
    void reloadingSwapsTheWordsForAnInstanceThatIsAlreadyInjectedEverywhere() {
        PlayerMock player = server.addPlayer();
        player.setLocale(Locale.GERMAN);
        Messages messages = new Messages(catalog(), LocaleSource.ofDefault(Locale.ENGLISH));

        messages.reload(new MessageCatalog(
                Map.of(Locale.GERMAN, Map.of("join.welcome", "<green>Servus <name>")), Locale.GERMAN));

        assertThat(Text.plain(messages.render(player, WELCOME, Text.placeholder("name", "Alex"))))
                .isEqualTo("Servus Alex");
        assertThat(messages.catalog().defaultLocale()).isEqualTo(Locale.GERMAN);
    }

    @Test
    void reloadingAlsoSwapsTheChannelAndTheBaseResolver() {
        PlayerMock player = server.addPlayer();
        player.setLocale(Locale.ENGLISH);
        MessageCatalog catalog =
                new MessageCatalog(Map.of(Locale.ENGLISH, Map.of("join.welcome", "<accent>Welcome")), Locale.ENGLISH);
        Messages messages = new Messages(catalog, LocaleSource.ofDefault(Locale.ENGLISH));

        messages.reload(
                catalog,
                LocaleSource.ofDefault(Locale.ENGLISH),
                Map.of(WELCOME.path(), new Message.ActionBar("<unused>")),
                accent());
        messages.send(player, WELCOME);

        assertThat(player.nextComponentMessage()).isNull();
        assertThat(paintedWith(player.nextActionBar(), NamedTextColor.BLUE)).isTrue();
    }

    @Test
    void reloadingTheLocaleSourceMovesTheAnswerAConsoleGets() {
        Map<Locale, Map<String, String>> templates = Map.of(
                Locale.ENGLISH, Map.of("join.welcome", "<green>Welcome <name>"),
                Locale.GERMAN, Map.of("join.welcome", "<green>Willkommen <name>"));
        Messages messages =
                new Messages(new MessageCatalog(templates, Locale.ENGLISH), LocaleSource.ofDefault(Locale.ENGLISH));
        Audience console = Audience.empty();

        // Everything a render reads moves together: the words and the language a non-player is answered in.
        messages.reload(
                new MessageCatalog(templates, Locale.GERMAN),
                LocaleSource.ofDefault(Locale.GERMAN),
                Map.of(),
                TagResolver.empty());

        assertThat(Text.plain(messages.render(console, WELCOME, Text.placeholder("name", "Alex"))))
                .isEqualTo("Willkommen Alex");
    }

    /**
     * The trap the one-argument reload sets, pinned so the javadoc that warns about it stays true: a new
     * catalog whose default locale is German still reaches a console in English, because the locale source
     * from before the reload is the one being asked.
     */
    @Test
    void reloadingTheCatalogAloneKeepsTheLocaleSourceItWasBuiltWith() {
        Map<Locale, Map<String, String>> templates = Map.of(
                Locale.ENGLISH, Map.of("join.welcome", "<green>Welcome <name>"),
                Locale.GERMAN, Map.of("join.welcome", "<green>Willkommen <name>"));
        Messages messages =
                new Messages(new MessageCatalog(templates, Locale.ENGLISH), LocaleSource.ofDefault(Locale.ENGLISH));
        Audience console = Audience.empty();

        messages.reload(new MessageCatalog(templates, Locale.GERMAN));

        assertThat(Text.plain(messages.render(console, WELCOME, Text.placeholder("name", "Alex"))))
                .isEqualTo("Welcome Alex");
    }

    /** A plugin that computes text has to style it for the language this facade is serving that viewer. */
    @Test
    void theLocaleAViewerIsServedCanBeAsked() {
        PlayerMock player = server.addPlayer();
        player.setLocale(Locale.GERMAN);
        Messages messages = new Messages(catalog(), LocaleSource.ofDefault(Locale.ENGLISH));

        assertThat(messages.localeOf(player)).isEqualTo(Locale.GERMAN);
        assertThat(messages.localeOf(Audience.empty())).isEqualTo(Locale.ENGLISH);
    }

    /** It answers off the live snapshot, so a reload that swapped the source is not answered with the old one. */
    @Test
    void theLocaleFollowsASourceSwappedByAReload() {
        Messages messages = new Messages(catalog(), LocaleSource.ofDefault(Locale.ENGLISH));

        messages.reload(catalog(), LocaleSource.ofDefault(Locale.FRENCH), Map.of(), TagResolver.empty());

        assertThat(messages.localeOf(Audience.empty())).isEqualTo(Locale.FRENCH);
    }

    @Test
    void playerLocaleSourceReadsThePlayersOwnLocale() {
        PlayerMock player = server.addPlayer();
        player.setLocale(Locale.GERMAN);
        LocaleSource source = LocaleSource.ofDefault(Locale.ENGLISH);

        assertThat(source.localeOf(player)).isEqualTo(Locale.GERMAN);
    }

    @Test
    void nonPlayerAudienceFallsBackToTheDefaultLocale() {
        LocaleSource source = LocaleSource.ofDefault(Locale.FRENCH);

        assertThat(source.localeOf(net.kyori.adventure.audience.Audience.empty()))
                .isEqualTo(Locale.FRENCH);
    }
}
