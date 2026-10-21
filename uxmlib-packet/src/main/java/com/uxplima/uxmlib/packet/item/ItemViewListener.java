package com.uxplima.uxmlib.packet.item;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

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
 * draw them again on top. Every changed copy therefore carries a token under this listener's key, the server
 * keeps the item behind each token for that viewer, and a creative slot that brings a copy back hands the
 * server its own item, in the count the client chose. The item itself never travels: a client could forge it,
 * read what the view hid, or swell a packet with it. A token another viewer was sent, or one the server no
 * longer holds, restores nothing and its mark is dropped. A second view on the same connection draws over the
 * first and is undone before it, so the copies unwind in order.
 *
 * <p>Runs on a Netty I/O thread. See {@link ItemView} for what that forbids.
 */
public final class ItemViewListener implements PacketListener {

    private final ItemPackets packets;
    private final NamespacedKey shownFrom;
    private final ItemView view;
    private final Map<UUID, ShownItems> shownTo = new ConcurrentHashMap<>();

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
        UUID viewer = player;
        Object replacement = packets.withClientItem(packet, sent -> restored(viewer, sent));
        return replacement == null ? PacketVerdict.pass() : PacketVerdict.rewrite(replacement);
    }

    /** The view's copy, carrying the server's own item when it differs from it. */
    private ItemStack shown(UUID viewer, ItemStack real) {
        ItemStack drawn = Objects.requireNonNull(view.shownTo(viewer, real), "view");
        if (drawn.equals(real)) {
            return real;
        }
        long token = shownTo.computeIfAbsent(viewer, id -> new ShownItems()).keep(real);
        ItemStack marked = drawn.clone();
        ItemMeta meta = marked.getItemMeta();
        meta.getPersistentDataContainer().set(shownFrom, PersistentDataType.LONG, token);
        marked.setItemMeta(meta);
        return marked;
    }

    /** Drop what was kept for {@code viewer}, who left. */
    public void forget(UUID viewer) {
        shownTo.remove(viewer);
    }

    /**
     * The server's own item when {@code sent} is a copy this listener drew for {@code viewer}, in the count the
     * client chose; {@code sent} without its mark when the token is not one the server holds for them.
     */
    private ItemStack restored(UUID viewer, ItemStack sent) {
        if (!sent.hasItemMeta()) {
            return sent;
        }
        Long token = sent.getItemMeta().getPersistentDataContainer().get(shownFrom, PersistentDataType.LONG);
        if (token == null) {
            return sent;
        }
        ShownItems kept = shownTo.get(viewer);
        @Nullable ItemStack real = kept == null ? null : kept.find(token);
        if (real == null) {
            ItemStack unmarked = sent.clone();
            ItemMeta meta = unmarked.getItemMeta();
            meta.getPersistentDataContainer().remove(shownFrom);
            unmarked.setItemMeta(meta);
            return unmarked;
        }
        real.setAmount(sent.getAmount());
        return real;
    }

    /**
     * The items one viewer was last shown copies of, by token. Bounded, so a long session cannot grow it: an item
     * drawn so long ago that it fell out was resent since, under a newer token.
     */
    private static final class ShownItems {

        private static final int KEPT = 512;

        private final Map<Long, ItemStack> items = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, ItemStack> eldest) {
                return size() > KEPT;
            }
        };

        synchronized long keep(ItemStack real) {
            long token = ThreadLocalRandom.current().nextLong();
            items.put(token, real.clone());
            return token;
        }

        synchronized @Nullable ItemStack find(long token) {
            ItemStack real = items.get(token);
            return real == null ? null : real.clone();
        }
    }
}
