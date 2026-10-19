package com.uxplima.uxmlib.hook;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmlib.hook.economy.VaultEconomy;
import com.uxplima.uxmlib.hook.economy.VaultUnlockedEconomy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Tests the load-without-the-plugin invariant: with no soft-dependency installed, the hooks report
 * absent and degrade gracefully rather than throwing.
 */
class HooksTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void reportsAbsentPluginsAsNotPresent() {
        assertThat(Hooks.isPresent("PlaceholderAPI")).isFalse();
        assertThat(Hooks.isPresent("Vault")).isFalse();
    }

    /**
     * A presence check is asked from anywhere, and a unit test that mocks a player stands no server behind
     * Bukkit. This used to throw there, which made every static hook a call a test had to route around.
     */
    @Test
    void aServerThatDoesNotExistHasNothingInstalled() {
        MockBukkit.unmock();
        try {
            assertThat(Hooks.isPresent("PlaceholderAPI")).isFalse();
        } finally {
            MockBukkit.mock();
        }
    }

    @Test
    void placeholdersReturnTextUnchangedWhenApiAbsent() {
        var player = MockBukkit.getMock().addPlayer();
        assertThat(PlaceholderApi.isAvailable()).isFalse();
        assertThat(PlaceholderApi.apply(player, "hi %server_online%")).isEqualTo("hi %server_online%");
    }

    /**
     * The player's own name and id need no plugin. A server without PlaceholderAPI, or with it and without its
     * Player expansion, printed {@code %player_name%} as written in every effect line an operator wrote.
     */
    @Test
    void thePlayersOwnNameAndIdNeedNoPlaceholderApi() {
        var player = MockBukkit.getMock().addPlayer("Steve");

        assertThat(PlaceholderApi.apply(player, "hi %player_name%, %player_uuid%"))
                .isEqualTo("hi Steve, " + player.getUniqueId());
    }

    @Test
    void vaultEconomyIsEmptyWhenVaultAbsent() {
        assertThat(VaultEconomy.find()).isEmpty();
    }

    @Test
    void vaultUnlockedEconomyIsEmptyWhenNoProviderIsRegistered() {
        assertThat(VaultUnlockedEconomy.find()).isEmpty();
    }

    /**
     * The guard the vault2 hook used to carry. It is kept as a test rather than as a comment, because it is
     * the reason that hook was dead: VaultUnlocked declares itself as {@code Vault}, so no server has ever
     * reported a plugin under this name, and a name guard would return empty on every one of them.
     */
    @Test
    void noServerRunsAPluginCalledVaultUnlocked() {
        assertThat(Hooks.isPresent("VaultUnlocked")).isFalse();
    }
}
