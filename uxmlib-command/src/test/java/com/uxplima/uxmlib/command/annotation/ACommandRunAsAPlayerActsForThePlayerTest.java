package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * A command run through {@code execute as <player> run ...} acts for that player.
 *
 * <p>Operators wire an NPC click or another plugin's button that way, and the console is the sender while the
 * player is the executor. Every player-only command read the sender, so each one answered the console "only a
 * player can run this command" and did nothing for the player. uxm-plots found it with {@code /plot shop}.
 */
class ACommandRunAsAPlayerActsForThePlayerTest {

    @Command(name = "shop")
    static class ShopCommand {
        final List<Player> opened = new ArrayList<>();
        final List<String> senders = new ArrayList<>();

        @Subcommand("open")
        @PlayerOnly
        void open(Player player) {
            opened.add(player);
        }

        @Subcommand("peek")
        void peek(Sender sender) {
            senders.add(sender.player().map(Player::getName).orElse("nobody"));
            sender.send(Component.text("peeked"));
        }
    }

    private ShopCommand shop;
    private CommandDispatcher<CommandSourceStack> dispatcher;
    private ConsoleCommandSender console;
    private Player player;

    @BeforeEach
    void setUp() {
        shop = new ShopCommand();
        dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(AnnotatedCommands.buildNode(shop));
        console = mock(ConsoleCommandSender.class);
        player = mock(Player.class);
        when(player.getName()).thenReturn("Alex");
        when(player.locale()).thenReturn(Locale.ENGLISH);
    }

    @Test
    @DisplayName("a player-only command run as a player opens for that player and refuses nobody")
    void aPlayerOnlyCommandActsForTheExecutor() throws Exception {
        dispatcher.execute("shop open", source(console, player));

        assertThat(shop.opened).containsExactly(player);
        verify(console, never()).sendMessage(org.mockito.ArgumentMatchers.any(Component.class));
    }

    @Test
    @DisplayName("the handler's sender is the player it runs as, and its reply reaches that player")
    void theSenderIsThePlayerItRunsAs() throws Exception {
        dispatcher.execute("shop peek", source(console, player));

        assertThat(shop.senders).containsExactly("Alex");
        verify(player).sendMessage(Component.text("peeked"));
    }

    @Test
    @DisplayName("run as a mob, or from the console alone, a player-only command still refuses")
    void aNonPlayerExecutorIsStillRefused() throws Exception {
        dispatcher.execute("shop open", source(console, mock(Zombie.class)));
        dispatcher.execute("shop open", source(console, null));

        assertThat(shop.opened).isEmpty();
        ArgumentCaptor<Component> replies = ArgumentCaptor.forClass(Component.class);
        verify(console, org.mockito.Mockito.times(2)).sendMessage(replies.capture());
        assertThat(replies.getAllValues())
                .extracting(PlainTextComponentSerializer.plainText()::serialize)
                .containsOnly("Only a player can run this command.");
    }

    private static CommandSourceStack source(
            ConsoleCommandSender sender, org.bukkit.entity.@org.jspecify.annotations.Nullable Entity executor) {
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(sender);
        when(source.getExecutor()).thenReturn(executor);
        return source;
    }
}
