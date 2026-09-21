package com.uxplima.uxmlib.hook.placeholder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Smoke test of the guarded registrar. MockBukkit cannot stand up a real PlaceholderAPI plugin, so we cannot
 * assert a live registration here; what matters for the load-without-the-plugin invariant is that, with PAPI
 * absent, {@code register} is a safe no-op that reports false and never touches the {@code me.clip} classes.
 * The actual routing is covered by {@link PlaceholderRegistryTest}.
 */
class PlaceholderExpansionsTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void registeredHandsBackNothingToUndoWhenPlaceholderApiAbsent() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.register("eco", (player, params) -> "x");

        assertThat(PlaceholderExpansions.registered("uxmtest", registry, "uxmLib", "1.0.0"))
                .describedAs("a consumer that takes its expansion down on disable is handed how to do it,"
                        + " and a server without the plugin has nothing to take down")
                .isEmpty();
    }

    @Test
    void registerIsANoOpReturningFalseWhenPlaceholderApiAbsent() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.register("eco", (player, params) -> "x");

        assertThatCode(() -> {
                    boolean registered = PlaceholderExpansions.register(registry, "uxmLib", "1.0.0");
                    assertThat(registered).isFalse();
                })
                .doesNotThrowAnyException();
    }
}
