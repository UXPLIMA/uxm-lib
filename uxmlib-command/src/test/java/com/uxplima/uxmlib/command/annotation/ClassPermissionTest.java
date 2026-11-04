package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import org.junit.jupiter.api.Test;

/** A class-level node holds even on a tree built and registered without the registrar's seal. */
class ClassPermissionTest {

    private final List<String> ran = new ArrayList<>();

    @Command(name = "admin", help = false)
    @Permission("plugin.admin")
    public class AdminCommand {

        @Subcommand("wipe")
        @Permission("plugin.admin.wipe")
        public void wipe(CommandSender sender) {
            ran.add("wipe");
        }
    }

    @Test
    void aSenderWithTheBranchNodeButNotTheClassNodeIsRefused() throws CommandSyntaxException {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(AnnotatedCommands.buildNode(new AdminCommand()));

        dispatcher.execute("admin wipe", source("plugin.admin.wipe"));
        assertThat(ran).isEmpty();

        dispatcher.execute("admin wipe", source("plugin.admin.wipe", "plugin.admin"));
        assertThat(ran).containsExactly("wipe");
    }

    private static CommandSourceStack source(String... nodes) {
        Set<String> held = Set.of(nodes);
        CommandSender sender = (CommandSender) Proxy.newProxyInstance(
                ClassPermissionTest.class.getClassLoader(),
                new Class<?>[] {CommandSender.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hasPermission" -> args[0] instanceof String node && held.contains(node);
                    case "getName" -> "tester";
                    case "sendMessage" -> null;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "tester";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (CommandSourceStack) Proxy.newProxyInstance(
                ClassPermissionTest.class.getClassLoader(),
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
}
