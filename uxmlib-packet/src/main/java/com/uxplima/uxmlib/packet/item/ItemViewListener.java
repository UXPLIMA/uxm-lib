package com.uxplima.uxmlib.packet.item;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.uxplima.uxmlib.pipeline.PacketAction;
import com.uxplima.uxmlib.pipeline.PacketListener;
import com.uxplima.uxmlib.pipeline.PacketVerdict;
import org.jspecify.annotations.Nullable;

/**
 * The listener that puts an {@link ItemView} in front of every item a client is sent, and takes it off again
 * when a client sends one back.
 *
 * <p>It holds no packet knowledge of its own: {@link ItemPackets} answers what a packet carries and builds
 * the replacement, and this class only decides when to ask. It never cancels a packet, and it rewrites one
 * only when the view actually changed an item, so a server with a view that decorates nothing pays a type
 * test per outbound packet and nothing else.
 *
 * <p>A creative client sends back the item it was shown, so the server would keep the words the view drew and
 * draw them again on top. Every changed copy therefore carries the server's own item under this listener's
 * key, and a creative slot that brings a copy back hands the server its own item, in the count the client
 * chose. A second view on the same connection draws over the first and is undone before it, so the copies
 * unwind in order.
 *
 * <p>Runs on a Netty I/O thread. See {@link ItemView} for what that forbids.
 */
public final class ItemViewListener implements PacketListener {

    private final ItemPackets packets;
    private final NamespacedKey shownFrom;
    private final ItemView view;

    /**
     * @param shownFrom the key a drawn copy carries the server's own item under; one per plugin, so two views
     *     never undo each other's copies
     */
    public ItemViewListener(ItemPackets packets, NamespacedKey shownFrom, ItemView view) {
        this.packets = Objects.requireNonNull(packets, "packets");
        this.shownFrom = Objects.requireNonNull(shownFrom, "shownFrom");
        this.view = Objects.requireNonNull(view, "view");
    }

    /**
     * Never used to decide anything: this listener rewrites rather than cancels, so the pass/cancel half of
     * the seam always passes and {@link #onSendVerdict} carries the real answer.
     */
    @Override
    public PacketAction onSend(@Nullable UUID player, Object packet) {
        return PacketAction.PASS;
    }

    @Override
    public PacketVerdict onSendVerdict(@Nullable UUID player, Object packet) {
        Objects.requireNonNull(packet, "packet");
        if (player == null || !packets.carriesItems(packet)) {
            // Before login there is no viewer to draw for, and most outbound packets carry no item at all.
            return PacketVerdict.pass();
        }
        UUID viewer = player;
        Object replacement = packets.withItems(packet, real -> shown(viewer, real));
        return replacement == null ? PacketVerdict.pass() : PacketVerdict.rewrite(replacement);
    }

    @Override
    public PacketVerdict onReceiveVerdict(@Nullable UUID player, Object packet) {
        Objects.requireNonNull(packet, "packet");
        if (player == null || !packets.carriesClientItem(packet)) {
            return PacketVerdict.pass();
        }
        Object replacement = packets.withClientItem(packet, this::restored);
        return replacement == null ? PacketVerdict.pass() : PacketVerdict.rewrite(replacement);
    }

    /** The view's copy, carrying the server's own item when it differs from it. */
    private ItemStack shown(UUID viewer, ItemStack real) {
        ItemStack drawn = Objects.requireNonNull(view.shownTo(viewer, real), "view");
        if (drawn.equals(real)) {
            return real;
        }
        ItemStack marked = drawn.clone();
        ItemMeta meta = marked.getItemMeta();
        meta.getPersistentDataContainer().set(shownFrom, PersistentDataType.BYTE_ARRAY, packets.bytesOf(real));
        marked.setItemMeta(meta);
        return marked;
    }

    /** The server's own item when {@code sent} is a copy this listener drew, in the count the client chose. */
    private ItemStack restored(ItemStack sent) {
        if (!sent.hasItemMeta()) {
            return sent;
        }
        byte @Nullable [] own =
                sent.getItemMeta().getPersistentDataContainer().get(shownFrom, PersistentDataType.BYTE_ARRAY);
        if (own == null) {
            return sent;
        }
        ItemStack real = packets.itemOf(own);
        real.setAmount(sent.getAmount());
        return real;
    }
}
