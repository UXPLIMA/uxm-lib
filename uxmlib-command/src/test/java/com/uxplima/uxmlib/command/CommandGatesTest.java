package com.uxplima.uxmlib.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import org.junit.jupiter.api.Test;

/** A command tree with gates, registered in a real dispatcher and run as senders with and without the nodes. */
class CommandGatesTest {

    private static final String EDIT = "plugin.kit.edit";
    private static final String USE = "plugin.kit.use";

    private final List<String> ran = new ArrayList<>();
    private final List<String> refused = new ArrayList<>();

    private final CommandGates.Refusal refusal =
            (source, gate) -> refused.add(gate.refusingNode(source.getSender()).orElse("?"));

    /** {@code /kit} for players, {@code /kit editor <name>} for staff, the shape the customer's report had. */
    private LiteralCommandNode<CommandSourceStack> kit() {
        return Cmd.literal("kit")
                .requires(Gate.permission(USE))
                .executes(ctx -> run("kit"))
                .then(Cmd.literal("list").executes(ctx -> run("list")))
                .then(Cmd.literal("editor")
                        .requires(Gate.permission(EDIT))
                        .executes(ctx -> run("editor"))
                        .then(Cmd.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) ->
                                        builder.suggest("starter").buildFuture())
                                .executes(ctx -> run("editor " + StringArgumentType.getString(ctx, "name")))))
                .then(Cmd.argument("name", StringArgumentType.word()).executes(ctx -> run("claim")))
                .build();
    }

    private int run(String what) {
        ran.add(what);
        return Cmd.OK;
    }

    private CommandDispatcher<CommandSourceStack> dispatcher() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(CommandGates.seal(kit(), refusal));
        return dispatcher;
    }

    @Test
    void aSenderWithoutTheNodeIsRefusedWithThePluginsLineNotBrigadiersText() throws CommandSyntaxException {
        int result = dispatcher().execute("kit editor", source(USE));

        assertThat(result).isZero();
        assertThat(refused).containsExactly(EDIT);
        assertThat(ran).isEmpty();
    }

    @Test
    void theRefusalHoldsForEveryWordBelowTheGate() throws CommandSyntaxException {
        dispatcher().execute("kit editor starter", source(USE));

        assertThat(refused).containsExactly(EDIT);
        assertThat(ran).isEmpty();
    }

    @Test
    void aSenderWithTheNodeRunsTheCommand() throws CommandSyntaxException {
        dispatcher().execute("kit editor starter", source(USE, EDIT));

        assertThat(ran).containsExactly("editor starter");
        assertThat(refused).isEmpty();
    }

    @Test
    void theRootsGateRefusesEveryBranch() throws CommandSyntaxException {
        dispatcher().execute("kit list", source());

        assertThat(refused).containsExactly(USE);
        assertThat(ran).isEmpty();
    }

    @Test
    void anUngatedSiblingStillRunsAndAnUnknownWordStillReachesTheArgument() throws CommandSyntaxException {
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();

        dispatcher.execute("kit list", source(USE));
        dispatcher.execute("kit starter", source(USE));

        assertThat(ran).containsExactly("list", "claim");
    }

    @Test
    void suggestionsBelowARefusingGateAreEmpty() {
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();

        Suggestions refusedSuggestions = dispatcher
                .getCompletionSuggestions(dispatcher.parse("kit editor ", source(USE)))
                .join();
        Suggestions allowedSuggestions = dispatcher
                .getCompletionSuggestions(dispatcher.parse("kit editor ", source(USE, EDIT)))
                .join();

        assertThat(refusedSuggestions.getList()).isEmpty();
        assertThat(allowedSuggestions.getList()).extracting(s -> s.getText()).containsExactly("starter");
    }

    @Test
    void theCommandListLosesWhatTheGateHides() {
        LiteralCommandNode<CommandSourceStack> registered = CommandGates.seal(kit(), refusal);
        CommandDispatcher<CommandSourceStack> sent = new CommandDispatcher<>();
        sent.getRoot().addChild(kit());
        sent.getRoot().addChild(Cmd.literal("other").then(Cmd.literal("editor")).build());

        CommandGates.prune(sent.getRoot(), Map.of("kit", registered), sender(USE));

        CommandNode<CommandSourceStack> kit = sent.getRoot().getChild("kit");
        assertThat(kit).isNotNull();
        assertThat(kit.getChild("editor")).isNull();
        assertThat(kit.getChild("list")).isNotNull();
        assertThat(sent.getRoot().getChild("other").getChild("editor")).isNotNull();
    }

    @Test
    void aCommandHiddenAtItsRootLeavesTheList() {
        LiteralCommandNode<CommandSourceStack> registered = CommandGates.seal(kit(), refusal);
        CommandDispatcher<CommandSourceStack> sent = new CommandDispatcher<>();
        sent.getRoot().addChild(kit());

        CommandGates.prune(sent.getRoot(), Map.of("kit", registered), sender());

        assertThat(sent.getRoot().getChild("kit")).isNull();
    }

    @Test
    void aWordForSomeReadersIsHiddenFromOthersAndStillRunsForThem() throws CommandSyntaxException {
        LiteralCommandNode<CommandSourceStack> tree = Cmd.literal("kit")
                .then(Cmd.literal("düzenle")
                        .requires(Gate.shownTo(sender -> sender.hasPermission("reads.turkish")))
                        .executes(ctx -> run("düzenle")))
                .build();
        LiteralCommandNode<CommandSourceStack> registered = CommandGates.seal(tree, refusal);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(registered);
        CommandDispatcher<CommandSourceStack> sent = new CommandDispatcher<>();
        sent.getRoot().addChild(tree);

        CommandGates.prune(sent.getRoot(), Map.of("kit", registered), sender());
        dispatcher.execute("kit düzenle", source());

        assertThat(sent.getRoot().getChild("kit").getChild("düzenle")).isNull();
        assertThat(ran).containsExactly("düzenle");
    }

    @Test
    void anyRedirectInATreeWithGatesIsRefusedWhenTheTreeIsSealed() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        LiteralCommandNode<CommandSourceStack> gated = Cmd.literal("editor")
                .requires(Gate.permission(EDIT))
                .executes(ctx -> run("editor"))
                .build();
        LiteralCommandNode<CommandSourceStack> tree = Cmd.literal("kit")
                .then(gated)
                .then(Cmd.literal("edit").redirect(gated))
                .build();

        assertThatThrownBy(() -> CommandGates.seal(tree, refusal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("edit");
        assertThat(dispatcher.getRoot().getChildren()).isEmpty();
    }

    @Test
    void anArgumentTypesOwnSuggestionsAreGatedToo() {
        ArgumentType<String> secrets = new ArgumentType<>() {
            @Override
            public String parse(StringReader reader) {
                return reader.readUnquotedString();
            }

            @Override
            public <S> CompletableFuture<Suggestions> listSuggestions(
                    CommandContext<S> context, SuggestionsBuilder builder) {
                return builder.suggest("secret").buildFuture();
            }
        };
        LiteralCommandNode<CommandSourceStack> tree = Cmd.literal("kit")
                .then(Cmd.literal("editor")
                        .requires(Gate.permission(EDIT))
                        .then(Cmd.argument("name", secrets).executes(ctx -> run("editor"))))
                .build();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(CommandGates.seal(tree, refusal));

        Suggestions refusedSuggestions = dispatcher
                .getCompletionSuggestions(dispatcher.parse("kit editor ", source()))
                .join();
        Suggestions allowedSuggestions = dispatcher
                .getCompletionSuggestions(dispatcher.parse("kit editor ", source(EDIT)))
                .join();

        assertThat(refusedSuggestions.getList()).isEmpty();
        assertThat(allowedSuggestions.getList()).extracting(s -> s.getText()).containsExactly("secret");
    }

    @Test
    void theServersCompletionsLoseTheWordsAGateHides() {
        LiteralCommandNode<CommandSourceStack> registered = CommandGates.seal(kit(), refusal);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(registered);
        // The server reads the line the client typed, slash and all, past the slash: ranges count the slash.
        StringReader typed = new StringReader("/kit ");
        typed.skip();
        Suggestions offered = dispatcher
                .getCompletionSuggestions(dispatcher.parse(typed, source(USE)))
                .join();

        Suggestions forPlayer =
                CommandGates.visibleSuggestions("/kit ", offered, Map.of("kit", registered), sender(USE));
        Suggestions forStaff =
                CommandGates.visibleSuggestions("/kit ", offered, Map.of("kit", registered), sender(USE, EDIT));

        assertThat(offered.getList()).extracting(s -> s.getText()).contains("editor", "list");
        assertThat(forPlayer.getList())
                .extracting(s -> s.getText())
                .contains("list")
                .doesNotContain("editor");
        assertThat(forStaff.getList()).extracting(s -> s.getText()).contains("editor", "list");
    }

    @Test
    void aTreeWalkAsksTheGateNotBrigadier() {
        LiteralCommandNode<CommandSourceStack> registered = CommandGates.seal(kit(), refusal);
        CommandNode<CommandSourceStack> editor = registered.getChild("editor");

        assertThat(editor.canUse(source(USE))).isTrue();
        assertThat(CommandGates.shows(editor, source(USE))).isFalse();
        assertThat(CommandGates.shows(editor, source(USE, EDIT))).isTrue();
        assertThat(CommandGates.shows(registered.getChild("list"), source(USE))).isTrue();
    }

    @Test
    void aGateJoinsOnlyGatesAndNamesTheNodeThatRefused() {
        Gate both = Gate.permission(USE).and(Gate.permission(EDIT));

        assertThat(both.refusingNode(sender(USE))).contains(EDIT);
        assertThat(both.refusingNode(sender(EDIT))).contains(USE);
        assertThat(both.refusingNode(sender(USE, EDIT))).isEmpty();
        assertThatThrownBy(() -> Gate.permission(USE).and(source -> false))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static CommandSourceStack source(String... nodes) {
        CommandSender sender = sender(nodes);
        return (CommandSourceStack) Proxy.newProxyInstance(
                CommandGatesTest.class.getClassLoader(),
                new Class<?>[] {CommandSourceStack.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getSender" -> sender;
                    case "getExecutor" -> null;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "source";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static CommandSender sender(String... nodes) {
        Set<String> held = Set.of(nodes);
        return (CommandSender) Proxy.newProxyInstance(
                CommandGatesTest.class.getClassLoader(),
                new Class<?>[] {CommandSender.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hasPermission" -> args[0] instanceof String node && held.contains(node);
                    case "getName" -> "tester";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "tester";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
