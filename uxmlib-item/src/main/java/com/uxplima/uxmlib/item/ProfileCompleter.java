package com.uxplima.uxmlib.item;

import java.util.Optional;

/**
 * The seam {@link SkullResolver} calls to turn a name or UUID into a head texture. It is invoked on the
 * library scheduler's async pool (never the main thread), because the default implementation performs a
 * blocking Mojang lookup via Paper's {@code PlayerProfile.complete()}.
 *
 * <p>Splitting this out keeps the live network call behind an interface so tests drive the resolver with a
 * fake completer and the cache/rate-limit behaviour is exercised without any I/O.
 */
@FunctionalInterface
public interface ProfileCompleter {

    /**
     * Resolve {@code key} (a player name or UUID string) to its head texture, or an empty {@link Optional}
     * when the profile has no skin or cannot be found. Called off the main thread; may block.
     */
    Optional<SkullData.ByTexture> complete(String key);
}
