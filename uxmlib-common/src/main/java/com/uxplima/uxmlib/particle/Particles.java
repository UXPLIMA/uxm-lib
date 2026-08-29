package com.uxplima.uxmlib.particle;

import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Spawns a {@link ParticleOptions} value through Bukkit's {@code spawnParticle}. Because the options already
 * carry the correct {@link ParticleOptions#particle()} and {@link ParticleOptions#data()}, this facade only
 * has to forward them to the right overload: the type-safety work is done at the call site by choosing the
 * right record. A {@link World} call shows the particle to everyone in range; a {@link Player} call shows it
 * only to that player.
 */
public final class Particles {

    private Particles() {}

    /** Spawn {@code count} particles at {@code where}, visible to every player in range. */
    public static void spawn(World world, Location where, ParticleOptions options, int count) {
        spawn(world, where, options, count, 0d, 0d, 0d, 0d);
    }

    /**
     * Spawn {@code count} particles at {@code where} with a Gaussian {@code (offsetX, offsetY, offsetZ)}
     * spread and {@code speed}, visible to every player in range.
     *
     * @throws IllegalArgumentException if {@code count} is negative
     */
    public static void spawn(
            World world,
            Location where,
            ParticleOptions options,
            int count,
            double offsetX,
            double offsetY,
            double offsetZ,
            double speed) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(where, "where");
        Objects.requireNonNull(options, "options");
        requireCount(count);
        world.spawnParticle(options.particle(), where, count, offsetX, offsetY, offsetZ, speed, options.data());
    }

    /** Spawn {@code count} particles at {@code where} visible only to {@code player}. */
    public static void spawn(Player player, Location where, ParticleOptions options, int count) {
        spawn(player, where, options, count, 0d, 0d, 0d, 0d);
    }

    /**
     * Spawn {@code count} particles at {@code where} with a Gaussian spread and {@code speed}, visible only to
     * {@code player}.
     *
     * @throws IllegalArgumentException if {@code count} is negative
     */
    public static void spawn(
            Player player,
            Location where,
            ParticleOptions options,
            int count,
            double offsetX,
            double offsetY,
            double offsetZ,
            double speed) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(where, "where");
        Objects.requireNonNull(options, "options");
        requireCount(count);
        player.spawnParticle(options.particle(), where, count, offsetX, offsetY, offsetZ, speed, options.data());
    }

    private static void requireCount(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative: " + count);
        }
    }
}
