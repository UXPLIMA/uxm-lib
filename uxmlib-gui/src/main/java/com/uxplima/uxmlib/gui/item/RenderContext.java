package com.uxplima.uxmlib.gui.item;

import java.util.Locale;
import java.util.Objects;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.gui.Gui;

/**
 * The per-viewer context handed to a {@link GuiItem} when its icon and action are resolved. It carries
 * who is looking at the menu, which menu and slot, so a dynamic item can render differently for each
 * player: a localized name, the viewer's own head, a value that depends on their permissions. The
 * viewer's {@link #locale()} is exposed so text resolves per player through Adventure.
 *
 * <p>The {@link #effectivePlayer()} is the player a placeholder modifier resolves against, which is usually
 * the {@link #viewer()} but can differ: an admin viewing another player's menu wants <em>that</em> player's
 * stats on the icon, not their own. It defaults to the viewer; use {@link #withEffectivePlayer} to retarget.
 */
public record RenderContext(Player viewer, Gui gui, int slot, Player effectivePlayer) {

    public RenderContext {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(gui, "gui");
        Objects.requireNonNull(effectivePlayer, "effectivePlayer");
    }

    /** A context whose placeholder target is the viewer themselves (the common case). */
    public RenderContext(Player viewer, Gui gui, int slot) {
        this(viewer, gui, slot, viewer);
    }

    /** A copy of this context whose placeholder target is {@code effectivePlayer}. */
    public RenderContext withEffectivePlayer(Player effectivePlayer) {
        Objects.requireNonNull(effectivePlayer, "effectivePlayer");
        return new RenderContext(viewer, gui, slot, effectivePlayer);
    }

    /** The viewer's client locale, for per-player text resolution. */
    public Locale locale() {
        return viewer.locale();
    }
}
