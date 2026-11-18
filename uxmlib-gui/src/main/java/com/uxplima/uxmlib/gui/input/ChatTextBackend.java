package com.uxplima.uxmlib.gui.input;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The chat backend of the text-input seam: one {@link AsyncChatEvent} listener that captures a player's next chat line
 * and reports it as the input. This single shared listener replaces the per-context chat-prompt listeners that used to
 * be copied into kits, warps, and the three economy flows: there is now exactly one place a chat line becomes input.
 *
 * <p>A pending prompt carries a deadline so an unanswered prompt expires instead of swallowing a chat line minutes
 * later; registering a second prompt for the same player replaces the first (the overlap guard). The captured line is
 * handed to the outcome on the async chat thread: the seam ({@link TextInput}) hops it onto the player's region thread
 * before the call site's callback runs, so the call site never has to. {@code Cancelled} is never produced here: a chat
 * prompt has no structural cancel; typing a cancel keyword is detected upstream by {@link TextInput} from the submitted
 * text. Install once on enable and {@link #uninstall()} on disable so a disabled plugin holds no listener.
 *
 * <p><b>Privacy.</b> A captured line may be a secret, a PIN or a password. Paper raises a chat line as the legacy
 * {@code AsyncPlayerChatEvent} first whenever any plugin listens to it, and only then as {@link AsyncChatEvent}, so the
 * capture listens to both at the lowest priority. It takes the line from whichever comes first, and in both it cancels
 * the event, empties the message and drops the recipients, so a chat logger or a bridge listening later reads nothing,
 * cancelled or not. A listener at the same lowest priority registered before this one is outside its reach. *
 * <p>This is best-effort hiding, not a secure channel: the event's {@code originalMessage()} and
 * {@code signedMessage()}, {@code AsyncChatDecorateEvent}, and a lowest-priority listener registered before the
 * capture still see the raw text. A real secret is asked for with {@link InputRequest#asSecret()}, never in chat.
 */
@NullMarked
final class ChatTextBackend implements TextInputBackend, Listener {

    private static final Duration PROMPT_EXPIRY = Duration.ofMinutes(2);

    private final Plugin plugin;
    private final ChatPrompts prompts = new ChatPrompts();

    ChatTextBackend(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /** Register the chat listener. Call once, on enable. */
    void install() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Unregister the listener and drop any pending prompts. Call on disable; mirrors {@link #install()}. */
    void uninstall() {
        HandlerList.unregisterAll(this);
        prompts.clear();
    }

    @Override
    public void open(Player player, Component prompt, @Nullable String initialText, Consumer<InputResult> outcome) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(outcome, "outcome");
        // The chat field cannot be pre-seeded, so initialText is intentionally ignored here.
        prompts.await(
                player.getUniqueId(),
                text -> outcome.accept(new InputResult.Submitted(text)),
                System.currentTimeMillis() + PROMPT_EXPIRY.toMillis());
        player.sendMessage(prompt);
    }

    /** Drop {@code player}'s pending prompt, so their next line is chat again. Nothing is reported for it. */
    void cancel(UUID player) {
        prompts.cancel(player);
    }

    // The legacy event is the one Paper raises first whenever any plugin listens to it, so a capture must read it.
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        if (prompts.take(
                event.getPlayer().getUniqueId(), event.getMessage().trim(), System.currentTimeMillis(), true)) {
            ChatPrivacy.hide(event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        String text = PlainTextComponentSerializer.plainText()
                .serialize(event.message())
                .trim();
        if (prompts.take(event.getPlayer().getUniqueId(), text, System.currentTimeMillis(), false)) {
            ChatPrivacy.hide(event);
        }
    }

    /** Drop a player's pending prompt when they leave, so a quit mid-prompt never lingers until the deadline. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        prompts.cancel(event.getPlayer().getUniqueId());
    }
}
