package com.uxplima.uxmlib.text.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.kyori.adventure.audience.Audience;

import org.junit.jupiter.api.Test;

/** The order a viewer's language is resolved in: server force, own choice, client, default. */
class LanguageResolverTest {

    private static final Locale EN = Locale.ENGLISH;
    private static final Locale TR = Locale.forLanguageTag("tr");
    private static final Locale DE = Locale.GERMAN;
    private static final UUID WHO = UUID.randomUUID();

    @Test
    void theLanguageTheServerForcesWinsOverEverything() {
        PlayerLanguages store = PlayerLanguages.inMemory();
        store.choose(WHO, TR);
        LanguageResolver resolver = new LanguageResolver(new LanguageSettings(EN, true, DE), store);

        assertThat(resolver.localeOf(player(TR))).isEqualTo(DE);
    }

    @Test
    void theOwnChoiceWinsOverTheClient() {
        PlayerLanguages store = PlayerLanguages.inMemory();
        store.choose(WHO, TR);
        LanguageResolver resolver = new LanguageResolver(new LanguageSettings(EN, true, null), store);

        assertThat(resolver.localeOf(player(DE))).isEqualTo(TR);
    }

    @Test
    void aRegisteredServiceAnswersBeforeTheOwnStore() {
        PlayerLanguages store = PlayerLanguages.inMemory();
        store.choose(WHO, TR);
        LanguageService network = new FixedService(Optional.of(DE));
        LanguageResolver resolver =
                new LanguageResolver(new LanguageSettings(EN, true, null), store, () -> Optional.of(network));

        assertThat(resolver.localeOf(player(EN))).isEqualTo(DE);
    }

    @Test
    void theOwnStoreAnswersWhenTheServiceHasNoChoice() {
        PlayerLanguages store = PlayerLanguages.inMemory();
        store.choose(WHO, TR);
        LanguageResolver resolver = new LanguageResolver(
                new LanguageSettings(EN, true, null), store, () -> Optional.of(new FixedService(Optional.empty())));

        assertThat(resolver.localeOf(player(EN))).isEqualTo(TR);
    }

    @Test
    void theRememberedClientLanguageBeatsTheOneTheClientHasNotSentYet() {
        PlayerLanguages store = PlayerLanguages.inMemory();
        store.rememberClient(WHO, TR);
        LanguageResolver resolver = new LanguageResolver(new LanguageSettings(EN, true, null), store);

        assertThat(resolver.localeOf(player(EN))).isEqualTo(TR);
    }

    @Test
    void theClientLanguageIsUsedWhenNothingIsRemembered() {
        LanguageResolver resolver =
                new LanguageResolver(new LanguageSettings(EN, true, null), PlayerLanguages.inMemory());

        assertThat(resolver.localeOf(player(DE))).isEqualTo(DE);
    }

    @Test
    void theClientIsIgnoredWhenTheServerDoesNotFollowIt() {
        LanguageResolver resolver =
                new LanguageResolver(new LanguageSettings(EN, false, null), PlayerLanguages.inMemory());

        assertThat(resolver.localeOf(player(DE))).isEqualTo(EN);
    }

    @Test
    void anAudienceThatIsNotAPlayerReadsTheDefault() {
        LanguageResolver resolver =
                new LanguageResolver(new LanguageSettings(TR, true, null), PlayerLanguages.inMemory());

        assertThat(resolver.localeOf(Audience.empty())).isEqualTo(TR);
        assertThat(resolver.defaultLocale()).isEqualTo(TR);
    }

    @Test
    void choosingWritesToTheServiceWhenThereIsOne() {
        PlayerLanguages store = PlayerLanguages.inMemory();
        FixedService network = new FixedService(Optional.empty());
        LanguageResolver resolver =
                new LanguageResolver(new LanguageSettings(EN, true, null), store, () -> Optional.of(network));

        resolver.choose(WHO, TR);

        assertThat(network.chosen).contains(TR);
        assertThat(store.chosen(WHO)).contains(TR);
    }

    private static Player player(Locale clientLocale) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(WHO);
        when(player.locale()).thenReturn(clientLocale);
        return player;
    }

    /** A stand-in for uxmLang: it answers for the network, or it does not answer at all. */
    private static final class FixedService implements LanguageService {

        private Optional<Locale> chosen;

        private FixedService(Optional<Locale> answer) {
            this.chosen = answer;
        }

        @Override
        public Optional<Locale> languageOf(UUID player) {
            return chosen;
        }

        @Override
        public void choose(UUID player, Locale locale) {
            this.chosen = Optional.of(locale);
        }

        @Override
        public void forget(UUID player) {
            this.chosen = Optional.empty();
        }
    }
}
