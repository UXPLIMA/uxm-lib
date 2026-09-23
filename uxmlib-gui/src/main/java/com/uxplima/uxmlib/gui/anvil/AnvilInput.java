package com.uxplima.uxmlib.gui.anvil;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import org.jspecify.annotations.Nullable;

/**
 * Captures a line of text from a player through a vanilla anvil: no NMS, no packets. Open one with a
 * prompt item (its display name is the hint shown in the left slot); the player types into the rename
 * field and clicks the result slot, and the callback receives an {@link AnvilResult}. Closing the anvil
 * without clicking the result yields {@link AnvilResult.Cancelled}.
 *
 * <p>Construct one per plugin, {@link #install()} it once, then call {@link #open} as needed. The result
 * slot is the anvil's slot 2; clicks elsewhere are cancelled so the prompt items cannot be taken.
 */
public final class AnvilInput implements Listener {

    private static final int RESULT_SLOT = 2;

    private final Plugin plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public AnvilInput(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /** Register the anvil listeners. Call once, on enable. */
    public void install() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Unregister the anvil listeners and drop any in-flight sessions. Call on disable, mirrors {@link #install()}. */
    public void uninstall() {
        org.bukkit.event.HandlerList.unregisterAll(this);
        sessions.clear();
    }

    /**
     * Open an anvil prompt for {@code player}. The {@code promptItem}'s name is the hint; {@code callback}
     * fires once with the submission or a cancellation.
     */
    public void open(Player player, ItemStack promptItem, Consumer<AnvilResult> callback) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(promptItem, "promptItem");
        Objects.requireNonNull(callback, "callback");
        InventoryView view = openAnvil(player, titleOf(promptItem));
        if (view == null) {
            callback.accept(AnvilResult.Cancelled.INSTANCE);
            return;
        }
        view.setItem(0, promptItem);
        sessions.put(player.getUniqueId(), new Session(promptItem, callback));
    }

    @EventHandler
    public void onPrepare(PrepareAnvilEvent event) {
        HumanEntity viewer = event.getView().getPlayer();
        Session session = sessions.get(viewer.getUniqueId());
        if (session != null && event.getResult() == null) {
            // Keep the result slot non-empty so the player can click it to submit.
            event.setResult(session.promptItem.clone());
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Session session = sessions.get(event.getWhoClicked().getUniqueId());
        if (session == null || !(event.getView() instanceof AnvilView anvilView)) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() != RESULT_SLOT) {
            return;
        }
        String text = anvilView.getRenameText();
        complete(event.getWhoClicked(), session, new AnvilResult.Submitted(text != null ? text : ""));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && session.done.compareAndSet(false, true)) {
            sessions.remove(event.getPlayer().getUniqueId());
            // Drop the prompt/result items before the close returns them, so a manual close (Esc) does not
            // hand the player the rename prompt item.
            event.getInventory().clear();
            session.callback.accept(AnvilResult.Cancelled.INSTANCE);
        }
    }

    /**
     * Open an anvil titled with the question. The old {@code openAnvil(null, true)} could not take a title, so the
     * window read "Repair & Name" over a field where the player was naming a home. Answers {@code null} when the
     * server did not open it.
     */
    private static @Nullable InventoryView openAnvil(Player player, Component title) {
        player.openInventory(MenuType.ANVIL.create(player, title));
        // The view the player holds now, rather than the one handed in: a server may wrap it on the way.
        InventoryView open = player.getOpenInventory();
        return open.getType() == InventoryType.ANVIL ? open : null;
    }

    /** The prompt's own name, which is the question the player answers, or no title when it has none. */
    private static Component titleOf(ItemStack prompt) {
        ItemMeta meta = prompt.getItemMeta();
        Component name = meta == null ? null : meta.displayName();
        return name == null ? Component.empty() : name;
    }

    private void complete(HumanEntity viewer, Session session, AnvilResult result) {
        if (session.done.compareAndSet(false, true)) {
            sessions.remove(viewer.getUniqueId());
            // Clear the anvil before closing so the close does not return the prompt/result items to the
            // player: otherwise submitting hands them the rename prompt item.
            Inventory top = viewer.getOpenInventory().getTopInventory();
            top.clear();
            viewer.closeInventory();
            session.callback.accept(result);
        }
    }

    private static final class Session {
        private final ItemStack promptItem;
        private final Consumer<AnvilResult> callback;
        private final AtomicBoolean done = new AtomicBoolean(false);

        private Session(ItemStack promptItem, Consumer<AnvilResult> callback) {
            this.promptItem = promptItem;
            this.callback = callback;
        }
    }
}
