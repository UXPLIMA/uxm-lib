package com.uxplima.uxmlib.menu.spec;

import java.util.Locale;
import java.util.Optional;

/**
 * Where a list's entries sit when a page has fewer of them than slots. {@link #START} fills from the first slot, as
 * every list always has. {@link #CENTER} fills the full rows the same way and puts the short last row in the middle of
 * its row, so three entries in a row of seven read as a group rather than as a gap on one side.
 */
public enum ListAlign {
    START,
    CENTER;

    /** The alignment a file names: {@code start}, or {@code center} as well as {@code centre}, in any case. */
    public static Optional<ListAlign> parse(String raw) {
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "start" -> Optional.of(START);
            case "center", "centre" -> Optional.of(CENTER);
            default -> Optional.empty();
        };
    }
}
