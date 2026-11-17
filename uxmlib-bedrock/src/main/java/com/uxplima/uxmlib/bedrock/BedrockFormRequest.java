package com.uxplima.uxmlib.bedrock;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A Bedrock form as plain data: what a {@link BedrockScreen} sends, before any SDK sees it. It names no Cumulus
 * type, so it can cross a wire. {@link BedrockFormCodec} turns it into bytes, and {@link CumulusForms} turns it into
 * the Cumulus form wherever Floodgate or Geyser runs.
 *
 * <p>A text-input prompt is a {@link Custom} form with one {@link BedrockWidget.Input}.
 */
public sealed interface BedrockFormRequest {

    /** The form's title as plain text. */
    String title();

    /**
     * A list of buttons; the answer is the tapped button's index.
     *
     * @param title the title; never {@code null}
     * @param content the body text above the buttons, or {@code null} for none
     * @param buttons the buttons in order; never {@code null}, copied
     */
    record Simple(String title, @Nullable String content, List<BedrockButton> buttons) implements BedrockFormRequest {
        public Simple {
            Objects.requireNonNull(title, "title");
            buttons = List.copyOf(Objects.requireNonNull(buttons, "buttons"));
        }
    }

    /**
     * A window with exactly two buttons; the answer is index {@code 0} for the first and {@code 1} for the second.
     *
     * @param title the title; never {@code null}
     * @param content the body text above the buttons, or {@code null} for none
     * @param button1 the first button's label; never {@code null}
     * @param button2 the second button's label; never {@code null}
     */
    record Modal(String title, @Nullable String content, String button1, String button2) implements BedrockFormRequest {
        public Modal {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(button1, "button1");
            Objects.requireNonNull(button2, "button2");
        }
    }

    /**
     * A list of widgets; the answer is each value widget's value by its name.
     *
     * @param title the title; never {@code null}
     * @param content an intro line above the widgets, or {@code null} for none
     * @param widgets the widgets in order; never {@code null}, copied
     */
    record Custom(String title, @Nullable String content, List<BedrockWidget> widgets) implements BedrockFormRequest {
        public Custom {
            Objects.requireNonNull(title, "title");
            widgets = List.copyOf(Objects.requireNonNull(widgets, "widgets"));
        }
    }
}
