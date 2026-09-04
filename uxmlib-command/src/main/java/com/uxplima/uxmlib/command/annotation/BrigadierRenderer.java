package com.uxplima.uxmlib.command.annotation;

import java.util.List;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Cmd;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.scheduler.Scheduler;
import org.jspecify.annotations.Nullable;

/**
 * Walks a {@link CommandModel} and emits the Brigadier {@link LiteralCommandNode} the registrar registers.
 * This is the <em>only</em> class that touches Brigadier builders: the reflective scan produces the model,
 * and this translates model to nodes: building the literal spine innermost-out, nesting positional
 * arguments innermost-first (an optional one also ends the command so the shorter path dispatches), and
 * ending a flagged branch with one greedy {@link FlagArgumentType} node. Decoupling reflect &rarr; model
 * &rarr; render is what lets flags, server-less tests, and a future second surface target the model.
 */
final class BrigadierRenderer {

    private final ParamResolvers resolvers;
    private final Scheduler scheduler;

    BrigadierRenderer(ParamResolvers resolvers, Scheduler scheduler) {
        this.resolvers = resolvers;
        this.scheduler = scheduler;
    }

    /** Render {@code model} into its registrable Brigadier tree. */
    LiteralCommandNode<CommandSourceStack> render(CommandModel model) {
        Command command = model.command();
        LiteralArgumentBuilder<CommandSourceStack> root = Cmd.literal(command.name());
        Permission classPermission = model.classPermission();
        if (classPermission != null) {
            root.requires(Cmd.permission(classPermission.value()));
        }
        for (BranchModel branch : model.branches()) {
            attachBranch(root, model.handler(), branch, command.name());
        }
        if (command.help()) {
            root.then(HelpRenderer.helpLiteral(command.name(), model.branches(), resolvers));
        }
        return root.build();
    }

    private void attachBranch(
            LiteralArgumentBuilder<CommandSourceStack> root, Object handler, BranchModel branch, String rootName) {
        String path = branch.path();
        String commandPath = path.isEmpty() ? rootName : rootName + ' ' + path;
        com.mojang.brigadier.Command<CommandSourceStack> executor = CommandExecutors.executorFor(
                handler, branch, branch.args(), branch.flags(), resolvers, commandPath, scheduler);
        ArgChain chain = buildArgChain(branch, executor);

        String[] literals = branch.literals();
        if (literals.length == 0) {
            attachRoot(root, branch, chain, executor);
            return;
        }
        attachSpine(root, branch, literals, chain, executor);
        for (String alias : branch.aliases()) {
            String[] spelling = literals.clone();
            spelling[spelling.length - 1] = alias;
            // A builder is spent once it is built, so every spelling gets its own chain of nodes. They all
            // carry the one executor, so an alias runs the method the declared path runs.
            attachSpine(root, branch, spelling, buildArgChain(branch, executor), executor);
        }
    }

    private void attachSpine(
            LiteralArgumentBuilder<CommandSourceStack> root,
            BranchModel branch,
            String[] literals,
            ArgChain chain,
            com.mojang.brigadier.Command<CommandSourceStack> executor) {
        ArgumentBuilder<CommandSourceStack, ?> tail = Cmd.literal(literals[literals.length - 1]);
        applyChain(tail, chain, executor);
        for (int i = literals.length - 2; i >= 0; i--) {
            LiteralArgumentBuilder<CommandSourceStack> parent = Cmd.literal(literals[i]);
            parent.then(tail);
            tail = parent;
        }
        applyPermission(tail, branch.permission());
        root.then(tail);
    }

    private static void attachRoot(
            LiteralArgumentBuilder<CommandSourceStack> root,
            BranchModel branch,
            ArgChain chain,
            com.mojang.brigadier.Command<CommandSourceStack> executor) {
        applyPermission(root, branch.permission());
        applyChain(root, chain, executor);
    }

    /** Attach a branch's argument chain under {@code builder}, making it executable as the chain allows. */
    private static void applyChain(
            ArgumentBuilder<CommandSourceStack, ?> builder,
            ArgChain chain,
            com.mojang.brigadier.Command<CommandSourceStack> executor) {
        if (chain.firstArg != null) {
            builder.then(chain.firstArg);
            if (chain.prefixExecutable) {
                builder.executes(executor); // the first argument (or the flags node) is optional, so this runs too
            }
        } else {
            builder.executes(executor);
        }
    }

    /**
     * The outermost argument builder of a branch (or {@code null} when it takes no arguments and no flags)
     * and whether the node above it must also end the command (a leading optional argument, or a branch that
     * is all flags: flags are always optional).
     */
    private record ArgChain(
            @Nullable RequiredArgumentBuilder<CommandSourceStack, ?> firstArg, boolean prefixExecutable) {}

    private ArgChain buildArgChain(BranchModel branch, com.mojang.brigadier.Command<CommandSourceStack> executor) {
        List<ArgBinder.ParamArg> args = branch.args();
        RequiredArgumentBuilder<CommandSourceStack, ?> flagsNode = branch.hasFlags() ? flagsNode(branch) : null;
        if (flagsNode != null) {
            flagsNode.executes(executor); // flags are optional, so the flags node always ends the command
        }
        if (args.isEmpty()) {
            // No positional args: the branch is either bare (executor on the literal) or all-flags (the flags
            // node carries the executor and the literal above must run too).
            return new ArgChain(flagsNode, flagsNode != null);
        }
        RequiredArgumentBuilder<CommandSourceStack, ?> tail = flagsNode;
        boolean tailEndsCommand = flagsNode != null;
        for (int i = args.size() - 1; i >= 0; i--) {
            ArgBinder.ParamArg pa = args.get(i);
            RequiredArgumentBuilder<CommandSourceStack, ?> builder =
                    Cmd.argument(pa.name(), pa.resolver().argumentType(pa.arg(), pa.parameter()));
            Suggestions.apply(builder, pa.view(), pa.resolver(), resolvers);
            if (tail == null) {
                builder.executes(executor);
            } else {
                builder.then(tail);
                if (tailEndsCommand || args.get(i + 1).arg().optional()) {
                    builder.executes(executor); // the next node may end the command, so this one may too
                }
            }
            tail = builder;
            tailEndsCommand = false;
        }
        return new ArgChain(tail, args.get(0).arg().optional());
    }

    /** The single greedy trailing node that parses this branch's flags and switches. */
    private static RequiredArgumentBuilder<CommandSourceStack, ?> flagsNode(BranchModel branch) {
        RequiredArgumentBuilder<CommandSourceStack, Flags> node =
                Cmd.argument("flags", new FlagArgumentType(branch.flags()));
        node.suggests(new FlagArgumentType(branch.flags())::listSuggestions);
        return node;
    }

    private static void applyPermission(
            ArgumentBuilder<CommandSourceStack, ?> builder, @Nullable Permission permission) {
        if (permission != null) {
            builder.requires(Cmd.permission(permission.value()));
        }
    }
}
