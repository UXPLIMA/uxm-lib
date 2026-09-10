package com.uxplima.uxmlib.content;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.condition.OperandResolver;
import com.uxplima.uxmlib.hook.PlaceholderApi;
import org.jspecify.annotations.Nullable;

/**
 * What each side of a comparison an operator wrote actually means.
 *
 * <p>Every requirement line and every reward line goes through the condition engine, and the engine asks a
 * resolver to expand an operand before it compares it. A plugin that hands it the identity compares
 * {@code %vault_eco_balance%} as those twenty characters: the line never fails, it is simply never true,
 * and nothing is logged. This is the one resolver the estate uses, so that cannot happen in one plugin and
 * not in another.
 *
 * <p><strong>Static, and set once</strong>, for the reason {@link ContentNames} is: what a player's skill
 * level is belongs to the server rather than to a plugin, and several of the places that build a resolver
 * are static helpers no constructor reaches. Nothing is installed until something installs it, and until
 * then every operand resolves exactly as it did before this existed.
 */
public final class Operands {

    /**
     * The token an operator writes for a skill level.
     *
     * <p>It is mcMMO's own PlaceholderAPI spelling on purpose. An operator who runs PlaceholderAPI with the
     * mcMMO expansion could write this line before this class existed; what this adds is that it also works
     * without PlaceholderAPI, because the level is read off the plugin directly.
     */
    public static final String SKILL_PREFIX = "%mcmmo_level_";

    private static volatile SkillLevels skills = SkillLevels.NONE;

    private Operands() {}

    /**
     * Say where a skill level comes from.
     *
     * <p>Called once at enable by whichever plugin of ours starts first, and safe for all of them to call.
     * Calling it again replaces the seam, which is what a reload does.
     */
    public static void readingSkills(SkillLevels levels) {
        skills = Objects.requireNonNull(levels, "levels");
    }

    /** Forget it, which a shutdown does so a reload does not hold the old server's plugins. */
    public static void forgetEverything() {
        skills = SkillLevels.NONE;
    }

    /** Whether a skill plugin answered on this server, which a start line says once. */
    public static boolean active() {
        return skills.active();
    }

    /**
     * The resolver a plugin of ours reads every operator written line with.
     *
     * <p>A skill level first, because it is answered without PlaceholderAPI and a server that has both
     * should get the same number either way. Everything else goes to PlaceholderAPI, which returns the text
     * unchanged when it is not installed: that is a comparison against a literal, and it refuses, which is
     * the same answer the engine gives for a placeholder nobody answers.
     */
    public static OperandResolver standard() {
        return Operands::resolve;
    }

    /**
     * The same, in front of a resolver a plugin already has.
     *
     * <p>Chained rather than replacing, because a plugin that answers tokens of its own must not have to
     * choose between those and these.
     */
    public static OperandResolver resolving(OperandResolver next) {
        Objects.requireNonNull(next, "next");
        return (player, template) -> skillLevel(player, template).orElseGet(() -> next.resolve(player, template));
    }

    private static String resolve(@Nullable Player player, String template) {
        Objects.requireNonNull(template, "template");
        Optional<String> level = skillLevel(player, template);
        if (level.isPresent()) {
            return level.get();
        }
        return player == null ? template : PlaceholderApi.apply(player, template);
    }

    /** The level this token names, when it is one of these tokens and the plugin knows the player. */
    private static Optional<String> skillLevel(@Nullable Player player, String template) {
        if (player == null || !template.startsWith(SKILL_PREFIX) || !template.endsWith("%")) {
            return Optional.empty();
        }
        String skill = template.substring(SKILL_PREFIX.length(), template.length() - 1);
        if (skill.isBlank()) {
            return Optional.empty();
        }
        OptionalInt found = skills.levelOf(player.getUniqueId(), skill);
        return found.isPresent() ? Optional.of(Integer.toString(found.getAsInt())) : Optional.empty();
    }
}
