package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Locale;

import org.bukkit.command.CommandSender;
import org.bukkit.permissions.PermissionDefault;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.CommandDispatcher;
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
 * What a player who lacks the node is told, and what they are not.
 *
 * <p>A permission used to do one thing: hide the branch, through Brigadier's {@code requires}. That is
 * right for an operator's verb, because nobody should learn that {@code /uxmcrates admin editor} exists by
 * being refused it. It is wrong for a verb a player is meant to have: a server that sells a cosmetic rank
 * has players typing {@code /glow} and reading the server's own "unknown command", which reads as a broken
 * plugin rather than as a locked feature.
 *
 * <p>The descriptor already says which is which. A node declared {@code default: true} is one a player is
 * meant to hold, so its branch stays visible and the refusal is said out loud. Every other node keeps the
 * old behaviour and is not there at all. Nothing new is written anywhere for the library to know this: it
 * asks the server for the permission the plugin registered.
 */
class PermissionRefusalTest {

    private static final String PLAYER_NODE = "uxmtest.player.verb";

    private static final String ADMIN_NODE = "uxmtest.admin.verb";

    @Command(name = "t")
    static class TestCommand {

        @Permission(PLAYER_NODE)
        @Subcommand("mine")
        void mine(Sender sender) {}

        @Permission(ADMIN_NODE)
        @Subcommand("theirs")
        void theirs(Sender sender) {}
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
    @DisplayName("a verb a player is meant to have stays visible when they lack it")
    void aplayerVerbStaysVisible() {
        assertThat(canSee("mine"))
                .describedAs("a granted-by-default node hidden from the player who lost it reads as a"
                        + " broken plugin, not as a locked feature")
                .isTrue();
    }

    @Test
    @DisplayName("an operator's verb is not there at all")
    void anoperatorVerbIsHidden() {
        assertThat(canSee("theirs"))
                .describedAs("nobody should learn an admin verb exists by being refused it")
                .isFalse();
    }

    @Test
    @DisplayName("running a verb you are meant to have and do not says so")
    void runningItSaysSo() throws Exception {
        assertThat(dispatch("t mine")).isEqualTo("no permission [en]");
    }

    /** Whether a sender who holds nothing sees this branch at all. */
    private static boolean canSee(String branch) {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new TestCommand(), resolvers());
        CommandNode<CommandSourceStack> child = node.getChild(branch);
        assertThat(child).isNotNull();
        return java.util.Objects.requireNonNull(child).getRequirement().test(source());
    }

    private static String dispatch(String input) throws Exception {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new TestCommand(), resolvers());
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(node);
        CommandSourceStack source = source();

        dispatcher.execute(input, source);

        org.mockito.ArgumentCaptor<Component> reply = org.mockito.ArgumentCaptor.forClass(Component.class);
        verify(source.getSender()).sendMessage(reply.capture());
        return PlainTextComponentSerializer.plainText().serialize(reply.getValue());
    }

    /** A sender who holds no node at all, which is every player on a fresh server. */
    private static CommandSourceStack source() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(sender);
        return source;
    }

    private static ParamResolvers resolvers() {
        return ParamResolvers.withDefaults().messages(new CommandMessages() {
            @Override
            public Component noPermission(Locale locale) {
                return Component.text("no permission [" + locale.getLanguage() + "]");
            }
        });
    }
}
