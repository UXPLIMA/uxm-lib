package com.uxplima.uxmlib.menu.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.uxplima.uxmlib.menu.render.ValueSpot;

/**
 * Expands {@code %argument_<name>%} tokens inside an action ref's argument values from the arguments a menu was opened
 * with. Rendered menu TEXT already gets this through {@link com.uxplima.uxmlib.menu.render.ItemRenderer}; this is the
 * same rule for the ACTION side, so a menu opened with {@code amount=5} whose confirm button runs {@code give-
 * money:%argument_amount%} actually gives five. It deliberately touches only the {@code argument_} tokens and leaves
 * every other {@code %token%} verbatim, so the downstream registry / PlaceholderAPI / MiniMessage handling is
 * unchanged.
 *
 * <p>Pure and allocation-conscious: when the open carried no arguments there is nothing to expand, so the original map
 * is returned unchanged rather than copied: the common case (a menu opened without a command) pays nothing.
 *
 * <p>Public because two engine paths in different packages reach it: the click path ({@code MenuListener}, this
 * package) and the open path ({@code Menus}, the parent package) both expand an action ref's arguments the same way
 * before dispatching an open-action. It is a pure static helper with no state, so widening it leaks nothing.
 */
public final class ActionArguments {

    /** A single {@code %token%} placeholder; {@code group(1)} is the bare token name. Mirrors the renderer's pattern. */
    private static final Pattern PLACEHOLDER = Pattern.compile("%(\\w+)%");

    /** Prefix marking a token as a typed command argument resolved from the open arguments rather than the registry. */
    private static final String ARGUMENT_PREFIX = "argument_";

    private ActionArguments() {}

    /**
     * The action arguments with every {@code %argument_<name>%} token in a value replaced by the matching open argument
     * (an unknown name yields empty, matching the renderer). Returns {@code args} unchanged when {@code arguments} is
     * empty (the identity fast-path) so a menu opened without a command allocates nothing.
     */
    public static Map<String, String> resolve(Map<String, String> args, Map<String, String> arguments) {
        if (arguments.isEmpty()) {
            return args;
        }
        Map<String, String> resolved = new LinkedHashMap<>(args.size());
        args.forEach((key, value) -> resolved.put(key, substitute(value, arguments)));
        return resolved;
    }

    /**
     * The action arguments with every {@code %argument_<name>%} token expanded from {@code ctx}'s open arguments and
     * every {@code %name%} its local placeholders define expanded from those, both in one pass over each value. A
     * token neither answers is left verbatim, for the registry, PlaceholderAPI or MiniMessage downstream.
     *
     * <p>One pass, because the values are typed by players: an open argument, a prompt's {@code %input%}, a form field,
     * a dropped item's name. Expanding the arguments and then the locals read each argument a second time, so a player
     * who typed {@code %drag_name%} or the name of any local got that value in place of what they typed. Values go in
     * as written, since an action reads them as data, and a value is never read again. Returns {@code args} unchanged
     * when there is nothing to expand.
     */
    public static Map<String, String> resolve(Map<String, String> args, MenuContext ctx) {
        return fill(args, ctx, (written, at, typed) -> typed);
    }

    /**
     * The action arguments filled as {@link #resolve(Map, MenuContext)} fills them, with every value a player typed
     * put through {@code typed} first: an argument the opener did not vouch for as markup, a prompt's answer, a form
     * field, a dropped item's name. A value an operator wrote, and an argument vouched for as markup, go in as written.
     *
     * <p>This is for an action that sends its argument in a format of its own, where only the action knows what keeps
     * a typed value inside it: a JSON component needs it as JSON string content, a legacy line needs its colour codes
     * taken out. The fill is one pass, so what {@code typed} returns is never read again for a token.
     */
    public static Map<String, String> resolve(Map<String, String> args, MenuContext ctx, UnaryOperator<String> typed) {
        Objects.requireNonNull(typed, "typed");
        return fill(args, ctx, (written, at, value) -> typed.apply(value));
    }

    /**
     * The action arguments filled as {@link #resolve(Map, MenuContext)} fills them, for an action that sends its
     * argument as a line of words rather than reading it as data: {@code message} is one. A value a player typed (an
     * argument the opener did not vouch for as markup, a prompt's answer, a form field, a dropped item's name) goes in
     * escaped for MiniMessage, or for the quote of the tag argument it sits in, so it reads as what they typed. A value
     * an operator wrote goes in as written.
     */
    public static Map<String, String> resolveAsText(Map<String, String> args, MenuContext ctx) {
        return fill(
                args,
                ctx,
                (written, at, typed) -> ValueSpot.TEXT.at(written, at).fit(typed));
    }

    /** How a value a player typed goes in at position {@code at} of the written value {@code written}. */
    @FunctionalInterface
    private interface TypedFit {
        String fit(String written, int at, String typed);
    }

    private static Map<String, String> fill(Map<String, String> args, MenuContext ctx, TypedFit fit) {
        if (ctx.arguments().isEmpty() && ctx.localPlaceholders().isEmpty()) {
            return args;
        }
        Map<String, String> resolved = new LinkedHashMap<>(args.size());
        args.forEach((key, value) -> resolved.put(key, substituteOnce(value, ctx, fit)));
        return resolved;
    }

    /** Replace each argument token and each token the local placeholders define in {@code value}, in one pass. */
    private static String substituteOnce(String value, MenuContext ctx, TypedFit fit) {
        Map<String, String> arguments = ctx.arguments();
        Map<String, String> locals = ctx.localPlaceholders();
        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1);
            String replacement;
            if (!token.startsWith(ARGUMENT_PREFIX)) {
                String local = locals.get(token);
                if (local == null) {
                    replacement = matcher.group();
                } else {
                    replacement = ctx.typedLocals().contains(token) ? fit.fit(value, matcher.start(), local) : local;
                }
            } else if (arguments.isEmpty()) {
                // An open without arguments has always left the token as written, for whoever reads it next.
                replacement = matcher.group();
            } else {
                String name = token.substring(ARGUMENT_PREFIX.length());
                String typed = arguments.getOrDefault(name, "");
                replacement = ctx.markupArguments().contains(name) ? typed : fit.fit(value, matcher.start(), typed);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Replace only the {@code argument_}-prefixed tokens in {@code value}, leaving every other {@code %token%} verbatim. */
    private static String substitute(String value, Map<String, String> arguments) {
        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1);
            String replacement = token.startsWith(ARGUMENT_PREFIX)
                    ? arguments.getOrDefault(token.substring(ARGUMENT_PREFIX.length()), "")
                    : matcher.group();
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * The action arguments with every bare {@code %name%} token that {@code locals} defines replaced by its value: the
     * same menu-local placeholders (the open spec's {@code placeholders {}} block, plus the item-drag flow's {@code
     * %drag_material%}/{@code %drag_amount%}/{@code %drag_name%}) that the renderer already expands in text. A token
     * the map does not define is left verbatim, so the downstream registry / PlaceholderAPI / MiniMessage handling is
     * unchanged. Returns {@code args} unchanged when {@code locals} is empty (the identity fast-path) so a menu without
     * a local block or a drag exposure allocates nothing and dispatches byte-identically.
     *
     * <p>Run after {@link #resolve(Map, Map)} this reads an argument a player typed a second time, so the engine uses
     * {@link #resolve(Map, MenuContext)}, which does both in one pass.
     */
    public static Map<String, String> resolveLocals(Map<String, String> args, Map<String, String> locals) {
        if (locals.isEmpty()) {
            return args;
        }
        Map<String, String> resolved = new LinkedHashMap<>(args.size());
        args.forEach((key, value) -> resolved.put(key, substituteLocals(value, locals)));
        return resolved;
    }

    /** Replace each {@code %name%} token in {@code value} that {@code locals} defines, leaving every other token verbatim. */
    private static String substituteLocals(String value, Map<String, String> locals) {
        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String replacement = locals.getOrDefault(matcher.group(1), matcher.group());
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
