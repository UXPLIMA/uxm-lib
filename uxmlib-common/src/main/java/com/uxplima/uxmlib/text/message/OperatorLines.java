package com.uxplima.uxmlib.text.message;

import java.util.Map;
import java.util.regex.Pattern;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** The argument tags behind {@link Messages#renderLine}. */
final class OperatorLines {

    private static final Pattern TAG_NAME = Pattern.compile("[a-z0-9_-]+");

    private OperatorLines() {}

    /** The caller's resolvers with one {@code <name>} tag per argument in front, so an entry may use either form. */
    static TagResolver[] withArguments(Map<String, String> arguments, TagResolver[] resolvers) {
        if (arguments.isEmpty()) {
            return resolvers;
        }
        TagResolver[] all = new TagResolver[resolvers.length + 1];
        TagResolver.Builder tags = TagResolver.builder();
        // A tag name is lower case letters, digits, an underscore or a hyphen; any other argument is a brace token
        // only.
        arguments.forEach((name, value) -> {
            if (TAG_NAME.matcher(name).matches()) {
                tags.resolver(Placeholder.parsed(name, value));
            }
        });
        all[0] = tags.build();
        System.arraycopy(resolvers, 0, all, 1, resolvers.length);
        return all;
    }
}
