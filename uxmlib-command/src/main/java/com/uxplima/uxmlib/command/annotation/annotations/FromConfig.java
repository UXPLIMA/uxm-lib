package com.uxplima.uxmlib.command.annotation.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a command handler whose label, aliases and description come from {@code commands.conf}.
 *
 * <p>It replaces {@code @Command} on the class rather than sitting beside it. The two cannot both be there:
 * uxmLib keeps a declared annotation over a produced one, on purpose, so a replacer can never silently
 * overwrite what the author wrote. A handler that declared {@code @Command} would therefore keep its
 * compiled-in name and the file would be ignored, which is the worst of the two outcomes because it looks
 * like it works.
 *
 * <p>{@link #value()} is the key in the file, not the label. The key never changes; the label is what the
 * operator is free to change.
 *
 * <p>On a method it names a branch instead, and the key is read under the {@code subcommands} block of the
 * command the method belongs to:
 *
 * <pre>{@code
 * commands {
 *   auction {
 *     name = "auction"
 *     subcommands {
 *       sell { name = "sat", aliases = ["satis"] }
 *       buy  { enabled = false }
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p>A branch that the file turns off is never built, so the method is dropped from the command tree. The
 * root executor of a command carries no literal, so it keeps a plain {@code @Subcommand("")} and is not
 * named in the file.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface FromConfig {

    /** The key under {@code commands} in {@code commands.conf}. */
    String value();

    /** The fallback label, used when the file names none. Left empty it is {@link #value()} itself. */
    String fallbackName() default "";

    /** Help text shown by the server. */
    String description() default "";
}
