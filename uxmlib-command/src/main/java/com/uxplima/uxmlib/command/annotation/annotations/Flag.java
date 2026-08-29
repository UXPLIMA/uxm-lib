package com.uxplima.uxmlib.command.annotation.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a handler parameter as a <em>value flag</em>: a position-independent, named argument given on the
 * command line as {@code --name <value>} (or {@code -x <value>} via the {@link #shorthand()}). Unlike an
 * {@code @}{@link Arg}, a flag is not tied to a slot in the literal spine: flags may appear in any order
 * after the positional arguments. The parameter's Java type drives how the value is parsed, exactly like an
 * {@code @Arg}. A flag is optional; when omitted the parameter is filled with its type's zero value (or
 * {@code null} for a reference type), so prefer a boxed type or check for absence in the handler.
 *
 * <p>All flag parameters of a branch must come after its positional {@code @Arg}s: validated at
 * registration, mirroring the trailing-optional rule.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Flag {

    /** The long flag name, given as {@code --name} (without the leading dashes). */
    String value();

    /** A single-character shorthand given as {@code -x}, or {@code 0} (the default) for no shorthand. */
    char shorthand() default 0;
}
