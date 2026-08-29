package com.uxplima.uxmlib.gui;

import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.gui.item.GuiItem;
import org.jspecify.annotations.Nullable;

/**
 * A ready-made yes/no confirmation menu: a title and two buttons (confirm and cancel) wired to a single
 * {@code Consumer<Boolean>} so a caller writes one decision handler instead of two. Confirm passes
 * {@code true}, cancel passes {@code false}; the menu closes itself before the decision runs so the handler
 * can open another menu without the close clobbering it. A thin convenience over {@link SimpleGui}, not a new
 * menu kind: pairs naturally with {@link GuiNavigator} for "are you sure?" flows.
 *
 * <pre>{@code
 * ConfirmMenu.of(Component.text("Delete home 'base'?"), confirmed -> {
 *     if (confirmed) deleteHome(player, "base");
 * }).open(player);
 * }</pre>
 */
public final class ConfirmMenu {

    private static final int CONFIRM_SLOT = 11;
    private static final int CANCEL_SLOT = 15;

    private final SimpleGui gui;

    private ConfirmMenu(SimpleGui gui) {
        this.gui = gui;
    }

    /** A confirmation menu with default green/red wool buttons and {@code onResult} run with the decision. */
    public static ConfirmMenu of(Component title, Consumer<Boolean> onResult) {
        return builder(title).onResult(onResult).build();
    }

    /** A confirmation menu that runs {@code onConfirm} on yes and {@code onCancel} on no. */
    public static ConfirmMenu of(Component title, Runnable onConfirm, Runnable onCancel) {
        Objects.requireNonNull(onConfirm, "onConfirm");
        Objects.requireNonNull(onCancel, "onCancel");
        return of(title, confirmed -> {
            if (confirmed) {
                onConfirm.run();
            } else {
                onCancel.run();
            }
        });
    }

    /** Start building a confirmation menu with custom button icons. */
    public static Builder builder(Component title) {
        return new Builder(title);
    }

    /** Open the confirmation menu for {@code viewer}. */
    public void open(HumanEntity viewer) {
        gui.open(Objects.requireNonNull(viewer, "viewer"));
    }

    /** The backing menu, for callers that want to decorate the remaining slots before opening. */
    public SimpleGui gui() {
        return gui;
    }

    /** Fluent builder: pick the two button icons and the result handler. */
    public static final class Builder {
        private final Component title;
        private ItemStack confirmIcon = named(Material.LIME_WOOL, "Confirm");
        private ItemStack cancelIcon = named(Material.RED_WOOL, "Cancel");
        private @Nullable Consumer<Boolean> onResult;

        private Builder(Component title) {
            this.title = Objects.requireNonNull(title, "title");
        }

        /** Use {@code icon} for the confirm (yes) button. */
        public Builder confirmIcon(ItemStack icon) {
            this.confirmIcon = Objects.requireNonNull(icon, "icon");
            return this;
        }

        /** Use {@code icon} for the cancel (no) button. */
        public Builder cancelIcon(ItemStack icon) {
            this.cancelIcon = Objects.requireNonNull(icon, "icon");
            return this;
        }

        /** Run {@code onResult} with {@code true} on confirm and {@code false} on cancel. */
        public Builder onResult(Consumer<Boolean> onResult) {
            this.onResult = Objects.requireNonNull(onResult, "onResult");
            return this;
        }

        /** Build the confirmation menu. */
        public ConfirmMenu build() {
            Consumer<Boolean> result = Objects.requireNonNull(onResult, "onResult");
            SimpleGui menu = Guis.gui().rows(3).title(title).build();
            menu.set(CONFIRM_SLOT, GuiItem.button(confirmIcon, event -> {
                menu.close(event.getWhoClicked());
                result.accept(Boolean.TRUE);
            }));
            menu.set(CANCEL_SLOT, GuiItem.button(cancelIcon, event -> {
                menu.close(event.getWhoClicked());
                result.accept(Boolean.FALSE);
            }));
            return new ConfirmMenu(menu);
        }

        private static ItemStack named(Material material, String name) {
            ItemStack stack = new ItemStack(material);
            stack.editMeta(meta -> meta.displayName(Component.text(name)));
            return stack;
        }
    }
}
