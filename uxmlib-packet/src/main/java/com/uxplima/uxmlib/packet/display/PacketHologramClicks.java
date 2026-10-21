package com.uxplima.uxmlib.packet.display;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;

import com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent;
import com.uxplima.uxmlib.scheduler.Scheduler;

/**
 * Answers a click on a {@link PacketHologram}'s box.
 *
 * <p>The box exists only in the clients it was sent to, so the server does not know it was clicked through the
 * ordinary interact event. Paper reports it as an unknown entity with the id the client named, and this
 * listener finds the hologram that owns that id. A client may name any id, so a click counts only from a
 * player whose client was sent that box and still holds it: one outside the audience or out of range is not
 * answered, and neither is a click on a hologram that was removed. The client sends one event per hand for a
 * single click, so only the main hand's is answered. The handler runs on the clicking player's own thread,
 * where running a command or opening a menu for them is safe.
 *
 * <p>Register it as a listener once: {@code server.getPluginManager().registerEvents(clicks, plugin)}.
 */
public final class PacketHologramClicks implements Listener {

    /** What a click on a box does. */
    @FunctionalInterface
    public interface Clicked {

        /**
         * Called on {@code player}'s own thread.
         *
         * @param attack whether the click was a left click, an attack, rather than a use
         */
        void clicked(Player player, boolean attack);
    }

    private final Scheduler scheduler;
    private final Map<PacketHologram, Clicked> handlers = new ConcurrentHashMap<>();

    public PacketHologramClicks(Scheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Answer clicks on {@code hologram}'s box with {@code handler}, replacing any handler it had. */
    public void register(PacketHologram hologram, Clicked handler) {
        handlers.put(Objects.requireNonNull(hologram, "hologram"), Objects.requireNonNull(handler, "handler"));
    }

    /** Stop answering clicks on {@code hologram}, as it is removed. */
    public void unregister(PacketHologram hologram) {
        handlers.remove(hologram);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUseUnknownEntity(PlayerUseUnknownEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        int entityId = event.getEntityId();
        Player player = event.getPlayer();
        for (Map.Entry<PacketHologram, Clicked> entry : handlers.entrySet()) {
            if (entry.getKey().clickableBy(player, entityId)) {
                boolean attack = event.isAttack();
                Clicked handler = entry.getValue();
                scheduler.entity(player, () -> handler.clicked(player, attack));
                return;
            }
        }
    }
}
