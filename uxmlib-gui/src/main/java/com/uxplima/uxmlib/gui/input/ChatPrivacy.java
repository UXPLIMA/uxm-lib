package com.uxplima.uxmlib.gui.input;

import org.bukkit.event.player.AsyncPlayerChatEvent;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;

import org.jspecify.annotations.NullMarked;

/**
 * Hides a chat line captured as input from every listener after the capture: the event is cancelled, its message
 * emptied and its recipients dropped, so a listener that reads cancelled events too finds nothing to log or forward.
 *
 * <p>This is best-effort hiding, not a secure channel: the event's {@code originalMessage()} and
 * {@code signedMessage()}, {@code AsyncChatDecorateEvent}, and a lowest-priority listener registered before the
 * capture still see the raw text. A real secret is asked for with {@link InputRequest#asSecret()}, never in chat.
 */
@NullMarked
final class ChatPrivacy {

    private ChatPrivacy() {}

    @SuppressWarnings("deprecation") // the legacy event still carries the line to every plugin that listens to it
    static void hide(AsyncPlayerChatEvent event) {
        event.setCancelled(true);
        event.setMessage("");
        try {
            event.getRecipients().clear();
        } catch (UnsupportedOperationException fixed) {
            // A plugin handed the event a set it may not change; the emptied message already hides the line.
        }
    }

    static void hide(AsyncChatEvent event) {
        event.setCancelled(true);
        event.message(Component.empty());
        try {
            event.viewers().clear();
        } catch (UnsupportedOperationException fixed) {
            // A plugin handed the event a set it may not change; the emptied message already hides the line.
        }
    }
}
