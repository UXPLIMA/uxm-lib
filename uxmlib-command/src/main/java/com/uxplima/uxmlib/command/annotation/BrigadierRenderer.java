package com.uxplima.uxmlib.command.annotation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Cmd;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.message.LocaleSource;
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
            root.requires(Cmd.visibility(classPermission.value()));
        }
        Map<String, Predicate<CommandSourceStack>> words = sharedWords(model.branches());
        for (BranchModel branch : model.branches()) {
            attachBranch(root, model.handler(), branch, command.name(), words);
        }
        if (command.help()) {
            root.then(HelpRenderer.helpLiteral(command.name(), model.branches(), resolvers));
        }
        return root.build();
    }

    /**
     * Who may see each word of each path, keyed by the path up to and including it.
     *
     * <p>Brigadier keeps one node per word, and when two branches bring the same word it keeps the requirement
     * of the one that came first. {@code lang}, {@code lang set} and an operator's {@code lang set-player} all
     * bring {@code lang}, so whichever was read first used to decide who saw the word at all. A word is open
     * here to anyone who may use any branch that passes through it. The executor refuses a sender who lacks a
     * branch's own node, so a shared word opened wider lets nobody run what they may not.
     */
    private Map<String, Predicate<CommandSourceStack>> sharedWords(List<BranchModel> branches) {
        Map<String, Predicate<CommandSourceStack>> words = new HashMap<>();
        for (BranchModel branch : branches) {
            String[] literals = branch.literals();
            if (literals.length == 0) {
                continue;
            }
            Predicate<CommandSourceStack> may = visibilityOf(branch.permission());
            for (String[] spelling : spellingsOf(branch, literals)) {
                for (int depth = 1; depth <= spelling.length; depth++) {
                    words.merge(key(spelling, depth), may, Predicate::or);
                }
            }
            branch.localizedAliases().forEach((word, tags) -> {
                String[] spelling = literals.clone();
                spelling[spelling.length - 1] = word;
                words.merge(key(spelling, spelling.length), may.and(readersOf(tags)), Predicate::or);
            });
        }
        return words;
    }

    private static List<String[]> spellingsOf(BranchModel branch, String[] literals) {
        List<String[]> spellings = new ArrayList<>();
        spellings.add(literals);
        for (String alias : branch.aliases()) {
            String[] spelling = literals.clone();
            spelling[spelling.length - 1] = alias;
            spellings.add(spelling);
        }
        return spellings;
    }

    private static String key(String[] spelling, int depth) {
        return String.join(" ", Arrays.copyOf(spelling, depth));
    }

    private static Predicate<CommandSourceStack> visibilityOf(@Nullable Permission permission) {
        return permission == null ? source -> true : Cmd.visibility(permission.value());
    }

    private void attachBranch(
            LiteralArgumentBuilder<CommandSourceStack> root,
            Object handler,
            BranchModel branch,
            String rootName,
            Map<String, Predicate<CommandSourceStack>> words) {
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
        attachSpine(root, literals, chain, executor, words);
        for (String alias : branch.aliases()) {
            String[] spelling = literals.clone();
            spelling[spelling.length - 1] = alias;
            // A builder is spent once it is built, so every spelling gets its own chain of nodes. They all
            // carry the one executor, so an alias runs the method the declared path runs.
            attachSpine(root, spelling, buildArgChain(branch, executor), executor, words);
        }
        branch.localizedAliases().forEach((word, tags) -> {
            String[] spelling = literals.clone();
            spelling[spelling.length - 1] = word;
            attachSpine(root, spelling, buildArgChain(branch, executor), executor, words);
        });
    }

    /**
     * Who a word for some languages is meant for: a player who reads one of them, by the language the plugin
     * says they read, and anything that is not a player. Brigadier sends a client only the nodes it may use,
     * so this is what keeps the word out of every other reader's completions.
     */
    private Predicate<CommandSourceStack> readersOf(List<String> tags) {
        LocaleSource locales = resolvers.locales();
        return source -> {
            if (!(source.getSender() instanceof Player player)) {
                return true;
            }
            Locale reads = locales.localeOf(player);
            return tags.stream().anyMatch(tag -> LocaleTags.matches(tag, reads));
        };
    }

    /**
     * Attach one spelling of a branch. Every word on its path takes the requirement {@link #sharedWords} worked
     * out for it, so a word two branches share opens to the readers of both, and the branch's own last word
     * keeps its own node and, for a word in one language, its readers.
     */
    private void attachSpine(
            LiteralArgumentBuilder<CommandSourceStack> root,
            String[] literals,
            ArgChain chain,
            com.mojang.brigadier.Command<CommandSourceStack> executor,
            Map<String, Predicate<CommandSourceStack>> words) {
        ArgumentBuilder<CommandSourceStack, ?> tail = Cmd.literal(literals[literals.length - 1]);
        applyChain(tail, chain, executor);
        tail.requires(requirementAt(words, literals, literals.length));
        for (int depth = literals.length - 1; depth >= 1; depth--) {
            LiteralArgumentBuilder<CommandSourceStack> parent = Cmd.literal(literals[depth - 1]);
            parent.then(tail);
            parent.requires(requirementAt(words, literals, depth));
            tail = parent;
        }
        root.then(tail);
    }

    private static Predicate<CommandSourceStack> requirementAt(
            Map<String, Predicate<CommandSourceStack>> words, String[] spelling, int depth) {
        return words.getOrDefault(key(spelling, depth), source -> true);
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
            if (chain.prefixFlags != null) {
                builder.then(chain.prefixFlags); // every optional argument may be left out and a flag written instead
            }
        } else {
            builder.executes(executor);
        }
    }

    /**
     * The outermost argument builder of a branch (or {@code null} when it takes no arguments and no flags),
     * whether the node above it must also end the command (a leading optional argument, or a branch that is
     * all flags: flags are always optional), and the flags node the caller attaches beside {@code firstArg}
     * when every positional argument may be left out.
     */
    private record ArgChain(
            @Nullable RequiredArgumentBuilder<CommandSourceStack, ?> firstArg,
            boolean prefixExecutable,
            @Nullable RequiredArgumentBuilder<CommandSourceStack, ?> prefixFlags) {}

    private ArgChain buildArgChain(BranchModel branch, com.mojang.brigadier.Command<CommandSourceStack> executor) {
        List<ArgBinder.ParamArg> args = branch.args();
        boolean hasFlags = branch.hasFlags();
        if (args.isEmpty()) {
            // No positional args: the branch is either bare (executor on the literal) or all-flags (the flags
            // node carries the executor and the literal above must run too).
            return new ArgChain(hasFlags ? flagsNode(branch, executor) : null, hasFlags, null);
        }
        RequiredArgumentBuilder<CommandSourceStack, ?> tail = hasFlags ? flagsNode(branch, executor) : null;
        boolean tailEndsCommand = hasFlags;
        for (int i = args.size() - 1; i >= 0; i--) {
            ArgBinder.ParamArg pa = args.get(i);
            RequiredArgumentBuilder<CommandSourceStack, ?> builder =
                    Cmd.argument(pa.name(), pa.resolver().argumentType(pa.arg(), pa.parameter()));
            Suggestions.apply(builder, pa.view(), pa.resolver(), resolvers);
            boolean endsHere;
            if (tail == null) {
                endsHere = true;
            } else {
                builder.then(tail);
                endsHere = tailEndsCommand || args.get(i + 1).arg().optional();
            }
            if (endsHere) {
                builder.executes(executor); // the next node may end the command, so this one may too
                if (hasFlags && i < args.size() - 1) {
                    // The command may end here, so a flag may be written here: every optional argument
                    // below this one is left out and the flags are still parsed. The positional is
                    // attached first, so a word that is an argument is read as one.
                    builder.then(flagsNode(branch, executor));
                }
            }
            tail = builder;
            tailEndsCommand = false;
        }
        boolean prefixOptional = args.get(0).arg().optional();
        return new ArgChain(tail, prefixOptional, hasFlags && prefixOptional ? flagsNode(branch, executor) : null);
    }

    /** One greedy trailing node that parses this branch's flags and switches, and ends the command. */
    private static RequiredArgumentBuilder<CommandSourceStack, ?> flagsNode(
            BranchModel branch, com.mojang.brigadier.Command<CommandSourceStack> executor) {
        RequiredArgumentBuilder<CommandSourceStack, Flags> node =
                Cmd.argument("flags", new FlagArgumentType(branch.flags()));
        node.suggests(new FlagArgumentType(branch.flags())::listSuggestions);
        node.executes(executor); // flags are optional, so a flags node always ends the command
        return node;
    }

    private static void applyPermission(
            ArgumentBuilder<CommandSourceStack, ?> builder, @Nullable Permission permission) {
        if (permission != null) {
            builder.requires(Cmd.visibility(permission.value()));
        }
    }
}
