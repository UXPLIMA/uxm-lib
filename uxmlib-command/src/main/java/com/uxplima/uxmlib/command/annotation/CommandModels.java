package com.uxplima.uxmlib.command.annotation;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.CommandPriority;
import com.uxplima.uxmlib.command.annotation.annotations.Flag;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import com.uxplima.uxmlib.command.annotation.annotations.Switch;

/**
 * The reflective scan: reads an {@code @}{@link Command} handler into a {@link CommandModel}: the
 * platform-neutral IR a renderer walks. Each {@code @}{@link Subcommand} method becomes a {@link BranchModel}
 * carrying its literal path, permission, ordered positional {@code @}{@link Arg}s and {@code @}{@link
 * Flag}/{@code @}{@link Switch} entries. Malformed handlers (an unsupported argument type, a required
 * argument after an optional one, a non-trailing greedy, a parameter that is neither injectable nor a flag
 * nor an {@code @Arg}) fail here with a {@link CommandParseException}, before anything touches Brigadier.
 * This is the only place reflection meets the model; the renderer never sees a {@link Method}.
 */
final class CommandModels {

    private CommandModels() {}

    /** Reflect {@code handler} into its command model using {@code resolvers} for argument and flag types. */
    static CommandModel reflect(Object handler, ParamResolvers resolvers) {
        Class<?> type = handler.getClass();
        Replacers replacers = resolvers.replacers();
        AnnotatedView classView = AnnotatedView.of(type, replacers);
        Command command = classView.get(Command.class);
        if (command == null) {
            throw new CommandParseException(type.getName() + " is not annotated with @Command");
        }
        List<Method> methods = orderedSubcommands(type);
        if (methods.isEmpty()) {
            throw new CommandParseException(type.getName() + " has no @Subcommand methods");
        }
        List<BranchModel> branches = new ArrayList<>();
        for (Method method : methods) {
            branches.add(branchOf(method, classView, resolvers, replacers));
        }
        return new CommandModel(handler, command, classView, classView.get(Permission.class), branches);
    }

    private static BranchModel branchOf(
            Method method, AnnotatedView classView, ParamResolvers resolvers, Replacers replacers) {
        AnnotatedView methodView = AnnotatedView.of(method, replacers);
        List<AnnotatedView> paramViews = paramViews(method, replacers);
        validateSignature(method, resolvers, paramViews);
        List<ArgBinder.ParamArg> args = argParameters(method, resolvers, paramViews);
        List<FlagModel> flags = flagParameters(method, resolvers, paramViews);
        checkParamOrder(method, args, !flags.isEmpty(), paramViews);
        String path = effectiveSubcommand(method, methodView).value().trim();
        return new BranchModel(
                method,
                methodView,
                classView,
                path,
                methodView.get(Permission.class),
                args,
                flags,
                priorityOf(methodView));
    }

    /** The effective annotation view of each parameter of {@code method}, index-aligned with its parameters. */
    private static List<AnnotatedView> paramViews(Method method, Replacers replacers) {
        List<AnnotatedView> views = new ArrayList<>();
        for (Parameter param : method.getParameters()) {
            views.add(AnnotatedView.of(param, replacers));
        }
        return views;
    }

    /** The effective {@code @Subcommand} of {@code method}; the scan only reaches a method that has one. */
    private static Subcommand effectiveSubcommand(Method method, AnnotatedView methodView) {
        Subcommand sub = methodView.get(Subcommand.class);
        if (sub == null) {
            throw new CommandParseException(method.getName() + " lost its @Subcommand");
        }
        return sub;
    }

    /** The explicit {@code @CommandPriority} of a branch, or empty when it declares none. */
    private static java.util.OptionalInt priorityOf(AnnotatedView methodView) {
        CommandPriority priority = methodView.get(CommandPriority.class);
        return priority == null ? java.util.OptionalInt.empty() : java.util.OptionalInt.of(priority.value());
    }

    private static List<Method> orderedSubcommands(Class<?> type) {
        List<Method> methods = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Subcommand.class)) {
                method.setAccessible(true);
                methods.add(method);
            }
        }
        // Longer literal paths first so "admin reload" is attached before a bare "" root executor; this keeps
        // node attachment order deterministic regardless of reflection's method ordering. Where two branches
        // share a path (overlapping overloads), the lower @CommandPriority attaches first so Brigadier, which
        // tries sibling argument nodes in attachment order, runs the higher-priority overload on ambiguity.
        methods.sort(Comparator.comparingInt((Method m) -> pathLength(m))
                .reversed()
                .thenComparingInt(CommandModels::priorityRank));
        return methods;
    }

    private static int pathLength(Method method) {
        return method.getAnnotation(Subcommand.class).value().length();
    }

    /** The sort rank of a branch's priority: its value, or {@link Integer#MAX_VALUE} when it declares none. */
    private static int priorityRank(Method method) {
        CommandPriority priority = method.getAnnotation(CommandPriority.class);
        return priority == null ? Integer.MAX_VALUE : priority.value();
    }

    private static void validateSignature(Method method, ParamResolvers resolvers, List<AnnotatedView> paramViews) {
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            Parameter param = params[i];
            AnnotatedView view = paramViews.get(i);
            if (isFlagParam(view)) {
                validateFlagParam(method, param, view, resolvers);
                continue;
            }
            boolean injectable = isInjectable(resolvers, param, view);
            if (!injectable && !view.isPresent(Arg.class)) {
                throw new CommandParseException("parameter '" + param.getName() + "' of " + method.getName()
                        + " must be @Arg-annotated, a @Flag/@Switch, or a Sender/CommandSourceStack/CommandSender");
            }
            if (!injectable && !resolvers.supports(param)) {
                throw new CommandParseException(
                        "no resolver for @Arg type " + param.getType().getName() + " on " + method.getName());
            }
        }
    }

    private static void validateFlagParam(
            Method method, Parameter param, AnnotatedView view, ParamResolvers resolvers) {
        if (view.isPresent(Switch.class)) {
            Class<?> t = param.getType();
            if (t != boolean.class && t != Boolean.class) {
                throw new CommandParseException("@Switch parameter '" + flagName(view, param) + "' of "
                        + method.getName() + " must be a boolean");
            }
            return;
        }
        ParamResolver<?> resolver = resolvers.resolverFor(param.getType());
        if (resolver == null) {
            throw new CommandParseException(
                    "no resolver for @Flag type " + param.getType().getName() + " on " + method.getName());
        }
        if (resolver.nativeArgument()) {
            // A value flag resolves one token through a standalone Brigadier dispatcher (TokenResolution), which
            // has no Paper registry/build context; a native type (player/world/location/sound/...) cannot parse
            // there and would throw on a live server. Reject it here so the failure is a clear startup message.
            throw new CommandParseException("@Flag type " + param.getType().getName() + " on " + method.getName()
                    + " is a native argument type (player/world/location/sound/...); a flag value cannot use one."
                    + " Take it as a positional @Arg instead.");
        }
    }

    private static boolean isInjectable(ParamResolvers resolvers, Parameter param, AnnotatedView view) {
        return !view.isPresent(Arg.class) && resolvers.hasContext(param.getType());
    }

    private static boolean isFlagParam(AnnotatedView view) {
        return view.isPresent(Flag.class) || view.isPresent(Switch.class);
    }

    private static List<ArgBinder.ParamArg> argParameters(
            Method method, ParamResolvers resolvers, List<AnnotatedView> paramViews) {
        Parameter[] params = method.getParameters();
        List<ArgBinder.ParamArg> args = new ArrayList<>();
        for (int i = 0; i < params.length; i++) {
            Parameter param = params[i];
            AnnotatedView view = paramViews.get(i);
            Arg arg = view.get(Arg.class);
            if (arg == null) {
                continue;
            }
            ParamResolver<?> resolver = resolvers.resolverFor(param.getType(), param.getParameterizedType());
            if (resolver == null) {
                throw new CommandParseException(
                        "no resolver for @Arg type " + param.getType().getName() + " on " + method.getName());
            }
            args.add(new ArgBinder.ParamArg(arg.value(), arg, resolver, param, view));
        }
        return args;
    }

    private static List<FlagModel> flagParameters(
            Method method, ParamResolvers resolvers, List<AnnotatedView> paramViews) {
        Parameter[] params = method.getParameters();
        List<FlagModel> flags = new ArrayList<>();
        for (int i = 0; i < params.length; i++) {
            Parameter param = params[i];
            AnnotatedView view = paramViews.get(i);
            Switch sw = view.get(Switch.class);
            if (sw != null) {
                flags.add(FlagModel.switchFlag(sw.value(), sw.shorthand(), param));
                continue;
            }
            Flag flag = view.get(Flag.class);
            if (flag != null) {
                flags.add(FlagModel.valueFlag(
                        flag.value(), flag.shorthand(), resolverFor(method, resolvers, param), param));
            }
        }
        return flags;
    }

    private static ParamResolver<?> resolverFor(Method method, ParamResolvers resolvers, Parameter param) {
        ParamResolver<?> resolver = resolvers.resolverFor(param.getType());
        if (resolver == null) {
            throw new CommandParseException(
                    "no resolver for @Flag type " + param.getType().getName() + " on " + method.getName());
        }
        return resolver;
    }

    private static String flagName(AnnotatedView view, Parameter param) {
        Switch sw = view.get(Switch.class);
        if (sw != null) {
            return sw.value();
        }
        Flag flag = view.get(Flag.class);
        return flag != null ? flag.value() : param.getName();
    }

    /**
     * Reject argument and flag orderings Brigadier could not represent: a required argument after an optional
     * one, a greedy positional that is not last, a greedy positional alongside flags (the flags node is also
     * greedy, so two greedy siblings would be ambiguous), and any positional {@code @Arg} declared after a
     * flag (flags are consumed by a single greedy trailing node, so they must come last).
     */
    private static void checkParamOrder(
            Method method, List<ArgBinder.ParamArg> args, boolean hasFlags, List<AnnotatedView> paramViews) {
        boolean seenOptional = false;
        for (int i = 0; i < args.size(); i++) {
            ArgBinder.ParamArg pa = args.get(i);
            if (seenOptional && !pa.arg().optional()) {
                throw new CommandParseException(
                        "a required argument cannot follow an optional one on " + method.getName());
            }
            seenOptional = seenOptional || pa.arg().optional();
            boolean consumesRest =
                    pa.arg().greedy() || isCollection(pa.parameter().getType());
            if (consumesRest && i != args.size() - 1) {
                throw new CommandParseException("only the last argument may be greedy on " + method.getName());
            }
            if (consumesRest && hasFlags) {
                throw new CommandParseException(
                        "a greedy argument cannot be combined with @Flag/@Switch on " + method.getName());
            }
        }
        checkFlagsLast(method, paramViews);
    }

    /** Whether a parameter type is one of the composing collection types, which consume a greedy trailing node. */
    private static boolean isCollection(Class<?> type) {
        return type == java.util.List.class || type == java.util.Optional.class || type.isArray();
    }

    private static void checkFlagsLast(Method method, List<AnnotatedView> paramViews) {
        boolean seenFlag = false;
        for (AnnotatedView view : paramViews) {
            if (isFlagParam(view)) {
                seenFlag = true;
            } else if (seenFlag && view.isPresent(Arg.class)) {
                throw new CommandParseException(
                        "a positional @Arg cannot follow a @Flag/@Switch on " + method.getName());
            }
        }
    }
}
