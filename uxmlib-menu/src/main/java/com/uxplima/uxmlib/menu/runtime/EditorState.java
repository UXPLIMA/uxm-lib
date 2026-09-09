package com.uxplima.uxmlib.menu.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmlib.menu.property.ConfirmOpener;
import com.uxplima.uxmlib.menu.property.EditableProperty;
import com.uxplima.uxmlib.menu.property.SelectorOpener;
import com.uxplima.uxmlib.menu.render.EditorRenderer;
import org.jspecify.annotations.Nullable;

/**
 * The per-open state of an editor menu, parked on its {@link MenuHolder} on the editor path only. Where a spec
 * menu re-derives its slots from a {@link com.uxplima.uxmlib.menu.spec.MenuSpec},
 * an editor re-derives its property buttons from the {@code EditorSpec} and the live subject the properties close
 * over, so the holder carries the spec and subject here and the editor renderer re-reads the property list fresh on
 * every draw, which is what makes an edited value show without a reopen.
 *
 * <p>It also owns the slot routing for one draw. A property slot maps to its {@link EditableProperty}; the click
 * listener consults {@link #propertyAt} to route a click there into {@link EditableProperty#onClick} with a freshly
 * built click context. A plain button (back, delete) maps to a {@link Runnable} the listener runs as-is on the
 * viewer's entity thread. Keeping these maps off the spec {@code clickMap} is what lets the one listener tell an
 * editor button from a spec item without forcing an editor's typed property onto a {@code RenderedSlot}, which
 * carries a spec item an editor button has none of.
 *
 * <p>The {@code spec} is held as {@link Object} so this runtime class needs no compile dependency on the public
 * {@code EditorSpec} façade type (the editor renderer, which already depends on that type, casts it back) and the
 * {@code runtime}→{@code menu} package edge stays acyclic. The subject is the same nullable domain object
 * {@link MenuContext} carries, repeated here only so the property-list provider can be re-applied without reaching
 * back through the context.
 */
public final class EditorState {

    private final Object spec;

    @Nullable private final Object subject;

    private final Map<Integer, EditableProperty> propertySlots = new HashMap<>();

    private final Map<Integer, Runnable> buttonSlots = new HashMap<>();

    /** What a click on this editor needs from the engine that opened it, or null on an editor opened without one. */
    @Nullable private final Clicks clicks;

    /**
     * What a click on one of this editor's properties needs from the engine that opened it: the renderer that
     * repaints the window after a value changes, the opener a property shows its picker through, and the opener a
     * removal is gated behind.
     *
     * <p>They are carried here rather than asked of the click listener, because a consumer builds its engine and its
     * listener separately and nothing made the two agree. uxmCrates wired an engine that could open an editor and a
     * listener that could not serve one: every click on a property threw {@code an editor listener needs a selector
     * opener} into the console, the window sat there doing nothing, and the whole editor read as broken. Every other
     * plugin in the estate wires its listener the same way, so every editor in the estate had the same hole.
     *
     * <p>An editor opened through the engine now carries the engine's own three, so the pair cannot disagree. The
     * listener's own fields stay as the fallback for a listener built with them directly.
     *
     * @param renderer repaints this editor in place after a property writes a value
     * @param selector opens a property's picker as an engine child window
     * @param confirm gates a property's removal behind an engine confirm child
     */
    public record Clicks(EditorRenderer renderer, SelectorOpener selector, ConfirmOpener confirm) {

        public Clicks {
            Objects.requireNonNull(renderer, "renderer");
            Objects.requireNonNull(selector, "selector");
            Objects.requireNonNull(confirm, "confirm");
        }
    }

    public EditorState(Object spec, @Nullable Object subject) {
        this(spec, subject, null);
    }

    /** The same, carrying what the opening engine hands a click on one of this editor's properties. */
    public EditorState(Object spec, @Nullable Object subject, @Nullable Clicks clicks) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.subject = subject;
        this.clicks = clicks;
    }

    /** What a property click uses, when the engine that opened this editor handed it over. */
    public Optional<Clicks> clicks() {
        return Optional.ofNullable(clicks);
    }

    /** The {@code EditorSpec} this editor was opened from, opaque here and cast back by the editor renderer. */
    public Object spec() {
        return spec;
    }

    /** The live subject the properties close over (null for a subject-less editor), re-applied on each draw. */
    @Nullable public Object subject() {
        return subject;
    }

    /** Drops every recorded slot ahead of a re-render so a stale button can never be clicked. */
    public void clearSlots() {
        propertySlots.clear();
        buttonSlots.clear();
    }

    /** Records the property the editor renderer painted at {@code slot}, so a later click on it routes back here. */
    public void recordProperty(int slot, EditableProperty property) {
        Objects.requireNonNull(property, "property");
        propertySlots.put(slot, property);
    }

    /** Records a plain button (back, delete) at {@code slot}; its action runs as-is on a click. */
    public void recordButton(int slot, Runnable action) {
        Objects.requireNonNull(action, "action");
        buttonSlots.put(slot, action);
    }

    /** The property drawn at {@code slot}, or empty when the slot carries no property button. */
    public Optional<EditableProperty> propertyAt(int slot) {
        return Optional.ofNullable(propertySlots.get(slot));
    }

    /** The plain-button action at {@code slot}, or empty when the slot carries no such button. */
    public Optional<Runnable> buttonAt(int slot) {
        return Optional.ofNullable(buttonSlots.get(slot));
    }
}
