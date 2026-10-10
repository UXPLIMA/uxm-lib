package com.uxplima.uxmlib.command.annotation.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a command handler. The {@link #name()} is the root label; methods annotated with
 * {@link Subcommand} become its branches. Process a handler instance with
 * {@link com.uxplima.uxmlib.command.annotation.AnnotatedCommands#register}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Command {

    /** The root command label, without a leading slash (e.g. {@code "home"}). */
    String name();

    /** Alternate labels for the root command. */
    String[] aliases() default {};

    /**
     * Labels for the readers of one language, each {@code tag=word}, as {@code tr=arsa}. A word here
     * answers to everyone who types it, the console and Bedrock included, and is kept out of the command
     * list of a player who reads another language. A malformed entry is ignored.
     */
    String[] localizedAliases() default {};

    /** Help text shown by the server. */
    String description() default "";

    /** Whether to auto-generate a {@code help} subcommand listing the visible branches. */
    boolean help() default true;
}
