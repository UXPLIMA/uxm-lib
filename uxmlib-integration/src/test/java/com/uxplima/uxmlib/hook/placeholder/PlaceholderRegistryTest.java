package com.uxplima.uxmlib.hook.placeholder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.OfflinePlayer;

import org.junit.jupiter.api.Test;

/**
 * Exercises the pure dispatch logic (provider registration and longest-prefix resolution) without the
 * PlaceholderAPI class on the path. The {@code UxmPlaceholderExpansion} subclass is compile-/smoke-only
 * because MockBukkit cannot register a real expansion; the routing it delegates to lives here and is
 * fully tested.
 */
class PlaceholderRegistryTest {

    @Test
    void resolvesByProviderPrefix() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.register("eco", (player, params) -> "balance:" + params);

        assertThat(registry.resolve(null, "eco_top_5")).isEqualTo("balance:top_5");
    }

    @Test
    void picksTheLongestMatchingPrefix() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.register("eco", (player, params) -> "short");
        registry.register("eco_top", (player, params) -> "long:" + params);

        // "eco_top_5" matches both "eco" and "eco_top"; the longer one wins and sees only "5".
        assertThat(registry.resolve(null, "eco_top_5")).isEqualTo("long:5");
    }

    @Test
    void passesEmptyParamsWhenIdentifierIsJustThePrefix() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.register("ping", (player, params) -> "[" + params + "]");

        assertThat(registry.resolve(null, "ping")).isEqualTo("[]");
    }

    @Test
    void returnsNullForAnUnknownPrefixSoPapiFallsThrough() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.register("eco", (player, params) -> "x");

        assertThat(registry.resolve(null, "weather_today")).isNull();
    }

    @Test
    void aThrowingProviderYieldsEmptyAndNeverPropagates() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.register("boom", (player, params) -> {
            throw new IllegalStateException("provider blew up");
        });

        assertThat(registry.resolve(null, "boom_now")).isEmpty();
    }

    @Test
    void aProviderReturningNullYieldsEmptyForThatRequest() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.register("eco", (player, params) -> null);

        assertThat(registry.resolve(null, "eco_top")).isEmpty();
    }

    @Test
    void aLaterRegistrationReplacesTheSamePrefix() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.register("eco", (player, params) -> "first");
        registry.register("eco", (player, params) -> "second");

        assertThat(registry.resolve(null, "eco_x")).isEqualTo("second");
    }

    @Test
    void aSubjectWhoIsNotOnTheServerReachesTheProvider() {
        // A leaderboard line and a tab-list entry both name a player who is not here. The registry must
        // hand the subject on rather than drop it, or a provider that answers from stored data sees null.
        UUID id = UUID.randomUUID();
        OfflinePlayer away = mock(OfflinePlayer.class);
        when(away.getUniqueId()).thenReturn(id);

        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.register(
                "who",
                (player, params) ->
                        player == null ? "nobody" : player.getUniqueId().toString());

        assertThat(registry.resolve(away, "who")).isEqualTo(id.toString());
    }

    @Test
    void aFallbackAnswersWhateverNoPrefixClaims() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.register("eco", (player, params) -> "money");
        registry.fallback((player, params) -> "flat:" + params);

        assertThat(registry.resolve(null, "eco_top")).isEqualTo("money");
        assertThat(registry.resolve(null, "has")).isEqualTo("flat:has");
        assertThat(registry.resolve(null, "tag_plain")).isEqualTo("flat:tag_plain");
    }

    @Test
    void aFallbackThatKnowsNothingLeavesTheTextAsWritten() {
        // Null, not empty: PlaceholderAPI leaves an unknown placeholder in the line exactly as the operator
        // typed it, which is how they find their own typo.
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.fallback((player, params) -> null);

        assertThat(registry.resolve(null, "nonsense")).isNull();
    }

    @Test
    void aThrowingFallbackYieldsEmptyRatherThanBreakingTheLine() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.fallback((player, params) -> {
            throw new IllegalStateException("boom");
        });

        assertThat(registry.resolve(null, "anything")).isEmpty();
    }

    @Test
    void aRegistryWithOnlyAFallbackIsNotEmpty() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.fallback((player, params) -> "");

        assertThat(registry.isEmpty()).isFalse();
    }

    @Test
    void rejectsABlankPrefix() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        assertThat(catchBlank(registry)).isTrue();
    }

    private static boolean catchBlank(PlaceholderRegistry registry) {
        try {
            registry.register("   ", (p, params) -> "");
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }
}
