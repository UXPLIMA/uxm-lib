package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;
import org.junit.jupiter.api.Test;

class EnumResolverTest {

    enum Mode {
        SURVIVAL,
        CREATIVE
    }

    @SuppressWarnings("unchecked")
    private static CommandContext<CommandSourceStack> contextGiving(String argument, String value) {
        CommandContext<CommandSourceStack> context = mock(CommandContext.class);
        when(context.getArgument(argument, String.class)).thenReturn(value);
        return context;
    }

    @Test
    void resolvesAConstantCaseInsensitively() {
        EnumResolver resolver = new EnumResolver(Mode.values());
        assertThat(resolver.resolve(contextGiving("mode", "Survival"), "mode")).isEqualTo(Mode.SURVIVAL);
    }

    @Test
    void aRejectionNamesTheValuesInTheCaseCompletionOffers() {
        EnumResolver resolver = new EnumResolver(Mode.values());

        assertThatThrownBy(() -> resolver.resolve(contextGiving("mode", "nope"), "mode"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("survival, creative")
                .hasMessageContaining("nope");
        assertThat(resolver.suggestions()).containsExactlyElementsOf(java.util.List.of("survival", "creative"));
    }
}
