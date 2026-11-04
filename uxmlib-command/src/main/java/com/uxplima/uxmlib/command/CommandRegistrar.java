package com.uxplima.uxmlib.command;

import java.util.List;
import java.util.Objects;

import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;

/**
 * Registers Brigadier commands through Paper's {@code LifecycleEvents.COMMANDS} event, hiding the
 * boilerplate that is easy to get wrong (the right event package, the {@code run} handler method, the
 * {@code ReloadableRegistrarEvent<Commands>} generic). Call from {@code onEnable}:
 *
 * <pre>{@code
 * CommandRegistrar.register(this,
 *     Cmd.literal("ping").executes(ctx -> { Sender.of(ctx.getSource()).send(Component.text("pong")); return Cmd.OK; }),
 *     "Replies with pong");
 * }</pre>
 */
public final class CommandRegistrar {

    private CommandRegistrar() {}

    /**
     * Register a built command node with a description and aliases. A sender a {@link Gate} refuses reads the server's
     * own permission message; a plugin with its own line passes a {@link CommandGates.Refusal}.
     */
    public static void register(
            JavaPlugin plugin, LiteralCommandNode<CommandSourceStack> node, String description, List<String> aliases) {
        register(plugin, node, description, aliases, CommandRegistrar::serverRefusal);
    }

    /**
     * Register a built command node with a description and aliases, its {@link Gate}s answered by {@code refusal}.
     * The tree is sealed (see {@link CommandGates#seal}), and when it has a gate the command list each player is
     * sent loses the nodes that gate hides from them.
     */
    public static void register(
            JavaPlugin plugin,
            LiteralCommandNode<CommandSourceStack> node,
            String description,
            List<String> aliases,
            CommandGates.Refusal refusal) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(aliases, "aliases");
        Objects.requireNonNull(refusal, "refusal");
        List<String> aliasCopy = List.copyOf(aliases);
        LiteralCommandNode<CommandSourceStack> sealed = CommandGates.seal(node, refusal);
        if (CommandGates.hasGates(sealed)) {
            GateVisibility.listen(plugin).add(sealed, aliasCopy);
        }
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            // registrar() is an unannotated generic return, which NullAway treats as @Nullable; it is
            // always present inside a COMMANDS handler.
            Commands commands = Objects.requireNonNull(event.registrar(), "registrar");
            commands.register(sealed, description, aliasCopy);
        });
    }

    private static void serverRefusal(CommandSourceStack source, Gate gate) {
        source.getSender().sendMessage(source.getSender().getServer().permissionMessage());
    }

    /** Register a command builder (built for you) with a description and varargs aliases. */
    public static void register(
            JavaPlugin plugin,
            LiteralArgumentBuilder<CommandSourceStack> builder,
            String description,
            String... aliases) {
        Objects.requireNonNull(builder, "builder");
        register(plugin, builder.build(), description, List.of(aliases));
    }
}
