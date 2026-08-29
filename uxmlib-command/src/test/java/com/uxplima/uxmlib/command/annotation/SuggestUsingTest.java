package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Objects;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import org.junit.jupiter.api.Test;

/**
 * Verifies the port-injectable suggestion seam: a {@link SuggestionSource} <em>instance</em> registered on a
 * {@link ParamResolvers} under a key, referenced from an {@code @Arg} via {@code @}{@link SuggestUsing},
 * drives that argument's tab-completion: the DI-friendly counterpart of {@code @SuggestWith} (which can only
 * reflect a no-arg provider class and so cannot carry a consumer's ports).
 */
class SuggestUsingTest {

    @Command(name = "named", help = false)
    static class NamedSuggestCommand {
        @Subcommand("pick")
        void pick(Sender sender, @Arg("fruit") @SuggestUsing("fruits") String fruit) {}
    }

    private static SuggestionSource fruitSource() {
        return (context, builder) -> builder.suggest("apple").suggest("banana").buildFuture();
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

    @Test
    void namedSuggestionInstanceIsWired() {
        ParamResolvers resolvers = ParamResolvers.withDefaults().suggestions("fruits", fruitSource());
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new NamedSuggestCommand(), resolvers);
        ArgumentCommandNode<CommandSourceStack, ?> fruit = arg(node, "pick", "fruit");
        assertThat(fruit).isNotNull();
        assertThat(Objects.requireNonNull(fruit).getCustomSuggestions()).isNotNull();
    }

    @Test
    void namedSuggestionInstanceProducesItsCompletions() throws Exception {
        ParamResolvers resolvers = ParamResolvers.withDefaults().suggestions("fruits", fruitSource());
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new NamedSuggestCommand(), resolvers);
        ArgumentCommandNode<CommandSourceStack, ?> fruit = Objects.requireNonNull(arg(node, "pick", "fruit"));
        Suggestions suggestions = Objects.requireNonNull(fruit.getCustomSuggestions())
                .getSuggestions(null, new SuggestionsBuilder("", 0))
                .get();
        assertThat(suggestions.getList().stream().map(Suggestion::getText)).contains("apple", "banana");
    }

    @Test
    void unknownSuggestionKeyFailsAtRegistration() {
        assertThatThrownBy(() -> AnnotatedCommands.buildNode(new NamedSuggestCommand(), ParamResolvers.withDefaults()))
                .isInstanceOf(CommandParseException.class);
    }
}
