package com.uxplima.uxmlib.text.language;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** An operator names a player, and the name is read as someone online, someone away, or nobody. */
class LanguageChoicesForAnotherTest {

    private ServerMock server;

    private LanguageChoices choices;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        LanguageSettings settings = LanguageSettings.following(Locale.ENGLISH);
        choices = new LanguageChoices(
                () -> Set.of(Locale.ENGLISH), new LanguageResolver(settings, PlayerLanguages.inMemory()), settings);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a name online is that player, whatever case it is typed in")
    void online() {
        PlayerMock steve = server.addPlayer("Steve");

        assertThat(choices.named("steve")).isEqualTo(new LanguageChoices.Named.Online(steve));
    }

    @Test
    @DisplayName("a name that has played here and is away is that player's id")
    void away() {
        PlayerMock alex = server.addPlayer("Alex");
        alex.disconnect();

        assertThat(choices.named("Alex")).isEqualTo(new LanguageChoices.Named.Away(alex.getUniqueId(), "Alex"));
    }

    @Test
    @DisplayName("a name nobody has played under is nobody")
    void unknown() {
        assertThat(choices.named("Nobody")).isEqualTo(new LanguageChoices.Named.Unknown("Nobody"));
    }
}
