package com.uxplima.uxmlib.hud.nametag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.jspecify.annotations.Nullable;

/**
 * What every contribution adds up to: the one prefix, one suffix and one colour a player's name can carry.
 *
 * <p>Prefixes and suffixes compose: each contribution's part is appended in priority order, joined by the
 * separator, because a name has room for all of them. A colour does not: the name is drawn in a single
 * colour, so exactly one contribution can own it.
 *
 * <p>Who that owner is, is a {@link ColorOwner}, because it is not a thing this library can know. Two
 * plugins claiming one name is a question about the server they run on: an estate that sells a rank colour
 * wants the rank to win, and an estate whose glow is a toy wants the toy to win. {@link ColorOwner#newest()}
 * is the shipped answer and the reason is a player: somebody who just switched a glow on and saw nothing
 * happen reads it as a broken feature rather than as a precedence rule. {@link ColorOwner#byPriority()} is
 * the other common answer, and a consumer may write its own.
 *
 * <p>Every plugin that asked is recorded in {@link #colorSources} and the winner in {@link #colorOwner}, so
 * a registry can say out loud who wanted the name and who has it. A clash nobody reports is what made the
 * original collision so hard to find.
 *
 * <p>Layout ties are broken by the contributing plugin's name, so two plugins that share a priority compose
 * in the same order on every restart rather than in whatever order a map happened to iterate.
 *
 * <p>Pure: no server, no scoreboard. {@link NametagSink} is what turns one of these into a name a player
 * actually wears.
 *
 * @param prefix everything before the player's name, empty when nobody contributed one
 * @param suffix everything after it, empty when nobody contributed one
 * @param color the owning contribution's colour, or {@code null} when no contribution supplied one
 * @param colorOwner the plugin whose colour the name wears, or {@code null} when nobody supplied one
 * @param colorSources every plugin that supplied a colour, oldest contribution first
 */
public record ComposedNametag(
        Component prefix,
        Component suffix,
        @Nullable NamedTextColor color,
        @Nullable String colorOwner,
        List<String> colorSources) {

    /**
     * Which of the plugins that asked for a colour wears it.
     *
     * <p>A name carries one colour, so a rule has to settle it, and the rule belongs to the server rather
     * than to this library: the two shipped answers are here, and a consumer that wants another writes one.
     */
    @FunctionalInterface
    public interface ColorOwner {

        /**
         * The contribution whose colour the name takes.
         *
         * @param claims the contributions that supplied a colour, oldest first; never empty
         * @return one of {@code claims}, or {@code null} to leave the name uncoloured
         */
        @Nullable NametagContribution pick(List<NametagContribution> claims);

        /**
         * The plugin that asked last wears the colour: an effect a player switches on takes the name the
         * moment they switch it on. This is what a registry uses until a consumer says otherwise.
         */
        static ColorOwner newest() {
            return claims -> claims.get(claims.size() - 1);
        }

        /**
         * The smallest {@link NametagContribution#priority()} wears the colour, and a tie goes to the
         * plugin that asked last. It is the answer for an estate where a colour is earned rather than
         * switched on: put the rank plugin first in the file and nothing can take the name off it.
         */
        static ColorOwner byPriority() {
            return claims -> claims.stream()
                    .reduce((left, right) -> right.priority() <= left.priority() ? right : left)
                    .orElse(null);
        }
    }

    /** Contributions compose by position, then by plugin name so the result survives a restart unchanged. */
    private static final Comparator<NametagContribution> ORDER =
            Comparator.comparingInt(NametagContribution::priority).thenComparing(NametagContribution::plugin);

    public ComposedNametag {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(suffix, "suffix");
        colorSources = List.copyOf(Objects.requireNonNull(colorSources, "colorSources"));
    }

    /** Compose {@code arrivals} with the shipped colour rule, {@link ColorOwner#newest()}. */
    public static ComposedNametag compose(List<NametagContribution> arrivals, String separator) {
        return compose(arrivals, separator, ColorOwner.newest());
    }

    /**
     * Compose {@code arrivals}, joining the parts of each half with {@code separator}.
     *
     * @param arrivals the contributions in the order they were made, oldest first; the layout is sorted by
     *     priority here and does not depend on it
     * @param owner which of the contributions that supplied a colour wears it
     */
    public static ComposedNametag compose(List<NametagContribution> arrivals, String separator, ColorOwner owner) {
        Objects.requireNonNull(arrivals, "arrivals");
        Objects.requireNonNull(separator, "separator");
        Objects.requireNonNull(owner, "owner");
        List<NametagContribution> ordered = new ArrayList<>(arrivals);
        ordered.sort(ORDER);
        List<NametagContribution> claims = arrivals.stream()
                .filter(contribution -> contribution.color() != null)
                .toList();
        NametagContribution winner = claims.isEmpty() ? null : owner.pick(claims);
        return new ComposedNametag(
                join(ordered, NametagContribution::prefix, separator),
                join(ordered, NametagContribution::suffix, separator),
                winner == null ? null : winner.color(),
                winner == null ? null : winner.plugin(),
                claims.stream().map(NametagContribution::plugin).toList());
    }

    /** Whether more than one contribution wanted the name's colour, so only one of them could have it. */
    public boolean hasColorClash() {
        return colorSources.size() > 1;
    }

    private static Component join(
            List<NametagContribution> ordered,
            Function<NametagContribution, @Nullable Component> part,
            String separator) {
        Component joined = Component.empty();
        boolean first = true;
        for (NametagContribution contribution : ordered) {
            Component piece = part.apply(contribution);
            if (piece == null) {
                continue;
            }
            joined = first ? piece : joined.append(Component.text(separator)).append(piece);
            first = false;
        }
        return joined;
    }
}
