package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Objects;
import java.util.Set;

import org.bukkit.command.CommandSender;
import org.bukkit.permissions.PermissionDefault;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * A word two branches share belongs to both of them.
 *
 * <p>{@code lang}, {@code lang set} and an operator's {@code lang set-player} all start with {@code lang}.
 * Brigadier keeps one node per word and, when two branches bring the same word, keeps the requirement of the
 * one that came first. The library put a multi-word branch's permission on its first word and attaches the
 * longest path first, so the operator's {@code lang set-player} decided who could see {@code lang} at all: on
 * 2026-10-12 uxmQuests' guard caught every player losing the whole language branch behind the operator's node. A shared word now opens to anyone who may use
 * any branch below it, and each branch's own last word carries its own node. The executor still refuses a
 * sender who lacks the node, so opening a shared word lets nobody run what they may not.
 */
class SharedWordPermissionTest {

    private static final String PLAYER_NODE = "uxmtest.language";

    private static final String ADMIN_NODE = "uxmtest.admin.language";

    /** The operator's path is the longest, so the library attaches it first. */
    @Command(name = "t")
    static class OperatorLongest {

        @Permission(ADMIN_NODE)
        @Subcommand("lang set-player")
        void setPlayer(Sender sender) {}

        @Permission(PLAYER_NODE)
        @Subcommand("lang")
        void lang(Sender sender) {}

        @Permission(PLAYER_NODE)
        @Subcommand("lang set")
        void set(Sender sender) {}
    }

    /** A player's path is the longest, so the operator's is attached after it. */
    @Command(name = "t")
    static class PlayerLongest {

        @Permission(PLAYER_NODE)
        @Subcommand("lang choose")
        void choose(Sender sender) {}

        @Permission(ADMIN_NODE)
        @Subcommand("lang set")
        void set(Sender sender) {}
    }

    @Command(name = "t")
    static class OperatorsOnly {

        @Permission(ADMIN_NODE)
        @Subcommand("key give")
        void give(Sender sender) {}

        @Permission(ADMIN_NODE)
        @Subcommand("key take")
        void take(Sender sender) {}
    }

    /** The bare command is the operator's, and a player's branch lives under it. */
    @Command(name = "t")
    static class OperatorsRoot {

        @Permission(ADMIN_NODE)
        @Subcommand("")
        void root(Sender sender) {}

        @Permission(PLAYER_NODE)
        @Subcommand("lang set")
        void set(Sender sender) {}

        @Permission(ADMIN_NODE)
        @Subcommand("reload")
        void reload(Sender sender) {}
    }

    @Command(name = "t")
    static class OperatorsOnlyRoot {

        @Permission(ADMIN_NODE)
        @Subcommand("")
        void root(Sender sender) {}

        @Permission(ADMIN_NODE)
        @Subcommand("reload")
        void reload(Sender sender) {}
    }

    @BeforeEach
    void start() {
        MockBukkit.mock();
        MockBukkit.getMock()
                .getPluginManager()
                .addPermission(new org.bukkit.permissions.Permission(PLAYER_NODE, PermissionDefault.TRUE));
        MockBukkit.getMock()
                .getPluginManager()
                .addPermission(new org.bukkit.permissions.Permission(ADMIN_NODE, PermissionDefault.OP));
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a player keeps the shared word whichever branch was read first")
    void thePlayerKeepsTheSharedWord() {
        CommandNode<CommandSourceStack> operatorLongest = child(build(new OperatorLongest()), "lang");
        CommandNode<CommandSourceStack> playerLongest = child(build(new PlayerLongest()), "lang");

        assertThat(operatorLongest.canUse(holding(PLAYER_NODE))).isTrue();
        assertThat(child(operatorLongest, "set").canUse(holding(PLAYER_NODE))).isTrue();
        assertThat(playerLongest.canUse(holding(PLAYER_NODE))).isTrue();
        assertThat(child(playerLongest, "choose").canUse(holding(PLAYER_NODE))).isTrue();
    }

    @Test
    @DisplayName("the operator's word under it stays hidden from a player")
    void theOperatorsWordStaysHidden() {
        CommandNode<CommandSourceStack> operatorLongest = child(build(new OperatorLongest()), "lang");
        CommandNode<CommandSourceStack> playerLongest = child(build(new PlayerLongest()), "lang");

        assertThat(child(operatorLongest, "set-player").canUse(holding(PLAYER_NODE)))
                .isFalse();
        assertThat(child(operatorLongest, "set-player").canUse(holding(PLAYER_NODE, ADMIN_NODE)))
                .isTrue();
        assertThat(child(playerLongest, "set").canUse(holding(PLAYER_NODE))).isFalse();
    }

    @Test
    @DisplayName("a word only operators' branches pass through stays hidden from a player")
    void anOperatorsOnlyWordStaysHidden() {
        CommandNode<CommandSourceStack> key = child(build(new OperatorsOnly()), "key");

        assertThat(key.canUse(holding())).isFalse();
        assertThat(key.canUse(holding(ADMIN_NODE))).isTrue();
    }

    /**
     * Found live on uxmConnect: {@code /blysis} alone is the operator's, and the root word took the operator's
     * node, so no player could reach {@code /blysis lang set} although every player holds its node.
     */
    @Test
    @DisplayName("the command's own word is the root branch's and every branch's below it")
    void thePlayerKeepsTheRootWord() {
        LiteralCommandNode<CommandSourceStack> root = build(new OperatorsRoot());

        assertThat(root.canUse(holding(PLAYER_NODE))).isTrue();
        assertThat(child(child(root, "lang"), "set").canUse(holding(PLAYER_NODE)))
                .isTrue();
        assertThat(child(root, "reload").canUse(holding(PLAYER_NODE))).isFalse();
        assertThat(build(new OperatorsOnlyRoot()).canUse(holding(PLAYER_NODE))).isFalse();
        assertThat(build(new OperatorsOnlyRoot()).canUse(holding(ADMIN_NODE))).isTrue();
    }

    private static LiteralCommandNode<CommandSourceStack> build(Object handler) {
        return AnnotatedCommands.buildNode(handler, ParamResolvers.withDefaults());
    }

    private static CommandNode<CommandSourceStack> child(CommandNode<CommandSourceStack> parent, String name) {
        return Objects.requireNonNull(parent.getChild(name), name);
    }

    /** A sender who holds exactly these nodes. */
    private static CommandSourceStack holding(String... nodes) {
        Set<String> held = Set.of(nodes);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(call -> held.contains(call.<String>getArgument(0)));
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(sender);
        return source;
    }
}
