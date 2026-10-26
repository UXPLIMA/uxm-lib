package com.uxplima.uxmlib.menu.render;

import java.util.Objects;

import org.bukkit.Material;

import org.jspecify.annotations.NullMarked;

/**
 * How the engine's confirm window looks: how many rows it has, and which block stands at which slot for each decision.
 *
 * <p>This is a look, so it is the host's to choose, and a host that reads it from its own file lets an operator choose
 * it. {@link #STANDARD} is what a host gets when it names none: three rows, a lime wool yes at slot 11 and a red wool
 * no at slot 15. The words on the buttons are not part of it; they come from the catalogue under {@code
 * gui.confirm.yes} and {@code gui.confirm.no}, in the viewer's language.
 *
 * @param rows how many rows the window has, from one to six
 * @param yesSlot the slot of the button that confirms, inside those rows
 * @param yesMaterial the block that stands for the confirm button
 * @param noSlot the slot of the button that cancels, inside those rows and apart from {@code yesSlot}
 * @param noMaterial the block that stands for the cancel button
 */
@NullMarked
public record ConfirmLook(int rows, int yesSlot, Material yesMaterial, int noSlot, Material noMaterial) {

    /** Three rows, a lime wool yes at slot 11 and a red wool no at slot 15. */
    public static final ConfirmLook STANDARD = new ConfirmLook(3, 11, Material.LIME_WOOL, 15, Material.RED_WOOL);

    private static final int MAX_ROWS = 6;

    public ConfirmLook {
        Objects.requireNonNull(yesMaterial, "yesMaterial");
        Objects.requireNonNull(noMaterial, "noMaterial");
        if (rows < 1 || rows > MAX_ROWS) {
            throw new IllegalArgumentException("a confirm window has one to six rows, not " + rows);
        }
        int size = rows * 9;
        if (yesSlot < 0 || yesSlot >= size || noSlot < 0 || noSlot >= size) {
            throw new IllegalArgumentException(
                    "both confirm buttons sit inside the " + size + " slots of the window: " + yesSlot + ", " + noSlot);
        }
        if (yesSlot == noSlot) {
            throw new IllegalArgumentException("the confirm buttons need two slots, both are at " + yesSlot);
        }
    }
}
