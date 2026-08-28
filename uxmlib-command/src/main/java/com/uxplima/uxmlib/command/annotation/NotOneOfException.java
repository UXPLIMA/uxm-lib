package com.uxplima.uxmlib.command.annotation;

import java.util.List;
import java.util.Objects;

/**
 * A rejection that knows the whole set it was matching against, thrown by a resolver whose accepted values
 * are a fixed list. {@link ArgBinder} recognises it and carries {@link #allowed()} into the
 * {@link ErrorContext}, so a message layer can word "not one of these" in its own language rather than
 * translating the English sentence this exception also carries for logs and untranslated callers.
 */
final class NotOneOfException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final transient List<String> allowed;

    NotOneOfException(String input, List<String> allowed) {
        super("expected one of " + String.join(", ", Objects.requireNonNull(allowed, "allowed")) + ", got "
                + Objects.requireNonNull(input, "input"));
        this.allowed = List.copyOf(allowed);
    }

    /** The values that would have been accepted, spelled as tab-completion offers them. */
    List<String> allowed() {
        return allowed;
    }
}
