package com.uxplima.uxmlib.bedrock;

import java.util.Map;
import java.util.Objects;

/**
 * A Bedrock player's answer to a {@link BedrockFormRequest}, as plain data that can cross a wire.
 *
 * <p>A {@link Submitted} answer may carry a secret, such as a PIN typed into an input. It goes only to the screen
 * that asked; it is never logged, never sent to another server, and never echoed to the client. Its
 * {@code toString} names the fields and leaves the values out.
 */
public sealed interface BedrockFormResponse {

    /** The player closed the form, or the client sent an answer that could not be read. */
    BedrockFormResponse CLOSED = new Closed();

    /**
     * A tapped button of a {@link BedrockFormRequest.Simple} or {@link BedrockFormRequest.Modal} form.
     *
     * @param index the zero-based index of the button
     */
    record Tapped(int index) implements BedrockFormResponse {}

    /**
     * The submitted values of a {@link BedrockFormRequest.Custom} form: an input's text, a dropdown's chosen option,
     * a slider's value without a trailing {@code .0}, a toggle's {@code true} or {@code false}.
     *
     * @param values each value widget's value by its name; never {@code null}, copied
     */
    record Submitted(Map<String, String> values) implements BedrockFormResponse {
        public Submitted {
            values = Map.copyOf(Objects.requireNonNull(values, "values"));
        }

        /** The field names only: a value may be a secret. */
        @Override
        public String toString() {
            return "Submitted[fields=" + values.keySet() + "]";
        }
    }

    /** The answer {@link #CLOSED} stands for. */
    record Closed() implements BedrockFormResponse {}
}
