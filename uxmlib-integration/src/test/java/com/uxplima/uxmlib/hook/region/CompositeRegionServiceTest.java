package com.uxplima.uxmlib.hook.region;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every region plugin a server runs, asked together.
 *
 * <p>{@code RegionHooks.active()} answers with the first present provider and nothing else, which is right
 * for "may this player build here" and wrong for everything else. A server running WorldGuard for its spawn
 * and Towny for its towns has two region models, and asking only the first makes a town invisible to
 * anything that reads a region name: a bonus written against a town would never fire and nothing would say
 * why.
 *
 * <p>The folds are the subject here, because a fold is where the safe answer and the convenient one differ.
 */
final class CompositeRegionServiceTest {

    /**
     * A point nobody reads.
     *
     * <p>Every fake below answers without looking at where it was asked about, because the subject here is
     * the fold and not the lookup. A real location would be a MockBukkit world for nothing.
     */
    private static final Location SOMEWHERE = new Location((World) null, 0, 64, 0);

    /** A player nobody reads, for the same reason. */
    private static final Player NOBODY = org.mockito.Mockito.mock(Player.class);

    @Test
    @DisplayName("the regions covering a point are every provider's, because a name is a name whoever gave it")
    void theRegionsAreTheUnion() {
        RegionService both = new CompositeRegionService(List.of(fake(true, true, "spawn"), fake(true, true, "town")));

        assertThat(both.regionsAt(SOMEWHERE)).containsExactlyInAnyOrder("spawn", "town");
    }

    @Test
    @DisplayName("one provider refusing a build refuses it, because two protections are two protections")
    void oneRefusalRefuses() {
        RegionService both = new CompositeRegionService(List.of(fake(true, true, "spawn"), fake(true, false, "town")));

        assertThat(both.canBuild(NOBODY, SOMEWHERE))
                .describedAs("honouring the more permissive one would let us do what a player could not")
                .isFalse();
        assertThat(both.canInteract(NOBODY, SOMEWHERE)).isFalse();
    }

    @Test
    @DisplayName("a provider that is not installed is not consulted, so it cannot refuse anything")
    void anabsentProviderRefusesNothing() {
        RegionService both = new CompositeRegionService(List.of(fake(true, true, "spawn"), fake(false, false, "town")));

        assertThat(both.canBuild(NOBODY, SOMEWHERE)).isTrue();
        assertThat(both.regionsAt(SOMEWHERE)).containsExactly("spawn");
    }

    @Test
    @DisplayName("land no provider claims is wilderness, which follows from the union being empty")
    void unclaimedLandIsWilderness() {
        assertThat(new CompositeRegionService(List.of(fake(true, true))).isWilderness(SOMEWHERE))
                .isTrue();
        assertThat(new CompositeRegionService(List.of(fake(true, true, "town"))).isWilderness(SOMEWHERE))
                .isFalse();
    }

    @Test
    @DisplayName("the composite names its members, because it is not a plugin and has no one name")
    void thecompositeNamesItsMembers() {
        RegionService both = new CompositeRegionService(List.of(fake(true, true, "spawn"), fake(true, true, "town")));

        assertThat(both.pluginName()).isEqualTo("fake+fake");
        assertThat(new CompositeRegionService(List.of()).pluginName()).isEqualTo("none");
    }

    @Test
    @DisplayName("every present provider is folded, and a server with none of them still reads as none")
    void thehooksFoldEveryPresentProvider() {
        RegionHooks hooks =
                new RegionHooks().register(fake(true, true, "spawn")).register(fake(true, true, "town"));

        assertThat(hooks.everyProvider().orElseThrow().regionsAt(SOMEWHERE)).containsExactlyInAnyOrder("spawn", "town");
        assertThat(new RegionHooks().register(fake(false, true, "town")).everyProvider())
                .isEmpty();
    }

    private static RegionService fake(boolean available, boolean allows, String... regions) {
        return new RegionService() {

            @Override
            public String pluginName() {
                return "fake";
            }

            @Override
            public boolean isAvailable() {
                return available;
            }

            @Override
            public boolean canBuild(Player player, Location location) {
                return allows;
            }

            @Override
            public boolean canInteract(Player player, Location location) {
                return allows;
            }

            @Override
            public Set<String> regionsAt(Location location) {
                return Set.of(regions);
            }

            @Override
            public boolean isWilderness(Location location) {
                return regions.length == 0;
            }
        };
    }
}
