package com.uxplima.uxmlib.command.annotation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmlib.text.message.LocaleSource;
import org.jspecify.annotations.Nullable;

/**
 * A registry of {@link ParamResolver}s, keyed by the Java parameter type they handle. An instance is
 * passed to {@link AnnotatedCommands} so a plugin can teach the command DSL new argument types without any
 * global mutable state. {@link #withDefaults()} returns a registry pre-loaded with resolvers for the
 * primitives and common Bukkit types (player, world, material, enums, UUID); add your own with
 * {@link #register}.
 */
public final class ParamResolvers {

    private final Map<Class<?>, ParamResolver<?>> byType = new HashMap<>();
    private final List<ParamResolver.Factory> factories = new ArrayList<>();
    private final Map<Class<?>, List<ParameterValidator<?>>> validatorsByType = new HashMap<>();
    private final Map<Class<?>, ContextParameter<?>> contextByType = new HashMap<>();
    private final Map<String, SuggestionSource> suggestionSources = new HashMap<>();
    private final List<CommandCondition> conditions = new ArrayList<>();
    private Cooldowns cooldowns = new Cooldowns();
    private Replacers replacers = Replacers.none();
    private CommandMessages messages = CommandMessages.english();
    private LocaleSource locales = LocaleSource.ofDefault(Locale.ENGLISH);

    private ParamResolvers() {}

    /** An empty registry. Usually you want {@link #withDefaults()} instead. */
    public static ParamResolvers empty() {
        return new ParamResolvers();
    }

    /** A registry pre-loaded with the built-in resolvers (primitives, player, world, material, enum, uuid). */
    public static ParamResolvers withDefaults() {
        ParamResolvers resolvers = new ParamResolvers();
        BuiltinResolvers.installInto(resolvers);
        CollectionResolvers.installInto(resolvers);
        ContextParameters.installInto(resolvers);
        return resolvers;
    }

    /** Register {@code resolver} for parameters of {@code type}. Returns this for chaining. */
    public <T> ParamResolvers register(Class<T> type, ParamResolver<T> resolver) {
        byType.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(resolver, "resolver"));
        return this;
    }

    /**
     * Register a {@link ParamResolver.Factory} that derives a resolver from a parameter's full generic type,
     * declining (returning {@code null}) for types it does not handle so the next factory is tried. Factories
     * run after the direct per-type registrations, in registration order; this is how the composing
     * {@code List<T>}/{@code Optional<T>} resolvers compose over an element resolver. Returns this for chaining.
     */
    public ParamResolvers factory(ParamResolver.Factory factory) {
        factories.add(Objects.requireNonNull(factory, "factory"));
        return this;
    }

    /**
     * Register a post-resolve {@code validator} for parameters of {@code type}. Several validators may share
     * a type; each runs against the resolved value and rejects bad input by throwing
     * {@link IllegalArgumentException}. Returns this for chaining.
     */
    public <T> ParamResolvers validate(Class<T> type, ParameterValidator<T> validator) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(validator, "validator");
        validatorsByType.computeIfAbsent(type, ignored -> new ArrayList<>()).add(validator);
        return this;
    }

    /**
     * Register a tab-completion {@code source} under {@code key}, referenced from an {@code @Arg} parameter via
     * {@code @}{@link SuggestUsing}. Unlike {@code @}{@link SuggestWith} (which reflects a no-arg provider
     * class), the source is a fully-constructed instance, so it can close over a consumer's ports. Replaces any
     * source already registered under that key. Returns this for chaining.
     */
    public ParamResolvers suggestions(String key, SuggestionSource source) {
        suggestionSources.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(source, "source"));
        return this;
    }

    /** The {@link SuggestionSource} registered under {@code key}, or {@code null} if none is. */
    @Nullable SuggestionSource suggestionSource(String key) {
        return suggestionSources.get(key);
    }

    /**
     * Register a {@code provider} that injects a non-{@code @Arg} parameter of {@code type} from the command
     * context. Replaces any provider already registered for that exact type. Returns this for chaining.
     */
    public <T> ParamResolvers context(Class<T> type, ContextParameter<T> provider) {
        contextByType.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(provider, "provider"));
        return this;
    }

    /** Register a pre-execute {@code condition} run before every branch's handler. Returns this for chaining. */
    public ParamResolvers condition(CommandCondition condition) {
        conditions.add(Objects.requireNonNull(condition, "condition"));
        return this;
    }

    /**
     * Register an {@link AnnotationReplacer} that rewrites the custom annotation {@code type} into core
     * annotation(s) at registration. The whole build — model reflection, the implicit permission/cooldown/
     * player-only gates, help, suggestions, and argument validation — then reads the rewritten (effective)
     * annotations, so a consumer's shorthand is honoured everywhere a core annotation would be. Replaces any
     * replacer already registered for that exact type. Returns this for chaining.
     */
    public <A extends java.lang.annotation.Annotation> ParamResolvers replacer(
            Class<A> type, AnnotationReplacer<A> replacer) {
        replacers = replacers.with(Objects.requireNonNull(type, "type"), Objects.requireNonNull(replacer, "replacer"));
        return this;
    }

    /**
     * Use {@code cooldowns} as the store backing {@code @}{@link
     * com.uxplima.uxmlib.command.annotation.annotations.Cooldown} for branches built with this registry.
     * Pass a shared instance to make several registrations share one set of windows, or one with a
     * controllable clock in tests. Defaults to a fresh wall-clock store. Returns this for chaining.
     */
    public ParamResolvers cooldowns(Cooldowns cooldowns) {
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        return this;
    }

    /** The cooldown store backing {@code @Cooldown} for branches built with this registry. */
    Cooldowns cooldowns() {
        return cooldowns;
    }

    /** The registered annotation replacers; the empty registry when none was added. */
    Replacers replacers() {
        return replacers;
    }

    /**
     * Answer senders through {@code messages} instead of the built-in English, for branches built with this
     * registry. Pair it with {@link #locales(LocaleSource)} so each sender is answered in their own language.
     * Returns this for chaining.
     */
    public ParamResolvers messages(CommandMessages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
        return this;
    }

    /** The message source the command layer speaks through; the built-in English when none was set. */
    CommandMessages messages() {
        return messages;
    }

    /**
     * Resolve the locale each sender is answered in through {@code locales}. Defaults to a source that reads
     * a player's own client locale and falls back to English for the console. Returns this for chaining.
     */
    public ParamResolvers locales(LocaleSource locales) {
        this.locales = Objects.requireNonNull(locales, "locales");
        return this;
    }

    /** The locale source; a player's client locale with an English fallback when none was set. */
    LocaleSource locales() {
        return locales;
    }

    /** Whether some resolver handles {@code type} (directly, or as an enum). */
    boolean supports(Class<?> type) {
        return resolverFor(type) != null;
    }

    /** Whether some resolver handles {@code param}, honouring its full generic type for composing factories. */
    boolean supports(java.lang.reflect.Parameter param) {
        return resolverFor(param.getType(), param.getParameterizedType()) != null;
    }

    /** The validators registered for {@code type}, in registration order; empty when none. */
    List<ParameterValidator<?>> validatorsFor(Class<?> type) {
        List<ParameterValidator<?>> found = validatorsByType.get(type);
        return found == null ? List.of() : found;
    }

    /** Whether a context provider is registered for the exact {@code type}. */
    boolean hasContext(Class<?> type) {
        return contextByType.containsKey(type);
    }

    /** The context provider for the exact {@code type}, or {@code null} if none is registered. */
    @Nullable ContextParameter<?> contextFor(Class<?> type) {
        return contextByType.get(type);
    }

    /** The pre-execute conditions, in registration order. */
    List<CommandCondition> conditions() {
        return conditions;
    }

    /** The resolver for {@code type}, or {@code null} if none is registered. Enums share one resolver. */
    @Nullable ParamResolver<?> resolverFor(Class<?> type) {
        return resolverFor(type, type);
    }

    /**
     * The resolver for a parameter whose erased type is {@code rawType} and full generic type is
     * {@code genericType}, or {@code null} if none applies. Direct registrations and enums win first; only
     * then are the {@link ParamResolver.Factory factories} consulted in registration order, so a composing
     * {@code List<T>}/{@code Optional<T>} factory can read the element type off {@code genericType}.
     */
    @Nullable ParamResolver<?> resolverFor(Class<?> rawType, java.lang.reflect.Type genericType) {
        ParamResolver<?> direct = byType.get(rawType);
        if (direct != null) {
            return direct;
        }
        if (rawType.isEnum()) {
            return BuiltinResolvers.enumResolver(rawType);
        }
        for (ParamResolver.Factory factory : factories) {
            ParamResolver<?> made = factory.create(rawType, genericType, this);
            if (made != null) {
                return made;
            }
        }
        return null;
    }
}
