package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The suggestion an argument that takes a player by name gets.
 *
 * <p>A command that names a {@code Player} gets Brigadier's own player argument and suggests for free. One
 * that acts on somebody offline takes a {@code String} and looks the name up itself, and a {@code String}
 * argument suggests nothing at all: twenty five arguments across five plugins were typed blind until the
 * owner tried to give a key on 2026-09-08 and got no help.
 */
class PlayerNamesTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /**
     * The context is not read: the source asks the server who is online and nothing else, so a mock of it
     * would prove only that the mock was passed. Brigadier's own builder is real here, which is what decides
     * whether a prefix matches.
     */
    @SuppressWarnings("unchecked")
    private static List<String> suggestionsFor(String written) {
        SuggestionsBuilder builder = new SuggestionsBuilder("give " + written, "give ".length());
        CommandContext<CommandSourceStack> unused = org.mockito.Mockito.mock(CommandContext.class);
        return new PlayerNames()
                .suggest(unused, builder).join().getList().stream()
                        .map(Suggestion::getText)
                        .toList();
    }

    @Test
    @DisplayName("every online player is offered when nothing has been typed")
    void everyOnlinePlayerIsOffered() {
        server.addPlayer("Ada");
        server.addPlayer("Bob");

        assertThat(suggestionsFor("")).containsExactlyInAnyOrder("Ada", "Bob");
    }

    @Test
    @DisplayName("what has been typed narrows the list, whatever case it was typed in")
    void typingNarrowsTheList() {
        server.addPlayer("Ada");
        server.addPlayer("Bob");

        assertThat(suggestionsFor("a")).containsExactly("Ada");
        assertThat(suggestionsFor("A")).containsExactly("Ada");
    }

    @Test
    @DisplayName("an empty server offers nothing rather than failing")
    void anEmptyServerOffersNothing() {
        assertThat(suggestionsFor("")).isEmpty();
    }

    @Test
    @DisplayName("the default registry carries it, so no plugin has to register its own")
    void theDefaultRegistryCarriesIt() {
        assertThat(ParamResolvers.withDefaults().suggestionSource(PlayerNames.KEY))
                .describedAs("withDefaults registers PlayerNames, which is what makes @SuggestUsing work "
                        + "with no wiring in the consumer")
                .isNotNull();
    }
}
