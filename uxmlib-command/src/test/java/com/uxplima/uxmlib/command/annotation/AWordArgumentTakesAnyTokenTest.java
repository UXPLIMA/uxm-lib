package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Args;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A one-word {@code String} argument takes everything up to the next space.
 *
 * <p>It was Brigadier's {@code word()}, which stops at any character outside {@code 0-9 A-Z a-z _ - . +}. uxm-plots
 * found its own plot ids, written {@code creative:-1;0}, refused before the command ran. The same rule refused a
 * namespaced key such as {@code minecraft:stone}, a colour such as {@code #ff0000}, an IPv6 address, and every Turkish
 * letter, so a Turkish player could not name a home {@code köy}.
 */
class AWordArgumentTakesAnyTokenTest {

    @Command(name = "tok")
    static class TokenCommand {
        final List<String> seen = new ArrayList<>();

        @Subcommand("one")
        void one(Sender sender, @Arg("id") String id) {
            seen.add(id);
        }

        @Subcommand("two")
        void two(Sender sender, @Arg("first") String first, @Arg("second") String second) {
            seen.add(first);
            seen.add(second);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"creative:-1;0", "minecraft:stone", "#ff0000", "::1", "köy", "İstanbul", "a/b", "x=y"})
    @DisplayName("an annotated word argument reads the token exactly as typed")
    void anAnnotatedWordReadsTheTokenAsTyped(String typed) throws Exception {
        TokenCommand handler = new TokenCommand();

        dispatcher(AnnotatedCommands.buildNode(handler)).execute("tok one " + typed, source());

        assertThat(handler.seen).containsExactly(typed);
    }

    @Test
    @DisplayName("a word still ends at a space, so the next argument reads its own")
    void aWordEndsAtASpace() throws Exception {
        TokenCommand handler = new TokenCommand();

        dispatcher(AnnotatedCommands.buildNode(handler)).execute("tok two a:b ç;d", source());

        assertThat(handler.seen).containsExactly("a:b", "ç;d");
    }

    @Test
    @DisplayName("a token that opens with a quote is read as a quoted phrase, as string() read it")
    void aQuotedTokenIsAPhrase() throws Exception {
        TokenCommand handler = new TokenCommand();

        dispatcher(AnnotatedCommands.buildNode(handler))
                .execute("tok two \"https://i.imgur.com/a.png\" 'Madenci Ömer'", source());

        assertThat(handler.seen).containsExactly("https://i.imgur.com/a.png", "Madenci Ömer");
    }

    @Test
    @DisplayName("the client is still told the argument is one word")
    void theClientStillSeesAWord() {
        CommandNode<CommandSourceStack> one =
                AnnotatedCommands.buildNode(new TokenCommand()).getChild("one");
        assertThat(one).isNotNull();
        CommandNode<CommandSourceStack> id =
                java.util.Objects.requireNonNull(one).getChild("id");

        assertThat(id).isInstanceOf(ArgumentCommandNode.class);
        Object type = ((ArgumentCommandNode<?, ?>) java.util.Objects.requireNonNull(id)).getType();
        assertThat(type).isInstanceOf(CustomArgumentType.class);
        assertThat(((CustomArgumentType<?, ?>) type).getNativeType())
                .isInstanceOfSatisfying(
                        StringArgumentType.class,
                        word -> assertThat(word.getType()).isEqualTo(StringArgumentType.StringType.SINGLE_WORD));
    }

    @Test
    @DisplayName("a command built by hand takes the same token through Args")
    void aHandBuiltCommandTakesTheSameToken() throws Exception {
        List<String> seen = new ArrayList<>();
        LiteralCommandNode<CommandSourceStack> node = Commands.literal("raw")
                .then(Commands.argument("key", Args.token()).executes(ctx -> {
                    seen.add(Args.string(ctx, "key"));
                    return 1;
                }))
                .build();

        dispatcher(node).execute("raw minecraft:diamond_sword", source());

        assertThat(seen).containsExactly("minecraft:diamond_sword");
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher(LiteralCommandNode<CommandSourceStack> node) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(node);
        return dispatcher;
    }

    private static CommandSourceStack source() {
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(mock(CommandSender.class));
        return source;
    }
}
