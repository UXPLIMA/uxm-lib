package com.uxplima.uxmlib.hud;

import java.time.Duration;
import java.util.Objects;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

/**
 * Title and subtitle delivery over Adventure. A single {@link #show} call renders a title with sensible
 * default fade/stay timing; the {@code times} overload lets a caller tune the animation. Vanilla titles
 * are transient, so there is no per-player state to keep here: this is a stateless helper.
 *
 * <p>The defaults (half-second fade in, three-second hold, half-second fade out) match what most plugins
 * want for a "pop a notice" title and mirror Adventure's own {@link Title#DEFAULT_TIMES}.
 */
public final class Titles {

    private static final Duration DEFAULT_FADE_IN = Duration.ofMillis(500L);
    private static final Duration DEFAULT_STAY = Duration.ofSeconds(3L);
    private static final Duration DEFAULT_FADE_OUT = Duration.ofMillis(500L);

    /** Show {@code title} over {@code subtitle} with the default fade/stay timing. */
    public void show(Player player, Component title, Component subtitle) {
        show(player, title, subtitle, DEFAULT_FADE_IN, DEFAULT_STAY, DEFAULT_FADE_OUT);
    }

    /** Show {@code title} over {@code subtitle} with caller-supplied fade-in, stay and fade-out durations. */
    public void show(
            Player player, Component title, Component subtitle, Duration fadeIn, Duration stay, Duration fadeOut) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(subtitle, "subtitle");
        Title.Times times = times(fadeIn, stay, fadeOut);
        player.showTitle(Title.title(title, subtitle, times));
    }

    /** Clear the current title without its fade-out, leaving the screen ready for a fresh one. */
    public void clear(Player player) {
        Objects.requireNonNull(player, "player");
        player.clearTitle();
    }

    /** Reset title state to vanilla defaults (timing and any queued title). */
    public void reset(Player player) {
        Objects.requireNonNull(player, "player");
        player.resetTitle();
    }

    private static Title.Times times(Duration fadeIn, Duration stay, Duration fadeOut) {
        Objects.requireNonNull(fadeIn, "fadeIn");
        Objects.requireNonNull(stay, "stay");
        Objects.requireNonNull(fadeOut, "fadeOut");
        requireNonNegative(fadeIn, "fadeIn");
        requireNonNegative(stay, "stay");
        requireNonNegative(fadeOut, "fadeOut");
        return Title.Times.times(fadeIn, stay, fadeOut);
    }

    private static void requireNonNegative(Duration duration, String name) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
