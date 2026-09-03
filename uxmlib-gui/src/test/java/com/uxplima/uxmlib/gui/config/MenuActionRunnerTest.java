package com.uxplima.uxmlib.gui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.sound.AudioExperience;

/** What a viewer sees when a menu runs the lines its file wrote. */
class MenuActionRunnerTest {

    private ServerMock server;
    private PlayerMock viewer;
    private MenuActions actions;
    private final List<String> opened = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        viewer = server.addPlayer();
        actions = new MenuActions();
        opened.clear();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("open reaches the opener, with the menu name")
    void opensAnotherMenu() {
        runner().run(viewer, MenuAction.read("open:categories", Set.of()::contains));

        assertThat(opened).containsExactly("categories");
    }

    @Test
    @DisplayName("close shuts the window")
    void closesTheWindow() {
        viewer.openInventory(server.createInventory(null, 27));
        assertThat(viewer.getOpenInventory().getType()).isNotEqualTo(org.bukkit.event.inventory.InventoryType.CRAFTING);

        runner().run(viewer, MenuAction.read("close", Set.of()::contains));

        assertThat(viewer.getOpenInventory().getType()).isEqualTo(org.bukkit.event.inventory.InventoryType.CRAFTING);
    }

    @Test
    @DisplayName("a message reaches the viewer, read as MiniMessage")
    void sendsAMessage() {
        runner().run(viewer, MenuAction.read("message:<green>Bought.", Set.of()::contains));

        assertThat(viewer.nextComponentMessage()).isNotNull();
    }

    @Test
    @DisplayName("a sound reaches the viewer, with the volume and the pitch the file wrote")
    void playsASound() {
        runner().run(viewer, MenuAction.read("sound:block.note_block.pling 0.6 1.5", Set.of()::contains));

        AudioExperience heard = viewer.getHeardSounds().getFirst();
        assertThat(heard.getSound()).isEqualTo("minecraft:block.note_block.pling");
        assertThat(heard.getVolume()).isEqualTo(0.6F);
        assertThat(heard.getPitch()).isEqualTo(1.5F);
    }

    @Test
    @DisplayName("a sound written as a constant is asked of the server and plays as its key")
    void playsASoundNamedByItsConstant() {
        runner().run(viewer, MenuAction.read("sound:ITEM_BOOK_PAGE_TURN 0.7 1.2", Set.of()::contains));

        AudioExperience heard = viewer.getHeardSounds().getFirst();
        assertThat(heard.getSound()).isEqualTo("minecraft:item.book.page_turn");
        assertThat(heard.getPitch()).isEqualTo(1.2F);
    }

    @Test
    @DisplayName("a sound this server does not have is silence, never a broken click")
    void staysSilentForASoundTheServerDoesNotHave() {
        runner().run(viewer, MenuAction.read("sound:NO_SUCH_SOUND_AT_ALL", Set.of()::contains));

        assertThat(viewer.getHeardSounds()).isEmpty();
    }

    @Test
    @DisplayName("a named action runs the verb the plugin registered, with the rest of the line")
    void runsANamedAction() {
        List<String> bought = new ArrayList<>();
        actions.registerVerb("shop:buy", (player, argument) -> bought.add(player.getName() + " " + argument));

        runner().run(viewer, MenuAction.read("shop:buy diamond 4", actions::knows));

        assertThat(bought).containsExactly(viewer.getName() + " diamond 4");
    }

    @Test
    @DisplayName("a resolver reaches every argument, so a line may hold a token")
    void resolvesATokenInAnArgument() {
        List<String> bought = new ArrayList<>();
        actions.registerVerb("shop:buy", (player, argument) -> bought.add(argument));
        MenuActionRunner runner = new MenuActionRunner(
                actions, (player, menu) -> opened.add(menu), line -> line.replace("%item%", "diamond"));

        runner.run(viewer, MenuAction.readAll(List.of("shop:buy %item%", "open:%item%"), actions::knows));

        assertThat(bought).containsExactly("diamond");
        assertThat(opened).containsExactly("diamond");
    }

    @Test
    @DisplayName("every line runs, in the order the file wrote them")
    void runsAWholeListInOrder() {
        List<String> seen = new ArrayList<>();
        actions.registerVerb("shop:one", (player, argument) -> seen.add("one"));
        actions.registerVerb("shop:two", (player, argument) -> seen.add("two"));

        runner().run(viewer, MenuAction.readAll(List.of("shop:one", "shop:two"), actions::knows));

        assertThat(seen).containsExactly("one", "two");
    }

    @Test
    @DisplayName("a verb that left the registry after the file loaded is named, not silently dropped")
    void refusesAVerbThatIsNoLongerRegistered() {
        actions.registerVerb("shop:buy", (player, argument) -> {});
        MenuAction action = MenuAction.read("shop:buy diamond", actions::knows);

        assertThatThrownBy(() -> new MenuActionRunner(new MenuActions(), (player, menu) -> {}).run(viewer, action))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shop:buy");
    }

    @Test
    @DisplayName("a name with no colon cannot be registered, because a menu file could never name it")
    void refusesToRegisterAVerbWithNoColon() {
        assertThatThrownBy(() -> actions.registerVerb("buy", (player, argument) -> {}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("buy");
    }

    private MenuActionRunner runner() {
        return new MenuActionRunner(actions, (player, menu) -> opened.add(menu));
    }
}
