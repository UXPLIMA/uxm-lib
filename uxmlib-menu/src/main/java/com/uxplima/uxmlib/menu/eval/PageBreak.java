package com.uxplima.uxmlib.menu.eval;

/**
 * A list entry that ends the page it lands on instead of taking a slot on it.
 *
 * <p>It is the marker a host uses when a run of entries belongs together and the break between two runs is
 * part of what the screen says: one group of rewards per page, one day of a log per page, one category of a
 * catalogue per page. Without it a host has to pad each run out to the end of its page with cells it invents,
 * which is filler an operator did not write and which every placeholder the window binds then has to answer
 * for.
 *
 * <p>It is a marker and not a cell. {@link Pagination} never places it in a slot and never counts it towards a
 * page, so the entry list a host hands over is its own content with the breaks written between the runs. A
 * break that falls where a page is already empty does nothing, so a leading break, a trailing break and two in
 * a row cost no blank page.
 *
 * <p>{@link PinnedEntry} is read first, so an entry that is both is pinned to its slot on every page and breaks
 * nothing. An entry is one or the other; being both is a defect in the host, not a shape this engine serves.
 */
public interface PageBreak {}
