package com.uxplima.uxmlib.command.annotation;

import java.util.List;
import java.util.Objects;

/**
 * The typed context of a per-argument failure: which {@code argument} the sender got wrong, the raw
 * {@code input} they gave for it, and the {@code reason} it was rejected. Where a bare
 * {@link IllegalArgumentException} only carries a flat message, this names the failing argument so the reply
 * can point at it precisely ("Invalid value 'abc' for &lt;amount&gt;: not a number") and so a consumer can
 * localize the three parts independently. The parts are handed to {@link CommandMessages} to be worded; this
 * record decides nothing about the wording itself.
 *
 * @param argument the argument name as declared on its {@code @Arg}
 * @param input the raw text the sender supplied for it
 * @param reason why it was rejected; may be empty when the resolver gave no detail
 * @param allowed the values that would have been accepted, when the rejection knows them (an enum argument);
 *     empty otherwise. A message layer that has these can word the rejection itself instead of translating
 *     {@code reason}, which is why they travel separately.
 */
record ErrorContext(String argument, String input, String reason, List<String> allowed) {

    ErrorContext {
        Objects.requireNonNull(argument, "argument");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(reason, "reason");
        allowed = List.copyOf(Objects.requireNonNull(allowed, "allowed"));
    }

    /** A rejection with no known set of accepted values. */
    ErrorContext(String argument, String input, String reason) {
        this(argument, input, reason, List.of());
    }

    /** The flat single-line form of the rejection, for an exception message or a log line. */
    String message() {
        String head = "Invalid value '" + input + "' for <" + argument + ">";
        return reason.isEmpty() ? head : head + ": " + reason;
    }
}
