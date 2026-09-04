package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Flag;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import com.uxplima.uxmlib.command.annotation.annotations.Switch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A flag is written where the command may end, and an optional argument is a place the command may end.
 *
 * <p>The tree was one chain: every positional argument stood between the literal and the flags, so a caller
 * had to write each optional one before a flag could be reached. {@code /shop sell 1 --retail} then asked
 * the number parser to read {@code --retail} and answered "Invalid integer", which is the server refusing a
 * line the plugin never saw. An optional argument that must be written is not optional.
 */
class FlagsAfterOptionalTest {

    @Command(name = "shop", help = false)
    static class ShopCommand {
        String price = "";
        int amount = -1;
        boolean retail;
        String time = "";

        @Subcommand("sell")
        void sell(
                Sender sender,
                @Arg(value = "price", optional = true) String price,
                @Arg(value = "amount", optional = true) int amount,
                @Flag(value = "time", shorthand = 't') String time,
                @Switch(value = "retail", shorthand = 'r') boolean retail) {
            this.price = price;
            this.amount = amount;
            this.time = time;
            this.retail = retail;
        }
    }

    private ShopCommand handler;
    private CommandDispatcher<CommandSourceStack> dispatcher;
    private CommandSourceStack source;

    @BeforeEach
    void build() {
        handler = new ShopCommand();
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(handler);
        dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(node);
        CommandSender sender = mock(CommandSender.class);
        source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(sender);
    }

    @Test
    @DisplayName("a switch is read with the last optional argument left out")
    void aswitchFollowsOneOptionalArgument() throws Exception {
        dispatcher.execute("shop sell 1 --retail", source);

        assertThat(handler.price).isEqualTo("1");
        assertThat(handler.retail).isTrue();
    }

    @Test
    @DisplayName("a flag with a value is read with every optional argument left out")
    void aflagFollowsEveryOptionalArgument() throws Exception {
        dispatcher.execute("shop sell --time 6h", source);

        assertThat(handler.time).isEqualTo("6h");
        assertThat(handler.price).isEmpty();
    }

    @Test
    @DisplayName("an argument that is written is still read as an argument and not as a flag")
    void apositionalIsStillPositional() throws Exception {
        dispatcher.execute("shop sell 1 64 --retail", source);

        assertThat(handler.price).isEqualTo("1");
        assertThat(handler.amount).isEqualTo(64);
        assertThat(handler.retail).isTrue();
    }

    @Test
    @DisplayName("the shorthand of a switch is read in the same place")
    void ashorthandFollowsAnOptionalArgument() throws Exception {
        dispatcher.execute("shop sell 1 -r", source);

        assertThat(handler.retail).isTrue();
    }

    @Test
    @DisplayName("the command still runs with no argument and no flag at all")
    void thebareBranchStillRuns() throws Exception {
        dispatcher.execute("shop sell", source);

        assertThat(handler.price).isEmpty();
        assertThat(handler.retail).isFalse();
    }
}
