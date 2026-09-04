package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Objects;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import org.junit.jupiter.api.Test;

/**
 * Covers the aliases of a branch. A root command has carried aliases from the start, so an operator could
 * rename {@code /shop} and keep the old word. A branch could not, which left a server that renames a
 * subcommand with no way to keep the word its players already know.
 *
 * <p>An alias names the last literal of the path only. {@code "admin reload"} with the alias {@code "rl"}
 * answers to {@code admin rl}, never to {@code rl} alone, because a branch keeps the place it was given in
 * the tree.
 */
class SubcommandAliasTest {

    @Command(name = "shop", help = false)
    static class ShopCommand {
        String said = "";

        @Subcommand(
                value = "sell",
                aliases = {"sat", "s"})
        void sell(Sender sender, @Arg("price") int price) {
            said = "sell " + price;
        }

        @Subcommand(value = "admin reload", aliases = "rl")
        void reload(Sender sender) {
            said = "reload";
        }

        @Subcommand(
                value = "open",
                aliases = {"", "  "})
        void open(Sender sender) {
            said = "open";
        }
    }

    @Test
    void abranchAnswersToEveryAliasTheAuthorGaveIt() {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new ShopCommand());

        assertThat(node.getChild("sell")).isNotNull();
        assertThat(node.getChild("sat")).isNotNull();
        assertThat(node.getChild("s")).isNotNull();
    }

    @Test
    void analiasNamesTheLastLiteralAndKeepsThePlaceOfTheBranch() {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new ShopCommand());
        CommandNode<CommandSourceStack> admin = Objects.requireNonNull(node.getChild("admin"));

        assertThat(admin.getChild("reload")).isNotNull();
        assertThat(admin.getChild("rl")).isNotNull();
        assertThat(node.getChild("rl")).isNull();
    }

    @Test
    void ablankAliasIsNotALiteral() {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new ShopCommand());

        assertThat(node.getChild("open")).isNotNull();
        assertThat(node.getChildren()).extracting(CommandNode::getName).doesNotContain("", "  ");
    }

    @Test
    void analiasRunsTheSameMethodWithTheSameArguments() throws Exception {
        ShopCommand handler = new ShopCommand();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(AnnotatedCommands.buildNode(handler));
        CommandSender sender = mock(CommandSender.class);
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(sender);

        dispatcher.execute("shop sat 40", source);

        assertThat(handler.said).isEqualTo("sell 40");
    }
}
