package com.uxplima.uxmlib.text.message;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** The argument filling behind {@link Messages#renderLine} and {@link Messages#expandLine}. */
final class OperatorLines {

    private static final Pattern TOKEN = Pattern.compile("\\{([A-Za-z0-9_.-]+)\\}");

    private static final Pattern TAG_NAME = Pattern.compile("[a-z0-9_-]+");

    private OperatorLines() {}

    /**
     * {@code template} with each {@code {name}} an argument answers replaced by its value, as text. Done before
     * parsing so a value can sit inside a tag or a placeholder another plugin reads ({@code <color:{accent}>}).
     * A token no argument answers stays as written. A value goes in once and is not read again.
     */
    static String fill(String template, Map<String, String> arguments) {
        if (arguments.isEmpty() || template.indexOf('{') < 0) {
            return template;
        }
        Matcher matcher = TOKEN.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = arguments.get(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value != null ? value : matcher.group()));
        }
        matcher.appendTail(out);
        return out.toString();
    }

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
