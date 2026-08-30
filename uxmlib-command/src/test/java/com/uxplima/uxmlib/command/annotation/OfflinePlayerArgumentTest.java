package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.bukkit.OfflinePlayer;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Covers what an {@code OfflinePlayer} argument accepts. The point of the resolver is what it refuses: a
 * name the server does not hold is rejected here rather than looked up over the network, because a command
 * runs on the main thread and a web request there is a freeze the operator did not ask for.
 */
class OfflinePlayerArgumentTest {

    private ServerMock server;

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    @Test
    void aPlayerOnTheServerResolvesByName() {
        PlayerMock online = server.addPlayer("Ayse");

        assertThat(NativeResolvers.known("Ayse").getUniqueId()).isEqualTo(online.getUniqueId());
    }

    @Test
    void aNameTheServerHasNeverSeenIsRefusedAndNamed() {
        assertThatThrownBy(() -> NativeResolvers.known("Nobody"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nobody");
    }

    @Test
    void aUuidResolvesEvenWithoutAName() {
        UUID id = UUID.randomUUID();

        OfflinePlayer resolved = NativeResolvers.known(id.toString());

        assertThat(resolved.getUniqueId()).isEqualTo(id);
    }

    @Test
    void completionOffersThePlayersOnlineNow() {
        server.addPlayer("Ayse");
        server.addPlayer("Ahmet");
        server.addPlayer("Berk");

        assertThat(completions("a")).containsExactlyInAnyOrder("Ayse", "Ahmet");
    }

    @Test
    void completionFollowsWhoJoinsAfterTheTreeIsBuilt() {
        // A static list is read once when the command is registered. Whoever joins after that would never be
        // offered, which is the whole reason the resolver drives completion per keystroke.
        assertThat(completions("")).isEmpty();

        server.addPlayer("Ayse");

        assertThat(completions("")).containsExactly("Ayse");
    }

    private List<String> completions(String typed) {
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(server.getConsoleSender());
        @SuppressWarnings("unchecked")
        CommandContext<CommandSourceStack> context = mock(CommandContext.class);
        when(context.getSource()).thenReturn(source);

        SuggestionsBuilder builder = new SuggestionsBuilder("who " + typed, "who ".length());
        return NativeResolvers.onlineNames(context, builder).join().getList().stream()
                .map(Suggestion::getText)
                .toList();
    }
}
