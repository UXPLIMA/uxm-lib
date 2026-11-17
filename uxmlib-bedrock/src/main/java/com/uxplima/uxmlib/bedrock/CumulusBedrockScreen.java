package com.uxplima.uxmlib.bedrock;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.bukkit.entity.Player;

import org.geysermc.api.Geyser;
import org.geysermc.cumulus.form.Form;
import org.geysermc.floodgate.api.FloodgateApi;

/**
 * The Cumulus-backed {@link BedrockScreen}: it builds each form with {@link CumulusForms} and hands it to the plugin
 * that carries the player's connection, Floodgate or Geyser. Both take the same Cumulus form, so only the last call
 * differs, and {@link #viaFloodgate()} and {@link #viaGeyser()} pick it. {@code BedrockScreen.forServer} constructs
 * it only behind the plugin-enabled guard of the plugin it sends through, so it never loads on a Java-only server and
 * a {@code NoClassDefFoundError} is impossible there. Each send is a lambda, so the SDK of the other plugin is never
 * resolved.
 *
 * <p>The SDK calls are wrapped defensively: a Floodgate or Geyser that is present but mid-reload or otherwise not
 * ready can return a null instance or throw, and a menu open must never fail because the form send hiccuped, so any
 * runtime failure is logged and swallowed rather than thrown into the open path. An answer arrives off the main
 * thread, and the callback the caller supplies does its own entity-thread hop before touching anything.
 */
final class CumulusBedrockScreen extends FormScreen {

    private static final Logger LOG = Logger.getLogger(CumulusBedrockScreen.class.getName());

    /** The last step of every send: the form to the plugin that carries the player's connection. */
    @FunctionalInterface
    interface FormSender {

        /** Sends {@code form} to {@code player}; {@code false} when the plugin does not know that player. */
        boolean send(UUID player, Form form);
    }

    private final FormSender sender;

    CumulusBedrockScreen(FormSender sender) {
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    /** The screen for a server with Floodgate, which carries the Bedrock connection to this backend. */
    static CumulusBedrockScreen viaFloodgate() {
        return new CumulusBedrockScreen(
                (player, form) -> FloodgateApi.getInstance().sendForm(player, form));
    }

    /**
     * The screen for a server that runs Geyser without Floodgate. Geyser keys its connections by the Java uuid the
     * player joined with, so the same uuid reaches it.
     */
    static CumulusBedrockScreen viaGeyser() {
        return new CumulusBedrockScreen((player, form) -> Geyser.api().sendForm(player, form));
    }

    /**
     * Builds the Cumulus form and hands it to the plugin. A plugin that is not ready, or does not know the player,
     * costs the form and nothing else: a menu open must never fail because the send hiccuped.
     */
    @Override
    void send(Player player, BedrockFormRequest request, Consumer<BedrockFormResponse> onResponse) {
        try {
            if (!sender.send(player.getUniqueId(), CumulusForms.toForm(request, onResponse))) {
                LOG.warning("event=bedrock_form_undelivered player=" + player.getName());
            }
        } catch (RuntimeException notReady) {
            LOG.warning("event=bedrock_form_failed player=" + player.getName() + " reason=" + notReady.getMessage());
        }
    }
}
