package com.uxplima.uxmlib.command.annotation;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmlib.text.Text;
import org.jspecify.annotations.Nullable;

/**
 * A pre-execute gate. Before a {@code @}{@link com.uxplima.uxmlib.command.annotation.annotations.Subcommand}
 * method's arguments are bound and the handler is invoked, every registered condition is tested against the
 * dispatch; a condition vetoes execution by throwing a {@link CommandConditionException} carrying the
 * sender-facing reason. Conditions generalise the kinds of checks Brigadier's {@code requires} cannot
 * express (it only hides nodes by permission): "player only", "not on cooldown", "in the right world". A
 * {@code @}{@link com.uxplima.uxmlib.command.annotation.annotations.Permission} stays a Brigadier
 * {@code requires} so it also hides the node; conditions are for run-time gates that should explain
 * themselves. Register one on a {@link ParamResolvers} registry with
 * {@link ParamResolvers#condition(CommandCondition)}.
 */
@FunctionalInterface
public interface CommandCondition {

    /**
     * Test the dispatch in {@code context}. Return normally to allow execution; throw a
     * {@link CommandConditionException} to veto it with a message shown to the sender.
     */
    void test(CommandContext<CommandSourceStack> context);

    /**
     * Thrown by a {@link CommandCondition} to veto execution, carrying what the sender should be told
     * instead of the handler running.
     *
     * <p>Two shapes, because a condition may know more than a string. Given plain text the library renders it
     * red, exactly as a rejected argument is rendered. Given a {@link Component} it sends that untouched,
     * which is the only way a translated line — one already rendered from a catalogue, with its own colours
     * and MiniMessage already parsed — reaches the sender instead of arriving as raw tags.
     */
    final class CommandConditionException extends RuntimeException {

        private final String reason;
        private final transient @Nullable Component rendered;

        /** Veto with plain text, shown to the sender in red. */
        public CommandConditionException(String reason) {
            super(java.util.Objects.requireNonNull(reason, "reason"));
            this.reason = reason;
            this.rendered = null;
        }

        /** Veto with an already-rendered component, sent to the sender as-is. */
        public CommandConditionException(Component reason) {
            super(Text.plain(java.util.Objects.requireNonNull(reason, "reason")));
            this.reason = Text.plain(reason);
            this.rendered = reason;
        }

        /** The sender-facing reason as plain text; never {@code null}. */
        public String reason() {
            return reason;
        }

        /**
         * The component to send: the one the condition supplied, or {@link #reason()} in red when it gave
         * only text. This is what the executor writes, so a condition never has to know which it built.
         */
        public Component message() {
            return rendered != null ? rendered : Component.text(reason, NamedTextColor.RED);
        }
    }
}
