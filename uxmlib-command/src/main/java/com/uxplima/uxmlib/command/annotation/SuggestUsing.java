package com.uxplima.uxmlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.uxplima.uxmlib.command.annotation.annotations.Arg;

/**
 * Drives tab-completion for an {@code @}{@link Arg} parameter from a {@link SuggestionSource} <em>instance</em>
 * registered by key on the {@link ParamResolvers} via {@link ParamResolvers#suggestions(String,
 * SuggestionSource)}. This is the dependency-injection-friendly counterpart of {@code @}{@link SuggestWith}:
 * where {@code @SuggestWith} reflects a provider class through its no-arg constructor (so it cannot carry a
 * consumer's ports), {@code @SuggestUsing} references a fully-constructed provider the consumer built with
 * whatever collaborators it needs and handed to the registry. Both feed the same ordered suggestion
 * decline-chain; a {@code @SuggestUsing} provider, when present, is tried first.
 *
 * <p>The referenced key must be registered on the {@link ParamResolvers} used to build the command, or
 * registration fails fast with a {@link CommandParseException}: a typo never silently disables completion.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface SuggestUsing {

    /** The registry key of the {@link SuggestionSource} instance supplying completions. */
    String value();
}
