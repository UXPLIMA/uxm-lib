package com.uxplima.uxmlib.text.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Locale;

import org.bukkit.entity.Player;

import net.kyori.adventure.audience.Audience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The locale a viewer reads in is never null, whatever the player in front of it answers. */
class LocaleSourceTest {

    @Test
    @DisplayName("a player whose client has not said its language reads the default, not null")
    void aPlayerWithNoLocaleReadsTheDefault() {
        Player player = mock(Player.class);
        when(player.locale()).thenReturn(null);

        assertThat(LocaleSource.ofDefault(Locale.ENGLISH).localeOf(player)).isEqualTo(Locale.ENGLISH);
    }

    @Test
    @DisplayName("a player's own language wins, and anybody else reads the default")
    void aPlayersOwnLanguageWins() {
        Player player = mock(Player.class);
        when(player.locale()).thenReturn(Locale.forLanguageTag("tr"));

        LocaleSource source = LocaleSource.ofDefault(Locale.ENGLISH);
        assertThat(source.localeOf(player)).isEqualTo(Locale.forLanguageTag("tr"));
        assertThat(source.localeOf(Audience.empty())).isEqualTo(Locale.ENGLISH);
    }
}
