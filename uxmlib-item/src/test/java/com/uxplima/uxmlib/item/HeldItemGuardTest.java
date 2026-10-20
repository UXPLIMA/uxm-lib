package com.uxplima.uxmlib.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Every way a held item can leave the player, refused in one place. The lobby tool guarded a click on the item and
 * a click with it on the cursor, and a number key pressed over a chest slot still carried it out of the hotbar.
 */
class HeldItemGuardTest {

    private static final NamespacedKey MARK = new NamespacedKey("test", "tool");

    private ServerMock server;
    private PlayerMock player;

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        server.getPluginManager().registerEvents(new HeldItemGuard(HeldItemGuardTest::isOurs), plugin);
        player = server.addPlayer();
        player.getInventory().clear();
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a click on the item is refused")
    void aClickOnTheItemIsRefused() {
        player.getInventory().setItem(4, tool());

        InventoryClickEvent click = click(4, ClickType.LEFT, InventoryAction.PICKUP_ALL, -1);

        assertThat(click.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("a number key that would swap the item out of the hotbar is refused")
    void aNumberKeySwapIsRefused() {
        player.getInventory().setItem(4, tool());
        Inventory chest = server.createInventory(null, 27);
        InventoryView view = player.openInventory(chest);

        InventoryClickEvent swap = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 0, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 4);
        server.getPluginManager().callEvent(swap);

        assertThat(swap.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("a click on some other item is let through")
    void anotherItemMovesFreely() {
        player.getInventory().setItem(4, new ItemStack(Material.DIAMOND));

        InventoryClickEvent click = click(4, ClickType.LEFT, InventoryAction.PICKUP_ALL, -1);

        assertThat(click.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("dragging the item is refused")
    void aDragIsRefused() {
        InventoryView view = player.openInventory(server.createInventory(null, 27));
        InventoryDragEvent drag =
                new InventoryDragEvent(view, null, tool(), false, Map.of(10, new ItemStack(Material.COMPASS)));
        server.getPluginManager().callEvent(drag);

        assertThat(drag.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("dropping the item is refused")
    void aDropIsRefused() {
        Item dropped = player.getWorld().dropItem(player.getLocation(), tool());
        PlayerDropItemEvent drop = new PlayerDropItemEvent(player, dropped);
        server.getPluginManager().callEvent(drop);

        assertThat(drop.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("swapping the item into the off hand is refused")
    void anOffHandSwapIsRefused() {
        PlayerSwapHandItemsEvent swap = new PlayerSwapHandItemsEvent(player, new ItemStack(Material.AIR), tool());
        server.getPluginManager().callEvent(swap);

        assertThat(swap.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("the item never reaches the ground on death")
    void itNeverDropsOnDeath() {
        List<ItemStack> drops = new ArrayList<>(List.of(tool(), new ItemStack(Material.DIAMOND)));
        PlayerDeathEvent death = new PlayerDeathEvent(
                player, DamageSource.builder(DamageType.GENERIC).build(), drops, 0, Component.empty(), false);
        server.getPluginManager().callEvent(death);

        assertThat(death.getDrops()).extracting(ItemStack::getType).containsExactly(Material.DIAMOND);
    }

    private InventoryClickEvent click(int slot, ClickType type, InventoryAction action, int key) {
        InventoryView view = player.openInventory(server.createInventory(null, 27));
        InventoryClickEvent click = new InventoryClickEvent(
                view, InventoryType.SlotType.QUICKBAR, rawSlotOf(view, slot), type, action, key);
        // The server puts the clicked item on the event; the mock view does not read the player's half of a chest
        // view, so it is handed over here.
        click.setCurrentItem(player.getInventory().getItem(slot));
        server.getPluginManager().callEvent(click);
        return click;
    }

    private static ItemStack tool() {
        ItemStack item = new ItemStack(Material.COMPASS);
        Items.editPdc(item, pdc -> pdc.set(MARK, PersistentDataType.BYTE, (byte) 1));
        return item;
    }

    private static boolean isOurs(ItemStack item) {
        return Pdc.read(item).has(MARK, PersistentDataType.BYTE);
    }

    /**
     * The raw slot that shows {@code slot} of the player's own inventory, asked of the view rather than counted. The
     * view is a chest because the mock server leaves the player's own view's slot conversion unimplemented.
     */
    private int rawSlotOf(InventoryView view, int slot) {
        for (int raw = view.getTopInventory().getSize(); raw < view.countSlots(); raw++) {
            if (view.convertSlot(raw) == slot) {
                return raw;
            }
        }
        throw new IllegalStateException("the view shows no slot " + slot);
    }
}
