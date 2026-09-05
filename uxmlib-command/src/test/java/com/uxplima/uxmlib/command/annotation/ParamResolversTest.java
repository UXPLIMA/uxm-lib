package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Verifies the resolver registry: rich built-in types build a tree, enums and custom types resolve.
 *
 * <p>One type per test, and one subcommand per command class. A node is built for a whole class at once, and
 * MockBukkit answers an argument type it has not implemented with an exception JUnit reads as an abort, so a
 * type the mock cannot build used to take the assertions for the types it can down with it and the run
 * reported that as a skip. The mock implements two of its argument types, {@code player()} and
 * {@code players()}; the ones it does not are named below instead of aborting, and
 * {@link MockBukkitArgumentGapsTest} fails when one of those reasons stops being true.
 */
class ParamResolversTest {

    enum Mode {
        SURVIVAL,
        CREATIVE
    }

    @Command(name = "who")
    static class PlayerCommand {
        @Subcommand("tp")
        void tp(Sender sender, @Arg("target") org.bukkit.entity.Player target) {}
    }

    @Command(name = "where")
    static class WorldCommand {
        @Subcommand("world")
        void world(Sender sender, @Arg("w") org.bukkit.World world) {}
    }

    @Command(name = "how")
    static class ModeCommand {
        @Subcommand("mode")
        void mode(Sender sender, @Arg("mode") Mode mode) {}
    }

    @Command(name = "what")
    static class MaterialCommand {
        @Subcommand("give")
        void give(Sender sender, @Arg("item") org.bukkit.Material item) {}
    }

    @Command(name = "which")
    static class UuidCommand {
        @Subcommand("id")
        void id(Sender sender, @Arg("id") java.util.UUID id) {}
    }

    record Point(int x, int z) {}

    @Command(name = "custom")
    static class CustomCommand {
        @Subcommand("at")
        void at(Sender sender, @Arg("point") Point point) {}
    }

    @Test
    void aPlayerBuildsAnArgumentNode() {
        assertThat(child(AnnotatedCommands.buildNode(new PlayerCommand()), "tp", "target"))
                .isNotNull();
    }

    @Test
    @Disabled("MockBukkit does not implement ArgumentTypes.world()")
    void aWorldBuildsAnArgumentNode() {
        assertThat(child(AnnotatedCommands.buildNode(new WorldCommand()), "world", "w"))
                .isNotNull();
    }

    @Test
    void anEnumBuildsAnArgumentNode() {
        assertThat(child(AnnotatedCommands.buildNode(new ModeCommand()), "mode", "mode"))
                .isNotNull();
    }

    @Test
    @Disabled("MockBukkit does not implement ArgumentTypes.resource()")
    void aMaterialBuildsAnArgumentNode() {
        assertThat(child(AnnotatedCommands.buildNode(new MaterialCommand()), "give", "item"))
                .isNotNull();
    }

    @Test
    @Disabled("MockBukkit does not implement ArgumentTypes.uuid()")
    void aUuidBuildsAnArgumentNode() {
        assertThat(child(AnnotatedCommands.buildNode(new UuidCommand()), "id", "id"))
                .isNotNull();
    }

    @Test
    void aCustomResolverTeachesTheDslANewType() {
        ParamResolvers resolvers = ParamResolvers.withDefaults().register(Point.class, new ParamResolver<Point>() {
            @Override
            public com.mojang.brigadier.arguments.ArgumentType<?> argumentType(Arg arg) {
                return com.mojang.brigadier.arguments.StringArgumentType.word();
            }

            @Override
            public Point resolve(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String name) {
                return new Point(0, 0);
            }
        });

        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new CustomCommand(), resolvers);
        assertThat(child(node, "at", "point")).isNotNull();
    }

    @Test
    void rejectsATypeNoResolverHandles() {
        // Without the custom resolver, Point has no resolver and registration fails loudly.
        assertThatThrownBy(() -> AnnotatedCommands.buildNode(new CustomCommand()))
                .isInstanceOf(CommandParseException.class)
                .hasMessageContaining("resolver");
    }

    private static @org.jspecify.annotations.Nullable CommandNode<CommandSourceStack> child(
            LiteralCommandNode<CommandSourceStack> root, String literal, String arg) {
        CommandNode<CommandSourceStack> lit = root.getChild(literal);
        return lit == null ? null : lit.getChild(arg);
    }
}
