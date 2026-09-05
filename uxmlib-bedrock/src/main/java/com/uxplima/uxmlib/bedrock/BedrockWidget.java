package com.uxplima.uxmlib.bedrock;

import java.util.List;
import java.util.Objects;

/**
 * One widget of a native CustomForm: the form-native controls a Bedrock viewer sees where a Java viewer sees a
 * chest. A {@link Label} is display-only; every other widget carries a {@code name}, so a caller can bind the
 * value the viewer submitted back to the control that asked for it. The type names no Bukkit and no Cumulus
 * class, so a caller parses a file into it and the SDK-free {@code BedrockScreen} takes it, and neither one
 * pulls the form library in.
 *
 * <p>The label, text, placeholder and option strings are carried verbatim. A caller that resolves a placeholder
 * or a message key does it when it draws the form, so nothing here is resolved when the file is read.
 */
public sealed interface BedrockWidget
        permits BedrockWidget.Label,
                BedrockWidget.Input,
                BedrockWidget.Dropdown,
                BedrockWidget.Slider,
                BedrockWidget.Toggle {

    /**
     * A read-only line of text shown in the form, with no value of its own, the CustomForm {@code label}
     * component. It binds nothing, so it carries no {@code name}.
     *
     * @param text the label text (may carry {@code %token%}/{@code @key}); never {@code null}
     */
    record Label(String text) implements BedrockWidget {
        public Label {
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * A single-line text field whose typed value binds to {@code %name%}.
     *
     * @param name the placeholder name its value binds to; never {@code null}
     * @param label the field label; never {@code null}
     * @param placeholder the greyed-out hint shown in an empty field; never {@code null}
     * @param defaultText the field's pre-fill; never {@code null}
     */
    record Input(String name, String label, String placeholder, String defaultText) implements BedrockWidget {
        public Input {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(placeholder, "placeholder");
            Objects.requireNonNull(defaultText, "defaultText");
        }
    }

    /**
     * A drop-down picker whose selected option string binds to {@code %name%}.
     *
     * @param name the placeholder name the selected option binds to; never {@code null}
     * @param label the picker label; never {@code null}
     * @param options the options in order; never {@code null}, copied defensively
     * @param defaultIndex the zero-based option selected on open
     */
    record Dropdown(String name, String label, List<String> options, int defaultIndex) implements BedrockWidget {
        public Dropdown {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(label, "label");
            options = List.copyOf(Objects.requireNonNull(options, "options"));
        }
    }

    /**
     * An integer slider whose chosen value binds to {@code %name%}.
     *
     * @param name the placeholder name the chosen value binds to; never {@code null}
     * @param label the slider label; never {@code null}
     * @param min the least value the slider allows
     * @param max the greatest value the slider allows
     * @param step the increment between stops
     * @param defaultValue the value the slider rests at on open
     */
    record Slider(String name, String label, int min, int max, int step, int defaultValue) implements BedrockWidget {
        public Slider {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(label, "label");
        }
    }

    /**
     * An on/off switch whose boolean value binds to {@code %name%}.
     *
     * @param name the placeholder name the switch value binds to; never {@code null}
     * @param label the switch label; never {@code null}
     * @param defaultValue whether the switch is on when the form opens
     */
    record Toggle(String name, String label, boolean defaultValue) implements BedrockWidget {
        public Toggle {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(label, "label");
        }
    }
}
