package com.uxplima.uxmlib.text.message;

import java.util.Locale;
import java.util.Map;

import net.kyori.adventure.audience.Audience;

import com.uxplima.uxmlib.text.Text;

/**
 * One render as a proxy plugin makes it: a catalogue, a default language and an audience that is not a Bukkit
 * player. {@link MessagesRenderWithoutBukkitTest} runs it through a class loader that has no Bukkit at all.
 */
public final class RenderWithoutBukkit {

    private RenderWithoutBukkit() {}

    /** The plain text of one greeting rendered for an audience with no name. */
    public static String greet() {
        MessageCatalog catalog =
                new MessageCatalog(Map.of(Locale.ENGLISH, Map.of("proxy.greeting", "Hello <player>")), Locale.ENGLISH);
        Messages messages = new Messages(catalog, LocaleSource.ofDefault(Locale.ENGLISH));
        return Text.plain(messages.render(Audience.empty(), MessageKey.of("proxy.greeting", "Hello <player>")));
    }
}
