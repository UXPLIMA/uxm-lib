package com.uxplima.uxmlib.packet.tablist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.Test;

/** Contract tests for the complete, NMS-free synthetic player-info value. */
class PlayerInfoEntryTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @Test
    void conventionalFactoryUsesExplicitProtocolDefaults() {
        PlayerInfoEntry entry = PlayerInfoEntry.of(ID, Component.text("row"));

        assertThat(entry.id()).isEqualTo(ID);
        assertThat(entry.displayName()).isEqualTo(Component.text("row"));
        assertThat(entry.listOrder()).isZero();
        assertThat(entry.skin()).isNull();
        assertThat(entry.name()).isNull();
        assertThat(entry.listed()).isTrue();
        assertThat(entry.latency()).isZero();
        assertThat(entry.gameMode()).isEqualTo(PlayerInfoGameMode.SURVIVAL);
        assertThat(entry.showHat()).isTrue();
    }

    @Test
    void carriesEveryProtocolVisibleSyntheticEntryField() {
        TabSkin skin = new TabSkin("texture", "signature");
        PlayerInfoEntry entry = new PlayerInfoEntry(
                ID, Component.text("spectator"), 73, skin, "Profile", false, 145, PlayerInfoGameMode.SPECTATOR, false);

        assertThat(entry.listOrder()).isEqualTo(73);
        assertThat(entry.skin()).isEqualTo(skin);
        assertThat(entry.profileName()).isEqualTo("Profile");
        assertThat(entry.listed()).isFalse();
        assertThat(entry.latency()).isEqualTo(145);
        assertThat(entry.gameMode()).isEqualTo(PlayerInfoGameMode.SPECTATOR);
        assertThat(entry.showHat()).isFalse();
    }

    @Test
    void legacyEntryUpgradePreservesValuesAndHistoricalDefaults() {
        TabSkin skin = TabSkin.unsigned("texture");
        TabEntry legacy = new TabEntry(ID, Component.text("legacy"), 42, skin, "Legacy");

        PlayerInfoEntry entry = legacy.toPlayerInfoEntry();

        assertThat(entry.id()).isEqualTo(ID);
        assertThat(entry.displayName()).isEqualTo(Component.text("legacy"));
        assertThat(entry.listOrder()).isEqualTo(42);
        assertThat(entry.skin()).isEqualTo(skin);
        assertThat(entry.name()).isEqualTo("Legacy");
        assertThat(entry.listed()).isTrue();
        assertThat(entry.latency()).isZero();
        assertThat(entry.gameMode()).isEqualTo(PlayerInfoGameMode.SURVIVAL);
        assertThat(entry.showHat()).isTrue();
    }

    @Test
    void derivedAndExplicitProfileNamesStayInsideTheProtocolLimit() {
        PlayerInfoEntry derived = PlayerInfoEntry.of(ID, Component.empty());
        PlayerInfoEntry explicit = new PlayerInfoEntry(
                ID, Component.empty(), 0, null, "a_seventeen_chars", true, 0, PlayerInfoGameMode.SURVIVAL, true);

        assertThat(derived.profileName()).isEqualTo(ID.toString().substring(0, 16));
        assertThat(explicit.profileName()).isEqualTo("a_seventeen_char");
    }

    @Test
    void rejectsNullRequiredFields() {
        assertThatNullPointerException()
                .isThrownBy(() -> new PlayerInfoEntry(
                        nullUuid(), Component.empty(), 0, null, null, true, 0, PlayerInfoGameMode.SURVIVAL, true));
        assertThatNullPointerException()
                .isThrownBy(() -> new PlayerInfoEntry(
                        ID, nullComponent(), 0, null, null, true, 0, PlayerInfoGameMode.SURVIVAL, true));
        assertThatNullPointerException()
                .isThrownBy(() -> new PlayerInfoEntry(ID, Component.empty(), 0, null, null, true, 0, nullMode(), true));
    }

    @SuppressWarnings("NullAway")
    private static UUID nullUuid() {
        return null;
    }

    @SuppressWarnings("NullAway")
    private static Component nullComponent() {
        return null;
    }

    @SuppressWarnings("NullAway")
    private static PlayerInfoGameMode nullMode() {
        return null;
    }
}
