/**
 * Type-safe particle spawning. A sealed {@link com.uxplima.uxmlib.particle.ParticleOptions} hierarchy pairs
 * each particle with exactly the data it requires (dust colour and size, a block or item, or nothing), so the
 * compiler (not a runtime cast) enforces the right payload per particle. {@link
 * com.uxplima.uxmlib.particle.Particles} maps an options value to the matching {@code spawnParticle} overload.
 */
@NullMarked
package com.uxplima.uxmlib.particle;

import org.jspecify.annotations.NullMarked;
