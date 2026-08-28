package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import com.uxplima.uxmlib.text.message.LocaleSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * End-to-end proof that a registry's {@link CommandMessages} reaches every line the command layer says on
 * its own behalf. Commands are dispatched through Brigadier's own dispatcher over the built node, so no live
 * server is needed, and the reply the sender receives is captured and compared against the wording the
 * registry was given — not against the library's English.
 */
class CommandMessagesDispatchTest {

    private static final Locale TR = Locale.forLanguageTag("tr");

    enum Mode {
        SURVIVAL,
        CREATIVE
    }

    @Command(name = "game", description = "Oyun komutları")
    static class GameCommand {
        @Subcommand(value = "mode", description = "Modu değiştir")
        void mode(Sender sender, @Arg("mode") Mode mode) {}

        @PlayerOnly
        @Subcommand("home")
        void home(Sender sender) {}

        @Subcommand("self")
        void self(org.bukkit.entity.Player player) {}
    }

    /** A message layer that answers in Turkish, overriding only the lines this test dispatches. */
    static final class TurkishMessages implements CommandMessages {
        @Override
        public Component playerOnly(Locale locale) {
            return Component.text("Bu komutu yalnızca bir oyuncu kullanabilir [" + locale.getLanguage() + "]");
        }

        @Override
        public Component notOneOf(Locale locale, String argument, String input, List<String> allowed) {
            String list = String.join(", ", allowed);
            String tail = " ('" + input + "' değil) [" + locale.getLanguage() + "]";
            return Component.text("<" + argument + "> şunlardan biri olmalı: " + list + tail);
        }

        @Override
        public Component helpHeader(Locale locale, String command, int page, int pages) {
            return Component.text("/" + command + " yardım (" + page + "/" + pages + ")");
        }
    }

    private static ParamResolvers turkishResolvers() {
        return ParamResolvers.withDefaults().messages(new TurkishMessages()).locales(LocaleSource.ofDefault(TR));
    }

    private static String dispatch(String input) throws Exception {
        LiteralCommandNode<CommandSourceStack> node =
                AnnotatedCommands.buildNode(new GameCommand(), turkishResolvers());
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(node);

        CommandSender sender = mock(CommandSender.class);
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(sender);

        dispatcher.execute(input, source);

        ArgumentCaptor<Component> reply = ArgumentCaptor.forClass(Component.class);
        verify(sender).sendMessage(reply.capture());
        return PlainTextComponentSerializer.plainText().serialize(reply.getValue());
    }

    @Test
    void aRejectedEnumIsWordedByTheRegistrysMessagesWithTheWholeAllowedSet() throws Exception {
        assertThat(dispatch("game mode banana"))
                .isEqualTo("<mode> şunlardan biri olmalı: survival, creative ('banana' değil) [tr]");
    }

    @Test
    void aPlayerOnlyBranchRefusesTheConsoleInTheRegistrysWording() throws Exception {
        assertThat(dispatch("game home")).isEqualTo("Bu komutu yalnızca bir oyuncu kullanabilir [tr]");
    }

    @Test
    void anInjectedPlayerParameterRefusesTheConsoleTheSameWay() throws Exception {
        assertThat(dispatch("game self")).isEqualTo("Bu komutu yalnızca bir oyuncu kullanabilir [tr]");
    }

    @Test
    void theGeneratedHelpHeaderComesFromTheRegistryToo() throws Exception {
        assertThat(dispatch("game help")).startsWith("/game yardım (1/1)");
    }
}
