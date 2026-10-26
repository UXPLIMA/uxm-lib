package com.uxplima.uxmlib.menu.render;

import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * Paints the engine's two-button confirm window. The decision carried by each button (what yes and no run) is the
 * {@code ConfirmState} on the holder; this renderer only places the two buttons where the {@link ConfirmLook} puts
 * them, which the façade records onto that state so the one listener can route a click back to the right decision.
 *
 * <p>Each button carries the word it is handed, which the façade reads from the viewer's catalogue. A button with an
 * empty name is not wordless: the client fills the gap with the block's own English name.
 */
@NullMarked
public final class ConfirmRenderer {

    public ConfirmRenderer() {}

    /** Place the yes and no buttons of {@code look} in {@code inv}, named {@code yes} and {@code no}. */
    public void populate(Inventory inv, ConfirmLook look, Component yes, Component no) {
        Objects.requireNonNull(inv, "inv");
        Objects.requireNonNull(look, "look");
        Objects.requireNonNull(yes, "yes");
        Objects.requireNonNull(no, "no");
        inv.setItem(look.yesSlot(), button(look.yesMaterial(), yes));
        inv.setItem(look.noSlot(), button(look.noMaterial(), no));
    }

    private static ItemStack button(Material material, Component name) {
        return ItemBuilder.of(material).name(name).build();
    }
}
