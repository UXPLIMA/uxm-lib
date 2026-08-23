package com.uxplima.uxmlib.packet.tablist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.Test;

/** Copy-operation contract of the pure state passed through outbound packet transformers. */
class PlayerInfoStateTest {

    @Test
    void copyMethodsChangeExactlyOneField() {
        UUID id = UUID.randomUUID();
        PlayerInfoState original =
                new PlayerInfoState(id, true, 10, PlayerInfoGameMode.SURVIVAL, Component.text("before"), true, 20);

        PlayerInfoState changed = original.withListed(false)
                .withLatency(99)
                .withGameMode(PlayerInfoGameMode.SPECTATOR)
                .withDisplayName(Component.text("after"))
                .withShowHat(false)
                .withListOrder(80);

        assertThat(changed.id()).isEqualTo(id);
        assertThat(changed.listed()).isFalse();
        assertThat(changed.latency()).isEqualTo(99);
        assertThat(changed.gameMode()).isEqualTo(PlayerInfoGameMode.SPECTATOR);
        assertThat(changed.displayName()).isEqualTo(Component.text("after"));
        assertThat(changed.showHat()).isFalse();
        assertThat(changed.listOrder()).isEqualTo(80);
    }

    @Test
    void displayNameCanBeExplicitlyCleared() {
        PlayerInfoState original = new PlayerInfoState(
                UUID.randomUUID(), true, 0, PlayerInfoGameMode.SURVIVAL, Component.text("name"), true, 0);

        assertThat(original.withDisplayName(null).displayName()).isNull();
    }
}
