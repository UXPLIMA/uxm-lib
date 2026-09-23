package com.uxplima.uxmlib.command;

import java.util.Objects;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

/**
 * Reads parsed arguments out of a {@link CommandContext} by name, one call per primitive type, so a
 * handler does not repeat the {@code XArgumentType.getX(ctx, name)} ceremony. The argument must have
 * been declared with the matching {@code ArgumentType}.
 */
public final class Args {

    private Args() {}

    /**
     * An argument of one token: everything up to the next space, a colon, a hash or a Turkish letter included. Read
     * it back with {@link #string}. Use it where Brigadier's {@code word()} would refuse what an operator or a player
     * legitimately types, such as {@code minecraft:stone}. A token opening with a quote is a quoted phrase, as in
     * {@code string()}.
     */
    public static ArgumentType<String> token() {
        return TokenArgumentType.INSTANCE;
    }

    /** The string argument named {@code name}. */
    public static String string(CommandContext<CommandSourceStack> ctx, String name) {
        return StringArgumentType.getString(check(ctx, name), name);
    }

    /** The integer argument named {@code name}. */
    public static int integer(CommandContext<CommandSourceStack> ctx, String name) {
        return IntegerArgumentType.getInteger(check(ctx, name), name);
    }

    /** The double argument named {@code name}. */
    public static double number(CommandContext<CommandSourceStack> ctx, String name) {
        return DoubleArgumentType.getDouble(check(ctx, name), name);
    }

    /** The boolean argument named {@code name}. */
    public static boolean bool(CommandContext<CommandSourceStack> ctx, String name) {
        return BoolArgumentType.getBool(check(ctx, name), name);
    }

    private static CommandContext<CommandSourceStack> check(CommandContext<CommandSourceStack> ctx, String name) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(name, "name");
        return ctx;
    }
}
