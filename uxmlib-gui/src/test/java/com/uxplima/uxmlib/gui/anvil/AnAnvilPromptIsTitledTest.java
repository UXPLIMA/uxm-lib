package com.uxplima.uxmlib.gui.anvil;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * An anvil opened to ask for text carries the question as its title.
 *
 * <p>It opened a plain anvil, so the window read "Repair & Name", the vanilla title, over a field where the player was
 * naming a home. A live sweep on a Paper 26.2 server read the vanilla key. The prompt item's name is the question, so
 * it is the title too.
 */
class AnAnvilPromptIsTitledTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("the anvil is titled with the prompt's name, and the prompt waits in its first slot")
    void theAnvilIsTitledWithThePrompt() {
        PlayerMock player = server.addPlayer();
        ItemStack prompt = new ItemStack(Material.PAPER);
        ItemMeta meta = prompt.getItemMeta();
        meta.displayName(Component.text("Name your home"));
        prompt.setItemMeta(meta);
        List<AnvilResult> results = new ArrayList<>();

        new AnvilInput(MockBukkit.createMockPlugin()).open(player, prompt, results::add);

        assertThat(player.getOpenInventory().getType()).isEqualTo(InventoryType.ANVIL);
        assertThat(PlainTextComponentSerializer.plainText()
                        .serialize(player.getOpenInventory().title()))
                .isEqualTo("Name your home");
        assertThat(results).isEmpty();
    }
}
