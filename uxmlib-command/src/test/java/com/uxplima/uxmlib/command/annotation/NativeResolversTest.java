package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import org.junit.jupiter.api.Test;

/**
 * Verifies the native Bukkit arg types beyond player/world/material build argument nodes off their Paper
 * argument types: a {@link org.bukkit.Location}, an {@link org.bukkit.OfflinePlayer}, and a
 * {@link org.bukkit.Sound}. The node shape (and that registration accepts the type at all) is what proves the
 * resolvers are wired; their parse from a live source is exercised by Brigadier at runtime.
 *
 * <p>One type per test, and one subcommand per command class, because a node is built for a whole class in a
 * single call. MockBukkit answers an argument type it has not implemented with an exception that extends
 * JUnit's {@code TestAbortedException}, so one unsupported type used to end this test before the other two
 * were asserted and the run reported the whole thing as skipped rather than failed. Which type it died on was
 * not even fixed: the renderer walks declared methods in the order reflection hands them over.
 *
 * <p>The types the mock cannot build are named here rather than left to abort, so the missing coverage is
 * something a reader can see. {@code verifyNoAbortedTests} keeps it that way, and
 * {@link MockBukkitArgumentGapsTest} fails when one of those reasons stops being true.
 */
class NativeResolversTest {

    @Command(name = "loc")
    static class LocationCommand {
        @Subcommand("tp")
        void tp(Sender sender, @Arg("where") org.bukkit.Location where) {}
    }

    @Command(name = "off")
    static class OfflinePlayerCommand {
        @Subcommand("seen")
        void seen(Sender sender, @Arg("who") org.bukkit.OfflinePlayer who) {}
    }

    @Command(name = "snd")
    static class SoundCommand {
        @Subcommand("play")
        void play(Sender sender, @Arg("sound") org.bukkit.Sound sound) {}
    }

    @Test
    void aLocationIsResolvedByANativeArgument() {
        assertThatTypeIsNative(org.bukkit.Location.class);
    }

    @Test
    void anOfflinePlayerBuildsAnArgumentNode() {
        assertThat(arg(AnnotatedCommands.buildNode(new OfflinePlayerCommand()), "seen", "who"))
                .isNotNull();
    }

    @Test
    void aSoundIsResolvedByANativeArgument() {
        assertThatTypeIsNative(org.bukkit.Sound.class);
    }

    /**
     * Both used to build the whole node and were {@code @Disabled}, because MockBukkit implements neither
     * {@code ArgumentTypes.finePosition()} nor {@code ArgumentTypes.resource()}. A disabled test protects
     * nothing and reports as green, and the workspace rule is that no test is skipped, so the question is asked
     * in the one way that runs here: the type has a resolver, and the resolver says it is Paper-native.
     *
     * <p>What is not asked is what {@code argumentType} returns, because calling it is exactly what MockBukkit
     * cannot do. Brigadier calls it on a live server on every command that carries the type.
     */
    private static void assertThatTypeIsNative(Class<?> type) {
        ParamResolver<?> resolver = ParamResolvers.withDefaults().resolverFor(type);

        assertThat(resolver)
                .describedAs("%s must have a resolver", type.getSimpleName())
                .isNotNull();
        assertThat(java.util.Objects.requireNonNull(resolver).nativeArgument())
                .describedAs("%s is resolved by a Paper-native argument type", type.getSimpleName())
                .isTrue();
    }

    @SuppressWarnings("unchecked")
    private static @org.jspecify.annotations.Nullable ArgumentCommandNode<CommandSourceStack, ?> arg(
            LiteralCommandNode<CommandSourceStack> root, String literal, String argName) {
        CommandNode<CommandSourceStack> lit = root.getChild(literal);
        if (lit == null) {
            return null;
        }
        CommandNode<CommandSourceStack> a = lit.getChild(argName);
        return a instanceof ArgumentCommandNode ? (ArgumentCommandNode<CommandSourceStack, ?>) a : null;
    }
}
