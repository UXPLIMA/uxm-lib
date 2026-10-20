package com.uxplima.uxmlib.bedrock;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import org.jspecify.annotations.Nullable;

/**
 * A screen that asks {@link BedrockScreen#forServer} again until Floodgate answers, then keeps that one. The reason
 * is the one {@link ResolvingBedrockDetector} gives: a plugin enabled at {@code STARTUP} runs before Floodgate.
 */
final class ResolvingBedrockScreen implements BedrockScreen {

    private final Server server;
    private volatile @Nullable BedrockScreen found;

    ResolvingBedrockScreen(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    private BedrockScreen current() {
        BedrockScreen known = found;
        if (known != null) {
            return known;
        }
        BedrockScreen now = BedrockScreen.forServer(server);
        if (now != NONE) {
            found = now;
        }
        return now;
    }

    @Override
    public void sendSimpleForm(
            Player player, String title, @Nullable String content, List<BedrockButton> buttons, IntConsumer onSelect) {
        current().sendSimpleForm(player, title, content, buttons, onSelect);
    }

    @Override
    public void sendModalForm(
            Player player,
            String title,
            @Nullable String content,
            String button1,
            String button2,
            Runnable onButton1,
            Runnable onButton2) {
        current().sendModalForm(player, title, content, button1, button2, onButton1, onButton2);
    }

    @Override
    public void sendInputForm(
            Player player,
            String title,
            String inputLabel,
            @Nullable String initial,
            Consumer<String> onSubmit,
            Runnable onClose) {
        current().sendInputForm(player, title, inputLabel, initial, onSubmit, onClose);
    }

    @Override
    public void sendCustomForm(
            Player player,
            String title,
            @Nullable String content,
            List<BedrockWidget> widgets,
            Consumer<Map<String, String>> onSubmit,
            Runnable onClose) {
        current().sendCustomForm(player, title, content, widgets, onSubmit, onClose);
    }
}
