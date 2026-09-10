package com.uxplima.uxmlib.hook.region;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Selects the region provider to use among the candidates registered with it. A caller adds the adapters it
 * supports in priority order; {@link #active()} returns the first whose backing plugin is present (its
 * {@link RegionService#isAvailable()} is {@code true}), so a server with WorldGuard answers through
 * WorldGuard, one with only Towny through Towny, and one with neither degrades to empty. An instance, not
 * static state, so each plugin owns its own selection.
 */
public final class RegionHooks {

    private final List<RegionService> candidates = new ArrayList<>();

    /** Register {@code service} as a candidate provider; earlier registrations win. Returns this. */
    public RegionHooks register(RegionService service) {
        candidates.add(Objects.requireNonNull(service, "service"));
        return this;
    }

    /** The first present provider in registration order, or empty when none of the candidates is available. */
    public Optional<RegionService> active() {
        for (RegionService service : candidates) {
            if (service.isAvailable()) {
                return Optional.of(service);
            }
        }
        return Optional.empty();
    }

    /**
     * Every present provider, folded into one.
     *
     * <p>{@link #active()} answers with the first and nothing else, which is correct for "may this player
     * build here" and wrong for everything else. A server running WorldGuard for its spawn and Towny for
     * its towns has two region models, and a caller that reads a region name has to see both or a town is
     * invisible to it.
     *
     * <p>Empty when no provider is present, so a caller keeps the same "nothing is installed" path.
     */
    public Optional<RegionService> everyProvider() {
        List<RegionService> present = new ArrayList<>();
        for (RegionService service : candidates) {
            if (service.isAvailable()) {
                present.add(service);
            }
        }
        return present.isEmpty() ? Optional.empty() : Optional.of(new CompositeRegionService(present));
    }

    /** Whether any registered provider is currently present. */
    public boolean hasProvider() {
        return active().isPresent();
    }
}
