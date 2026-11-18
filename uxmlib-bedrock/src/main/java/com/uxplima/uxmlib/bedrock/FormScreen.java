package com.uxplima.uxmlib.bedrock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import org.bukkit.entity.Player;

import org.jspecify.annotations.Nullable;

/**
 * A {@link BedrockScreen} that turns each call into a {@link BedrockFormRequest} and the answer back into the
 * caller's callbacks. A subclass only sends: through Floodgate or Geyser on this server, or through a plugin's relay.
 * So every screen reads an answer the same way, whichever way the form went.
 */
abstract class FormScreen implements BedrockScreen {

    /** The name the single field of an input prompt answers under. */
    private static final String INPUT = "input";

    /** Sends {@code request} to {@code player} and hands {@code onResponse} the answer when one comes. */
    abstract void send(Player player, BedrockFormRequest request, Consumer<BedrockFormResponse> onResponse);

    @Override
    public final void sendSimpleForm(
            Player player, String title, @Nullable String content, List<BedrockButton> buttons, IntConsumer onSelect) {
        sendSimpleForm(player, title, content, buttons, onSelect, () -> {});
    }

    @Override
    public final void sendSimpleForm(
            Player player,
            String title,
            @Nullable String content,
            List<BedrockButton> buttons,
            IntConsumer onSelect,
            Runnable onClose) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(onSelect, "onSelect");
        Objects.requireNonNull(onClose, "onClose");
        BedrockFormRequest.Simple request = new BedrockFormRequest.Simple(title, content, buttons);
        int size = request.buttons().size();
        send(player, request, response -> {
            if (response instanceof BedrockFormResponse.Tapped(int index) && index >= 0 && index < size) {
                onSelect.accept(index);
            } else {
                onClose.run();
            }
        });
    }

    @Override
    public final void sendModalForm(
            Player player,
            String title,
            @Nullable String content,
            String button1,
            String button2,
            Runnable onButton1,
            Runnable onButton2) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(onButton1, "onButton1");
        Objects.requireNonNull(onButton2, "onButton2");
        send(player, new BedrockFormRequest.Modal(title, content, button1, button2), response -> {
            // A modal has no close of its own to report: closing it answers neither button.
            if (response instanceof BedrockFormResponse.Tapped(int index)) {
                (index == 0 ? onButton1 : onButton2).run();
            }
        });
    }

    @Override
    public final void sendInputForm(
            Player player,
            String title,
            String inputLabel,
            @Nullable String initial,
            Consumer<String> onSubmit,
            Runnable onClose) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(onSubmit, "onSubmit");
        Objects.requireNonNull(onClose, "onClose");
        BedrockWidget field = new BedrockWidget.Input(INPUT, inputLabel, "", initial != null ? initial : "");
        send(player, new BedrockFormRequest.Custom(title, null, List.of(field)), response -> {
            String typed = response instanceof BedrockFormResponse.Submitted(Map<String, String> values)
                    ? values.get(INPUT)
                    : null;
            if (typed != null) {
                onSubmit.accept(typed);
            } else {
                onClose.run();
            }
        });
    }

    @Override
    public final void sendCustomForm(
            Player player,
            String title,
            @Nullable String content,
            List<BedrockWidget> widgets,
            Consumer<Map<String, String>> onSubmit,
            Runnable onClose) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(onSubmit, "onSubmit");
        Objects.requireNonNull(onClose, "onClose");
        BedrockFormRequest.Custom request = new BedrockFormRequest.Custom(title, content, widgets);
        send(player, request, response -> {
            if (response instanceof BedrockFormResponse.Submitted(Map<String, String> values)) {
                onSubmit.accept(fieldsOf(request, values));
            } else {
                onClose.run();
            }
        });
    }

    /**
     * The submitted values of the form's own value widgets, in widget order. A value under a name the form does not
     * have is dropped and a missing one reads empty, so a relayed answer cannot add a field the form never asked for.
     */
    private static Map<String, String> fieldsOf(BedrockFormRequest.Custom request, Map<String, String> values) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (BedrockWidget widget : request.widgets()) {
            String name =
                    switch (widget) {
                        case BedrockWidget.Label ignored -> null;
                        case BedrockWidget.Input input -> input.name();
                        case BedrockWidget.Dropdown dropdown -> dropdown.name();
                        case BedrockWidget.Slider slider -> slider.name();
                        case BedrockWidget.Toggle toggle -> toggle.name();
                    };
            if (name != null) {
                fields.put(name, values.getOrDefault(name, ""));
            }
        }
        return fields;
    }
}
