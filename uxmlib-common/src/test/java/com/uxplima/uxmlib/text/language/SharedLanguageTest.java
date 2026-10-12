package com.uxplima.uxmlib.text.language;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.persistence.PersistentDataType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A language a player chooses in one plugin of ours is the language every other plugin of ours reads them in.
 *
 * <p>Each plugin carries its own copy of this library under its own package, so two plugins never share a class
 * and a service registered by one is invisible to the other. The player's own data is the one place they all
 * reach, so the choice is kept there, under a plain key and as a plain string.
 */
class SharedLanguageTest {

    private static final Locale EN = Locale.ENGLISH;
    private static final Locale TR = Locale.forLanguageTag("tr");
    private static final Locale DE = Locale.GERMAN;

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a choice made in one plugin is the language another plugin reads")
    void oneChoiceForEveryPlugin() {
        PlayerMock player = server.addPlayer();
        LanguageResolver plots = resolver(PlayerLanguages.inMemory());
        LanguageResolver glow = resolver(PlayerLanguages.inMemory());

        plots.choose(player.getUniqueId(), TR);

        assertThat(glow.localeOf(player)).isEqualTo(TR);
    }

    @Test
    @DisplayName("the choice is kept on the player as a plain language tag under uxmlib:language")
    void theKeyIsPlain() {
        PlayerMock player = server.addPlayer();

        resolver(PlayerLanguages.inMemory()).choose(player.getUniqueId(), TR);

        assertThat(player.getPersistentDataContainer().get(SharedLanguage.KEY, PersistentDataType.STRING))
                .isEqualTo("tr");
        assertThat(SharedLanguage.KEY.asString()).isEqualTo("uxmlib:language");
    }

    @Test
    @DisplayName("the shared choice wins over a choice a plugin kept on its own before")
    void theSharedChoiceWinsOverAnOldOne() {
        PlayerMock player = server.addPlayer();
        PlayerLanguages old = PlayerLanguages.inMemory();
        old.choose(player.getUniqueId(), DE);

        resolver(PlayerLanguages.inMemory()).choose(player.getUniqueId(), TR);

        assertThat(resolver(old).localeOf(player)).isEqualTo(TR);
    }

    @Test
    @DisplayName("a choice a plugin kept on its own is shared the first time nothing is shared yet")
    void anOldChoiceIsShared() {
        PlayerMock player = server.addPlayer();
        PlayerLanguages old = PlayerLanguages.inMemory();
        old.choose(player.getUniqueId(), DE);

        assertThat(resolver(old).localeOf(player)).isEqualTo(DE);

        assertThat(resolver(PlayerLanguages.inMemory()).localeOf(player)).isEqualTo(DE);
    }

    @Test
    @DisplayName("a reset in one plugin is not undone by an old choice another plugin kept")
    void aResetStays() {
        PlayerMock player = server.addPlayer();
        player.setLocale(Locale.FRENCH);
        PlayerLanguages old = PlayerLanguages.inMemory();
        old.choose(player.getUniqueId(), DE);
        LanguageResolver first = resolver(PlayerLanguages.inMemory());
        first.choose(player.getUniqueId(), TR);

        first.forget(player.getUniqueId());

        assertThat(resolver(old).localeOf(player)).isEqualTo(Locale.FRENCH);
    }

    @Test
    @DisplayName("a choice made for a player who is away is held, and shared when they join")
    void aChoiceForAnAbsentPlayerWaits() {
        UUID away = UUID.randomUUID();
        PlayerLanguages store = PlayerLanguages.inMemory();
        LanguageResolver admin = resolver(store);

        admin.choose(away, TR);

        assertThat(store.held(away)).contains(new HeldChoice(TR));
        PlayerMock joined = join("Away", away);
        admin.settle(joined);
        assertThat(resolver(PlayerLanguages.inMemory()).localeOf(joined)).isEqualTo(TR);
        assertThat(store.held(away)).isEmpty();
    }

    @Test
    @DisplayName("a reset made for a player who is away clears their shared choice when they join")
    void aResetForAnAbsentPlayerWaits() {
        PlayerMock player = server.addPlayer();
        player.setLocale(Locale.FRENCH);
        resolver(PlayerLanguages.inMemory()).choose(player.getUniqueId(), TR);
        UUID id = player.getUniqueId();
        player.disconnect();
        PlayerLanguages store = PlayerLanguages.inMemory();
        LanguageResolver admin = resolver(store);

        admin.forget(id);
        player.reconnect();
        admin.settle(player);

        assertThat(resolver(PlayerLanguages.inMemory()).localeOf(player)).isEqualTo(Locale.FRENCH);
    }

    @Test
    @DisplayName("a player's own choice after an operator's held one is the one that stays")
    void theNewerChoiceWins() {
        UUID id = UUID.randomUUID();
        PlayerLanguages store = PlayerLanguages.inMemory();
        LanguageResolver plugin = resolver(store);
        plugin.choose(id, TR);
        PlayerMock joined = join("Later", id);

        plugin.choose(id, DE);
        plugin.settle(joined);

        assertThat(plugin.localeOf(joined)).isEqualTo(DE);
    }

    @Test
    @DisplayName("away from the player's thread the last answer read is used, and a write waits for that thread")
    void offTheThreadNothingTouchesThePlayer() {
        PlayerMock player = server.addPlayer();
        boolean[] owned = {true};
        List<Runnable> queued = new ArrayList<>();
        SharedLanguage shared = SharedLanguage.with(who -> owned[0], (who, work) -> queued.add(work));
        LanguageResolver resolver = new LanguageResolver(
                () -> LanguageSettings.following(EN), PlayerLanguages.inMemory(), Optional::empty, shared);
        resolver.choose(player.getUniqueId(), TR);
        assertThat(resolver.localeOf(player)).isEqualTo(TR);

        owned[0] = false;
        resolver.choose(player.getUniqueId(), DE);

        assertThat(resolver.localeOf(player)).isEqualTo(DE);
        assertThat(player.getPersistentDataContainer().get(SharedLanguage.KEY, PersistentDataType.STRING))
                .isEqualTo("tr");
        queued.forEach(Runnable::run);
        assertThat(player.getPersistentDataContainer().get(SharedLanguage.KEY, PersistentDataType.STRING))
                .isEqualTo("de");
    }

    @Test
    @DisplayName("a server that does not follow the client starts a player in its default")
    void aServerWithOneAudience() {
        PlayerMock player = server.addPlayer();
        player.setLocale(EN);
        LanguageResolver resolver =
                new LanguageResolver(new LanguageSettings(DE, false, null), PlayerLanguages.inMemory());

        assertThat(resolver.localeOf(player)).isEqualTo(DE);
    }

    private static LanguageResolver resolver(PlayerLanguages store) {
        return new LanguageResolver(LanguageSettings.following(EN), store);
    }

    private PlayerMock join(String name, UUID id) {
        PlayerMock player = new PlayerMock(server, name, id);
        server.addPlayer(player);
        return player;
    }
}
