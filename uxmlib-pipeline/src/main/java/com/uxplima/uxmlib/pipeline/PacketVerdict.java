package com.uxplima.uxmlib.pipeline;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A listener's verdict on a packet. Three outcomes:
 *
 * <ul>
 *   <li>{@link #pass()}: let the packet continue unchanged;
 *   <li>{@link #cancel()}: drop the packet;
 *   <li>{@link #rewrite(Object)}: forward a <em>replacement</em> packet in place of the original.
 * </ul>
 *
 * <p>The folding rule (see {@link PacketListenerRegistry}) keeps {@code cancel} a veto that beats a rewrite, so an
 * observe-only or cancelling listener is never overridden by a rewriting one. {@link #from(PacketAction)} reads the
 * pass or cancel a folded dispatch carries back as a verdict.
 *
 * <p>A rewrite works both ways: on the way out the client is sent the replacement, and on the way in the server
 * reads it. The replacement is an opaque {@link Object} (the same raw packet type the channel speaks in), so this
 * type carries no packet knowledge.
 */
public sealed interface PacketVerdict permits PacketVerdict.Pass, PacketVerdict.Cancel, PacketVerdict.Rewrite {

    /** Let the packet continue unchanged. */
    static PacketVerdict pass() {
        return Pass.INSTANCE;
    }

    /** Drop the packet; it is not forwarded further along the pipeline. */
    static PacketVerdict cancel() {
        return Cancel.INSTANCE;
    }

    /** Forward {@code replacement} in place of the original packet. */
    static PacketVerdict rewrite(Object replacement) {
        return new Rewrite(replacement);
    }

    /** The verdict matching a {@link PacketAction}: {@code PASS} is {@link #pass()}, {@code CANCEL} is {@link #cancel()}. */
    static PacketVerdict from(PacketAction action) {
        Objects.requireNonNull(action, "action");
        return action.cancels() ? cancel() : pass();
    }

    /** {@code true} if this verdict cancels (drops) the packet. */
    default boolean cancels() {
        return this instanceof Cancel;
    }

    /** The replacement packet if this is a {@link Rewrite}, otherwise {@code null}. */
    default @Nullable Object replacement() {
        return this instanceof Rewrite rewrite ? rewrite.replacement() : null;
    }

    /** The "let it through unchanged" verdict; a singleton because it carries no state. */
    final class Pass implements PacketVerdict {
        private static final Pass INSTANCE = new Pass();

        private Pass() {}
    }

    /** The "drop it" verdict; a singleton because it carries no state. */
    final class Cancel implements PacketVerdict {
        private static final Cancel INSTANCE = new Cancel();

        private Cancel() {}
    }

    /** The "forward this replacement instead" verdict, carrying the new packet to write downstream. */
    record Rewrite(Object replacement) implements PacketVerdict {
        public Rewrite {
            Objects.requireNonNull(replacement, "replacement");
        }
    }
}
