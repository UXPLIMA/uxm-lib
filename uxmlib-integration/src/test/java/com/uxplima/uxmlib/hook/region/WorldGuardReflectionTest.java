package com.uxplima.uxmlib.hook.region;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The one reflective way into WorldGuard, on the parts that can be proven without a live WorldGuard: the two
 * present-guards and how a flag state is read. The region walk itself is proven where a caller uses it, in
 * {@code WorldGuardClaimProviderTest}.
 */
final class WorldGuardReflectionTest {

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
    @DisplayName("a server without WorldGuard has it neither installed nor enabled")
    void aServerWithoutWorldGuard() {
        assertThat(WorldGuardReflection.isInstalled(server)).isFalse();
        assertThat(WorldGuardReflection.isEnabled(server)).isFalse();
    }

    @Test
    @DisplayName("a server with WorldGuard has it installed and enabled")
    void aServerWithWorldGuard() {
        MockBukkit.createMockPlugin(WorldGuardReflection.PLUGIN);

        assertThat(WorldGuardReflection.isInstalled(server)).isTrue();
        assertThat(WorldGuardReflection.isEnabled(server)).isTrue();
    }

    @Test
    @DisplayName("a flag denies only when its state is the constant named DENY")
    void onlyDenyDenies() {
        assertThat(WorldGuardReflection.isDeny(State.DENY)).isTrue();
        assertThat(WorldGuardReflection.isDeny(State.ALLOW)).isFalse();
        assertThat(WorldGuardReflection.isDeny(null)).isFalse();
        assertThat(WorldGuardReflection.isDeny("DENY")).isFalse();
    }

    /** Stands in for WorldGuard's {@code StateFlag.State}, which is read by the name of its constant. */
    private enum State {
        ALLOW,
        DENY
    }
}
