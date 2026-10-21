package com.uxplima.uxmlib.packet.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.pipeline.PacketVerdict;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * A creative client sends back the item it was shown, and the server would keep the words a view drew on it, then
 * draw them a second time. Proven on a live 26.3 server: after one creative slot packet the server's sword carried
 * the lore the view had drawn. So every drawn copy carries a token for the server's own item, kept on the server
 * for that viewer, and a creative slot packet that brings a drawn copy back hands the server its own item instead.
 * The item itself never travels: a client could forge it, read what the view hid, or swell a packet with it.
 */
class ItemViewCreativeTest {

    private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final NamespacedKey KEY = new NamespacedKey("host", "shown-from");

    private final FakeItemPackets packets = new FakeItemPackets();
    private ItemViewListener listener;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        listener = new ItemViewListener(packets, KEY, (viewer, real) -> drawn(real));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aDrawnItemCarriesATokenAndNotTheServersItem() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);

        ItemStack shown = shownCopyOf(sword);

        assertThat(shown.getItemMeta().getPersistentDataContainer().has(KEY, PersistentDataType.LONG))
                .isTrue();
        assertThat(shown.getItemMeta().getPersistentDataContainer().getKeys()).containsExactly(KEY);
    }

    @Test
    void aCreativeSlotCarryingADrawnItemKeepsTheServersStack() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemStack shown = shownCopyOf(sword);
        shown.setAmount(3);

        PacketVerdict verdict = listener.onReceiveVerdict(VIEWER, new FakeItemPackets.CreativeSlot(36, shown));

        FakeItemPackets.CreativeSlot restored =
                (FakeItemPackets.CreativeSlot) Objects.requireNonNull(verdict.replacement());
        ItemStack expected = sword.clone();
        expected.setAmount(3);
        assertThat(restored.slot()).isEqualTo(36);
        assertThat(restored.item()).isEqualTo(expected);
    }

    @Test
    void aCreativeSlotCarryingAnUnmarkedItemIsLeftAlone() {
        ItemStack own = new ItemStack(Material.STONE);

        PacketVerdict verdict = listener.onReceiveVerdict(VIEWER, new FakeItemPackets.CreativeSlot(36, own));

        assertThat(verdict.cancels()).isFalse();
        assertThat(verdict.replacement()).isNull();
    }

    @Test
    void anotherPlayersTokenRestoresNothing() {
        ItemStack shown = shownCopyOf(new ItemStack(Material.DIAMOND_SWORD));
        UUID mallory = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

        PacketVerdict verdict = listener.onReceiveVerdict(mallory, new FakeItemPackets.CreativeSlot(36, shown));

        FakeItemPackets.CreativeSlot kept =
                (FakeItemPackets.CreativeSlot) Objects.requireNonNull(verdict.replacement());
        assertThat(kept.item().getItemMeta().getPersistentDataContainer().has(KEY))
                .isFalse();
        assertThat(kept.item().getType()).isEqualTo(Material.DIAMOND_SWORD);
    }

    @Test
    void aForgedTokenRestoresNothing() {
        ItemStack forged = new ItemStack(Material.STONE);
        ItemMeta meta = forged.getItemMeta();
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.LONG, 42L);
        forged.setItemMeta(meta);

        PacketVerdict verdict = listener.onReceiveVerdict(VIEWER, new FakeItemPackets.CreativeSlot(36, forged));

        FakeItemPackets.CreativeSlot kept =
                (FakeItemPackets.CreativeSlot) Objects.requireNonNull(verdict.replacement());
        assertThat(kept.item()).isEqualTo(new ItemStack(Material.STONE));
    }

    @Test
    void aViewerWhoLeftIsForgotten() {
        ItemStack shown = shownCopyOf(new ItemStack(Material.DIAMOND_SWORD));

        listener.forget(VIEWER);
        PacketVerdict verdict = listener.onReceiveVerdict(VIEWER, new FakeItemPackets.CreativeSlot(36, shown));

        FakeItemPackets.CreativeSlot kept =
                (FakeItemPackets.CreativeSlot) Objects.requireNonNull(verdict.replacement());
        assertThat(kept.item().getItemMeta().getPersistentDataContainer().has(KEY))
                .isFalse();
    }

    @Test
    void anotherPluginsMarkIsLeftForThatPlugin() {
        ItemViewListener other =
                new ItemViewListener(packets, new NamespacedKey("other", "shown-from"), (viewer, real) -> real);
        ItemStack shown = shownCopyOf(new ItemStack(Material.DIAMOND_SWORD));

        PacketVerdict verdict = other.onReceiveVerdict(VIEWER, new FakeItemPackets.CreativeSlot(36, shown));

        assertThat(verdict.replacement()).isNull();
    }

    private ItemStack shownCopyOf(ItemStack real) {
        PacketVerdict verdict = listener.onSendVerdict(VIEWER, new FakeItemPackets.Slot(real));
        return ((FakeItemPackets.Slot) Objects.requireNonNull(verdict.replacement())).item();
    }

    private static ItemStack drawn(ItemStack real) {
        ItemStack copy = real.clone();
        ItemMeta meta = copy.getItemMeta();
        meta.lore(List.of(Component.text("Ember I")));
        copy.setItemMeta(meta);
        return copy;
    }
}
