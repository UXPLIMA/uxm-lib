package com.uxplima.uxmlib.menu.spec;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A list-backed item: the engine asks the {@code source} reference for a collection of entries and stamps the
 * {@code template} item once per entry, expanding a single spec block into a paginated grid.
 *
 * <p>{@code pageSize} and {@code sorts} are the knobs a paged source reads to page and order its corpus down at the
 * store rather than in memory. A {@code pageSize} of {@code 0} means "derive the page from the item's slot count",
 * which is the historic behaviour every in-memory list still gets; {@code sorts} is the ordered list of sort keys a
 * paged source offers (empty means its default order). Both are inert for a plain in-memory source, which hands its
 * whole corpus over for the engine to slice: the binding validator rejects a spec that sets them there so the
 * mismatch is a loud configuration error rather than a knob that silently does nothing.
 *
 * <p>{@code empty} is the item drawn in each slot a page leaves without an entry, and {@code align} says where the
 * entries of a short page sit. Both are the file's choice: with no empty item a cell without an entry shows what the
 * file layered under the list, and failing that the window's fill.
 */
public record ListSpec(
        Ref source,
        MenuItemSpec template,
        int pageSize,
        List<String> sorts,
        Optional<MenuItemSpec> empty,
        ListAlign align) {

    public ListSpec {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(template, "template");
        if (pageSize < 0) {
            throw new IllegalArgumentException("pageSize must be >= 0: " + pageSize);
        }
        sorts = List.copyOf(Objects.requireNonNull(sorts, "sorts"));
        Objects.requireNonNull(empty, "empty");
        Objects.requireNonNull(align, "align");
    }

    /** A list with no empty item that fills from its first slot: the list most code builds. */
    public ListSpec(Ref source, MenuItemSpec template, int pageSize, List<String> sorts) {
        this(source, template, pageSize, sorts, Optional.empty(), ListAlign.START);
    }
}
