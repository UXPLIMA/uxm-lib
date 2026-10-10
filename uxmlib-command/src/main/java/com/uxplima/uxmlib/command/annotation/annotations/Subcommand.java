package com.uxplima.uxmlib.command.annotation.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an executable command branch. The {@link #value()} is the space-separated literal
 * path beneath the root {@link Command} (e.g. {@code "set"} or {@code "admin reload"}); an empty path
 * makes the method the root command's own executor. After the literals, the method's
 * {@link Arg}-annotated parameters become typed Brigadier arguments, and a leading {@code Sender} or
 * {@code CommandSourceStack} parameter is injected.
 *
 * <p>{@link #aliases()} give the branch other spellings of its last literal, in the place the path already
 * names: {@code "admin reload"} with the alias {@code "rl"} also answers to {@code admin rl}. Every spelling
 * runs the same method with the same arguments.
 *
 * <p>{@link #localizedAliases()} are spellings for the readers of one language, each written
 * {@code tag=word}, as {@code tr=sat}. Such a word is shown to, and answered for, the players who read
 * that language and the console, and nobody else.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subcommand {

    /** The literal path beneath the root, space-separated; empty for the root executor. */
    String value() default "";

    /** Other spellings of the last literal of {@link #value()}; a blank one is ignored. */
    String[] aliases() default {};

    /**
     * Spellings of the last literal for the readers of one language, each {@code tag=word} with a language
     * tag such as {@code tr} or {@code pt-BR}. A malformed entry is ignored.
     */
    String[] localizedAliases() default {};

    /** A short description of this branch, shown in the generated help. */
    String description() default "";
}
