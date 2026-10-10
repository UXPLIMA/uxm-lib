package com.uxplima.uxmlib.command.annotation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmlib.text.message.LocaleSource;
import org.jspecify.annotations.Nullable;

/**
 * Keeps a root word for some languages out of the command list of every other reader.
 *
 * <p>Such a word is registered as an ordinary alias, so typing it works for everyone and the console, a
 * script and a Bedrock player keep the ordinary path. Only the list a client is sent is trimmed: a player
 * who reads English does not find {@code /arsa} among their completions. The word is trimmed in both of the
 * forms the server sends, bare and under the plugin's namespace.
 *
 * <p>A word under a command needs none of this. Brigadier sends a client only the nodes it may use, and the
 * renderer gives such a word a requirement that names its readers.
 *
 * <p>A client that changes its language is sent its list again, a tick later, once the server holds the
 * new language. A player who chooses a language inside a plugin is sent it again by
 * {@code LanguageChoices}.
 */
public final class LocalizedAliasVisibility implements Listener {

    private final Map<String, List<String>> tagsByLabel;
    private final LocaleSource locales;
    private final @Nullable Plugin plugin;

    /**
     * @param tagsByWord the language tags each root word is for
     * @param namespace the prefix the server files the plugin's commands under, its name in lower case
     * @param locales what language a player reads, as the plugin decides it
     * @param plugin what the refresh after a language change is scheduled for; {@code null} skips it
     */
    public LocalizedAliasVisibility(
            Map<String, List<String>> tagsByWord, String namespace, LocaleSource locales, @Nullable Plugin plugin) {
        Objects.requireNonNull(tagsByWord, "tagsByWord");
        Objects.requireNonNull(namespace, "namespace");
        this.locales = Objects.requireNonNull(locales, "locales");
        this.plugin = plugin;
        Map<String, List<String>> labels = new LinkedHashMap<>();
        String prefix = namespace.toLowerCase(Locale.ROOT) + ":";
        tagsByWord.forEach((word, tags) -> {
            String label = word.toLowerCase(Locale.ROOT);
            labels.put(label, List.copyOf(tags));
            labels.put(prefix + label, List.copyOf(tags));
        });
        this.tagsByLabel = Collections.unmodifiableMap(labels);
    }

    /** Take every word for another language out of the list this player is about to be sent. */
    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        Locale reads = locales.localeOf(event.getPlayer());
        event.getCommands().removeIf(label -> {
            List<String> tags = tagsByLabel.get(label.toLowerCase(Locale.ROOT));
            return tags != null && tags.stream().noneMatch(tag -> LocaleTags.matches(tag, reads));
        });
    }

    /** Send the list again once the client's new language has landed. */
    @EventHandler
    public void onLocaleChange(PlayerLocaleChangeEvent event) {
        Plugin owner = plugin;
        if (owner == null) {
            return;
        }
        Player player = event.getPlayer();
        player.getScheduler().runDelayed(owner, task -> player.updateCommands(), null, 1L);
    }
}
