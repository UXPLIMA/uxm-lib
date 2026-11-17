package com.uxplima.uxmlib.bedrock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.response.CustomFormResponse;
import org.geysermc.cumulus.util.FormImage;

/**
 * Turns a {@link BedrockFormRequest} into the Cumulus form Floodgate and Geyser send, and the player's answer back
 * into a {@link BedrockFormResponse}. Every Bedrock screen in this library builds its forms here, and a plugin that
 * sends forms itself does the same, such as on a proxy that runs Geyser for its backends.
 *
 * <p>This class names Cumulus, which only a server or proxy with Floodgate or Geyser carries. Call it only where
 * one of them runs; a plugin that may run without either names it past its own plugin-present check.
 */
public final class CumulusForms {

    private CumulusForms() {}

    /**
     * The Cumulus form for {@code request}, which hands {@code onResponse} the answer when the player gives one.
     *
     * <p>A Simple or Modal form answers {@link BedrockFormResponse.Tapped}, a Custom form
     * {@link BedrockFormResponse.Submitted} with each value widget's value by name, and every form
     * {@link BedrockFormResponse#CLOSED} when the player closes it or the client's answer cannot be read. The
     * answer arrives on the thread Floodgate or Geyser reads it on, never the main thread.
     *
     * @param request the form; never {@code null}
     * @param onResponse the receiver of the answer; never {@code null}
     * @return the form to hand to Floodgate's or Geyser's {@code sendForm}
     */
    public static Form toForm(BedrockFormRequest request, Consumer<BedrockFormResponse> onResponse) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(onResponse, "onResponse");
        Runnable closed = () -> onResponse.accept(BedrockFormResponse.CLOSED);
        return switch (request) {
            case BedrockFormRequest.Simple simple -> {
                SimpleForm.Builder builder = SimpleForm.builder().title(simple.title());
                if (simple.content() != null) {
                    builder.content(simple.content());
                }
                for (BedrockButton button : simple.buttons()) {
                    BedrockImage image = button.image();
                    if (image == null) {
                        builder.button(button.text());
                    } else {
                        builder.button(
                                button.text(),
                                image.kind() == BedrockImage.Kind.PATH ? FormImage.Type.PATH : FormImage.Type.URL,
                                image.value());
                    }
                }
                builder.validResultHandler((form, response) ->
                        onResponse.accept(new BedrockFormResponse.Tapped(response.clickedButtonId())));
                builder.closedOrInvalidResultHandler(closed);
                yield builder.build();
            }
            case BedrockFormRequest.Modal modal -> {
                ModalForm.Builder builder = ModalForm.builder().title(modal.title());
                if (modal.content() != null) {
                    builder.content(modal.content());
                }
                builder.button1(modal.button1());
                builder.button2(modal.button2());
                builder.validResultHandler((form, response) ->
                        onResponse.accept(new BedrockFormResponse.Tapped(response.clickedButtonId())));
                builder.closedOrInvalidResultHandler(closed);
                yield builder.build();
            }
            case BedrockFormRequest.Custom custom -> {
                CustomForm.Builder builder = CustomForm.builder().title(custom.title());
                boolean contentPresent = custom.content() != null;
                if (custom.content() != null) {
                    builder.label(custom.content());
                }
                for (BedrockWidget widget : custom.widgets()) {
                    addWidget(builder, widget);
                }
                builder.validResultHandler((form, response) -> onResponse.accept(
                        new BedrockFormResponse.Submitted(collect(custom.widgets(), response, contentPresent))));
                builder.closedOrInvalidResultHandler(closed);
                yield builder.build();
            }
        };
    }

    /** Add one widget as its matching Cumulus component; a {@link BedrockWidget.Label} is a display-only label. */
    private static void addWidget(CustomForm.Builder builder, BedrockWidget widget) {
        switch (widget) {
            case BedrockWidget.Label label -> builder.label(label.text());
            case BedrockWidget.Input input -> builder.input(input.label(), input.placeholder(), input.defaultText());
            case BedrockWidget.Dropdown dropdown ->
                builder.dropdown(dropdown.label(), dropdown.options(), dropdown.defaultIndex());
            case BedrockWidget.Slider slider ->
                builder.slider(slider.label(), slider.min(), slider.max(), slider.step(), slider.defaultValue());
            case BedrockWidget.Toggle toggle -> builder.toggle(toggle.label(), toggle.defaultValue());
        }
    }

    /**
     * Collect each value widget's submitted value into a {@code name -> value} map, reading each by its component
     * index. A Cumulus CustomForm response is indexed by ABSOLUTE component position, and a label, including the
     * intro-{@code content} label added first when present, occupies an index while carrying no value. So the index
     * starts past the content label and advances for every widget, but a label reads nothing; an input reads its
     * string, a dropdown its selected option string, a slider its value formatted without a trailing {@code .0}, and a
     * toggle {@code true}/{@code false}. This keeps widget N's value read from the correct response slot.
     */
    private static Map<String, String> collect(
            List<BedrockWidget> widgets, CustomFormResponse response, boolean contentPresent) {
        Map<String, String> values = new LinkedHashMap<>();
        int index = contentPresent ? 1 : 0;
        for (BedrockWidget widget : widgets) {
            switch (widget) {
                case BedrockWidget.Label ignored -> {
                    // A label is display-only: it consumes a response index but yields no value to bind.
                }
                case BedrockWidget.Input input -> values.put(input.name(), response.asInput(index));
                case BedrockWidget.Dropdown dropdown ->
                    values.put(dropdown.name(), optionAt(dropdown, response.asDropdown(index)));
                case BedrockWidget.Slider slider -> values.put(slider.name(), formatSlider(response.asSlider(index)));
                case BedrockWidget.Toggle toggle -> values.put(toggle.name(), String.valueOf(response.asToggle(index)));
            }
            index++;
        }
        return values;
    }

    /** The selected dropdown option string, or empty when the returned index is out of the option range. */
    private static String optionAt(BedrockWidget.Dropdown dropdown, int selected) {
        List<String> options = dropdown.options();
        return selected >= 0 && selected < options.size() ? options.get(selected) : "";
    }

    /**
     * A slider value as a string, dropping the trailing {@code .0} of an integral value (sliders are
     * integer-bounded).
     */
    private static String formatSlider(float value) {
        if (Float.isFinite(value) && value == Math.floor(value)) {
            return Long.toString((long) value);
        }
        return Float.toString(value);
    }
}
