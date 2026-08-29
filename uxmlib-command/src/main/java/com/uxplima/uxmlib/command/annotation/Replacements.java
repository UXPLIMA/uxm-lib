package com.uxplima.uxmlib.command.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmlib.command.annotation.annotations.Cooldown;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;

/**
 * Factory methods that build live instances of the core command annotations, for an {@link AnnotationReplacer}
 * to return. A consumer cannot {@code new} an annotation, so this hands back real annotation proxies whose
 * {@code annotationType}/{@code equals}/{@code hashCode}/{@code toString} behave the way the platform expects:
 * the same contract a reflectively-read annotation satisfies. Only the small, attribute-bearing core
 * annotations a replacer would plausibly synthesise are exposed; a marker annotation (no attributes) is built
 * directly from its type with {@link #marker(Class)}.
 */
public final class Replacements {

    private Replacements() {}

    /** A {@code @}{@link Permission} carrying {@code node}. */
    public static Permission permission(String node) {
        Objects.requireNonNull(node, "node");
        return of(Permission.class, Map.of("value", node));
    }

    /** A {@code @}{@link Cooldown} of the given human {@code duration} (e.g. {@code "30s"}). */
    public static Cooldown cooldown(String duration) {
        Objects.requireNonNull(duration, "duration");
        return of(Cooldown.class, Map.of("value", duration));
    }

    /**
     * A marker annotation of {@code type} that declares no attributes (such as {@code @PlayerOnly} or
     * {@code @Secret}). Rejects an annotation type that does declare attributes, since those need explicit
     * values supplied through {@link #of(Class, Map)}.
     */
    public static <A extends Annotation> A marker(Class<A> type) {
        Objects.requireNonNull(type, "type");
        if (type.getDeclaredMethods().length != 0) {
            throw new IllegalArgumentException(type.getName() + " declares attributes; it is not a marker annotation");
        }
        return of(type, Map.of());
    }

    /**
     * A live instance of annotation {@code type} whose attributes are taken from {@code values} (an attribute
     * not present falls back to its declared default). Every supplied key must name a declared attribute and
     * every attribute lacking a default must be supplied, mirroring how the compiler would reject a malformed
     * use of the annotation. Public so a consumer can synthesise an annotation this factory does not name.
     */
    @SuppressWarnings("unchecked")
    public static <A extends Annotation> A of(Class<A> type, Map<String, Object> values) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(values, "values");
        Map<String, Object> resolved = resolve(type, values);
        return (A) Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[] {type}, new AnnotationInvocationHandler(type, resolved));
    }

    /** Merge supplied {@code values} over each attribute's declared default, validating names and presence. */
    private static Map<String, Object> resolve(Class<? extends Annotation> type, Map<String, Object> values) {
        Map<String, Object> resolved = new ConcurrentHashMap<>();
        for (Method attribute : type.getDeclaredMethods()) {
            Object supplied = values.get(attribute.getName());
            Object value = supplied != null ? supplied : attribute.getDefaultValue();
            if (value == null) {
                throw new IllegalArgumentException(
                        "missing value for required attribute '" + attribute.getName() + "' of " + type.getName());
            }
            resolved.put(attribute.getName(), value);
        }
        rejectUnknownKeys(type, values, resolved);
        return resolved;
    }

    private static void rejectUnknownKeys(
            Class<? extends Annotation> type, Map<String, Object> values, Map<String, Object> resolved) {
        for (String key : values.keySet()) {
            if (!resolved.containsKey(key)) {
                throw new IllegalArgumentException("'" + key + "' is not an attribute of " + type.getName());
            }
        }
    }

    /** The reflective backing of a synthesised annotation: attribute reads plus the {@link Annotation} contract. */
    private record AnnotationInvocationHandler(Class<? extends Annotation> type, Map<String, Object> values)
            implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object @org.jspecify.annotations.Nullable [] args) {
            String name = method.getName();
            return switch (name) {
                case "annotationType" -> type;
                case "hashCode" -> hash();
                case "toString" -> render();
                case "equals" -> equalsAnnotation(args == null ? null : args[0]);
                default -> attribute(name);
            };
        }

        private Object attribute(String name) {
            Object value = values.get(name);
            if (value == null) {
                throw new IllegalStateException("no value for attribute '" + name + "' of " + type.getName());
            }
            return value;
        }

        /** The {@link Annotation#hashCode()} contract: the sum of each {@code (127 * name) ^ value} hash. */
        private int hash() {
            int sum = 0;
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                sum += (127 * entry.getKey().hashCode()) ^ valueHash(entry.getValue());
            }
            return sum;
        }

        private static int valueHash(Object value) {
            return value.getClass().isArray() ? Arrays.deepHashCode(new Object[] {value}) : value.hashCode();
        }

        private boolean equalsAnnotation(@org.jspecify.annotations.Nullable Object other) {
            if (!type.isInstance(other) || other == null) {
                return false;
            }
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                if (!Objects.deepEquals(entry.getValue(), read(other, entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }

        private @org.jspecify.annotations.Nullable Object read(Object other, String name) {
            try {
                return type.getMethod(name).invoke(other);
            } catch (ReflectiveOperationException unreadable) {
                return null;
            }
        }

        private String render() {
            StringBuilder text = new StringBuilder("@").append(type.getName()).append('(');
            boolean first = true;
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                if (!first) {
                    text.append(", ");
                }
                text.append(entry.getKey()).append('=').append(entry.getValue());
                first = false;
            }
            return text.append(')').toString();
        }
    }
}
