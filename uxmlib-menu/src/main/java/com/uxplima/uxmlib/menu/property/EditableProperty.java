package com.uxplima.uxmlib.menu.property;

import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import org.jspecify.annotations.NullMarked;

/**
 * One editable field in an {@link com.uxplima.uxmlib.menu.EntityEditorView}: the description of a single button (its
 * label, the lore line that reports the current value, and its icon) plus the behaviour run when the viewer clicks it.
 * The editor draws one button per property at its configured slot and routes a click on that slot back to {@link
 * #onClick(PropertyClick)}.
 *
 * <p>A property carries no domain logic of its own. Its {@link #onClick} performs a presentation step (cycle a flag,
 * open an anvil, step a number, open a sub-selector) and then hands the new value to a caller-supplied setter (a {@code
 * Consumer} that calls the module's existing application use case). The framework is the inbound adapter; the use case
 * is the only place the change is actually made, so the GUI and the equivalent command always go through the same code.
 *
 * <p>Implementations are canon-styled (label/value text via {@link String} catalog entries), schedule any write off the
 * tick thread through the shared {@code Scheduler}, and gate destructive steps behind a confirm. The supplied set is
 * {@link ToggleProperty}, {@link TextProperty}, {@link NumberProperty}, {@link EnumProperty}, and {@link ListProperty};
 * a module composes them, it does not subclass them.
 */
@NullMarked
public interface EditableProperty {

    /** The catalog key for this field's button name (e.g. "Difficulty", "Public"). */
    String label();

    /**
     * The rendered current value to show in the button lore for {@code viewer}, already resolved to a display
     * string in the viewer's locale (e.g. "On", "42", "Easy"). The editor wraps it in the configured
     * value-lore catalog line, so this returns the bare value text, not a full lore block.
     */
    String valueLore(Player viewer);

    /**
     * The value already painted, for a property whose value carries a colour of its own.
     *
     * <p>Empty is the ordinary answer and means {@link #valueLore} is the whole of it: plain words the editor
     * puts through the spec's value line and the catalogue inserts <em>as text</em>. That is deliberate and it
     * has to stay: a text property's value is whatever an operator typed, and an operator who names a crate
     * {@code <red>} has to read those characters back rather than have them parsed.
     *
     * <p>Which leaves nowhere for a value that is genuinely markup. A toggle's state is a coloured word by
     * UI-STYLE 9, and a toggle that answered {@code "<good>On"} had those characters printed on the button:
     * the owner read {@code ANNOUNCED TO THE SERVER <#9AA5BE>ᴏꜰꜰ} off a uxmCrates screen on 2026-09-09. A
     * property that has already painted its value says so here and the editor draws it as it is.
     */
    default Optional<Component> drawnValue(Player viewer) {
        return Optional.empty();
    }

    /**
     * The catalogue key of this button's own {@code →} line, or empty to take the editor's.
     *
     * <p>An editor names one click line for every button it draws, which is right while every button does the
     * same thing. A toggle steps, a list opens a window, and a button that takes the item out of your hand
     * does neither: all three read "click to write a new value" until they say otherwise.
     */
    default String action() {
        return "";
    }

    /** The button icon material; from the editor layout conf, never hardcoded by a property. */
    Material icon();

    /** Perform the edit for a click on this field's button. */
    void onClick(PropertyClick click);
}
