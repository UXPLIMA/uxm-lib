package com.uxplima.uxmlib.packet;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Reads a server static field by its Mojang-mapped name. The data-watcher accessors a metadata packet needs
 * ({@code EntityDataAccessor} objects on {@code Display}/{@code TextDisplay} and friends) are package-private
 * static fields, so a tiny guarded reflective read is the only way to reach them. Reading the accessor object
 * rather than hard-coding its network id keeps us off the volatile integer indices.
 *
 * <p>This helper is stateless: it does the read once per call and fails loudly. Callers that need the value
 * across many packets cache it themselves (assign it to a {@code final} field at construction), which keeps the
 * reflection off every hot path without this class holding any mutable state.
 */
public final class Reflect {

    private Reflect() {}

    /**
     * Resolve the first available class from candidate FQCNs, or return null if none are available on the classpath.
     */
    public static @Nullable Class<?> findClass(String... candidateNames) {
        for (String candidate : candidateNames) {
            try {
                return Class.forName(candidate);
            } catch (ClassNotFoundException ignored) {
                // Try next candidate
            }
        }
        return null;
    }

    /**
     * Read the static field {@code field} declared on {@code owner} and return it as {@code T}.
     *
     * @throws IllegalStateException if the field is missing or unreadable on this server (a mapping mismatch),
     *     naming {@code owner#field} so the failure is obvious rather than a silently broken packet
     */
    // unchecked: the field's declared type matches the requested element type at the call site.
    // TypeParameterUnusedInFormals: the inferred-from-assignment return is the API: callers store the accessor
    // straight into a typed field (e.g. EntityDataAccessor<Byte>) without an explicit cast, as the renderers do.
    @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
    public static <T> T accessor(Class<?> owner, String field) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(field, "field");
        try {
            var declared = owner.getDeclaredField(field);
            declared.setAccessible(true);
            return (T) Objects.requireNonNull(declared.get(null), field);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException(
                    "Static field " + owner.getName() + "#" + field + " is unavailable on this server", e);
        }
    }

    /**
     * Read the static field {@code field} declared on the first available class among {@code candidateClassNames}.
     *
     * @throws IllegalStateException if none of the candidate classes exist or the field is unreadable
     */
    @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
    public static <T> T accessorByClass(String field, String... candidateClassNames) {
        Class<?> owner = findClass(candidateClassNames);
        if (owner == null) {
            throw new IllegalStateException("None of the candidate classes [" + String.join(", ", candidateClassNames)
                    + "] exist on this server");
        }
        return accessor(owner, field);
    }
}
