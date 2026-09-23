package com.uxplima.uxmlib.command;

import io.papermc.paper.command.brigadier.argument.CustomArgumentType;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.jspecify.annotations.NullMarked;

/**
 * One argument that reads everything up to the next space, whatever the characters.
 *
 * <p>Brigadier's {@code word()} stops at any character outside {@code 0-9 A-Z a-z _ - . +}, so it refused a plot id
 * written {@code creative:-1;0}, a namespaced key such as {@code minecraft:stone}, a colour such as {@code #ff0000},
 * an IPv6 address and every Turkish letter, and it refused them while parsing, before the command could say why. This
 * type reads the same token a player sees between two spaces and leaves the judging to the command.
 *
 * <p>The client is told it is a {@code word()}, so it completes and highlights the argument as before. A token the
 * client's own word rule dislikes is drawn in red while it is typed and is still sent, and it is read here.
 */
@NullMarked
final class TokenArgumentType implements CustomArgumentType<String, String> {

    static final TokenArgumentType INSTANCE = new TokenArgumentType();

    private TokenArgumentType() {}

    @Override
    public String parse(StringReader reader) {
        int start = reader.getCursor();
        while (reader.canRead() && reader.peek() != ' ') {
            reader.skip();
        }
        return reader.getString().substring(start, reader.getCursor());
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }
}
