package com.uxplima.uxmlib.menu.runtime;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.gui.input.TextInput;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The narrow capability the menu engine's {@code input:} step uses to capture a line of text without depending on the
 * concrete text-input seam: it hands over a viewer, an input-point key (the operator's per-key anvil/chat/sign mode is
 * looked up by it), a resolved prompt label and an optional pre-fill, and gets exactly one callback back: the typed
 * line on submit, or {@code onCancel}. Production wires it over {@code TextInput.promptResolved}, and a test can
 * satisfy it with a lambda. Defined here so {@code MenuListener} stays decoupled from the input package, which is a
 * final class with no interface that nothing outside its own package can construct.
 *
 * <p>It is not {@link com.uxplima.uxmlib.menu.property.CatalogTextPrompt}, which an editable property uses, and the
 * two are not one interface because they sit on opposite sides of the text pipeline. This one is asked after
 * resolution: the engine has already rendered the prompt against the open context, placeholders and all, so the
 * {@link Component} it carries is finished and nothing may resolve it twice. That one is asked before: it carries a
 * catalog key and its placeholders for the seam behind it to resolve.
 *
 * <p>The implementation is responsible for delivering both callbacks on the viewer's entity thread, exactly as the
 * text-input seam already does, so the engine's continuation runs where it can safely touch the player and reopen a
 * menu.
 */
@NullMarked
public interface MenuTextPrompt {

    void prompt(
            Player viewer,
            String key,
            Component prompt,
            @Nullable String initialText,
            Consumer<String> onSubmit,
            Runnable onCancel);

    /**
     * As the six-argument form, for a line that may be a secret, a PIN or a password, which must then never be asked
     * for in chat or on a sign, whose lines other plugins receive. Production wires this over {@code
     * TextInput.promptResolved} with the same flag.
     *
     * <p>The default asks through the six-argument form for a line that is not a secret. For a secret it cancels:
     * an implementation that does not override this cannot say where the line is typed, so it is never handed one.
     */
    default void prompt(
            Player viewer,
            String key,
            Component prompt,
            @Nullable String initialText,
            boolean secret,
            Consumer<String> onSubmit,
            Runnable onCancel) {
        if (secret) {
            Logger.getLogger(MenuTextPrompt.class.getName())
                    .warning("event=menu_secret_input_unsupported key=" + key
                            + " reason=the text prompt cannot keep a secret out of chat");
            onCancel.run();
            return;
        }
        prompt(viewer, key, prompt, initialText, onSubmit, onCancel);
    }

    /**
     * The prompt over the text-input seam {@code input} answers, asked for on each prompt so a plugin that builds the
     * seam after the menu listener can hand it over later. A secret goes through as one. While {@code input} answers
     * nothing, every prompt cancels.
     */
    static MenuTextPrompt through(Supplier<@Nullable TextInput> input) {
        Objects.requireNonNull(input, "input");
        return new MenuTextPrompt() {
            @Override
            public void prompt(
                    Player viewer,
                    String key,
                    Component prompt,
                    @Nullable String initialText,
                    Consumer<String> onSubmit,
                    Runnable onCancel) {
                prompt(viewer, key, prompt, initialText, false, onSubmit, onCancel);
            }

            @Override
            public void prompt(
                    Player viewer,
                    String key,
                    Component prompt,
                    @Nullable String initialText,
                    boolean secret,
                    Consumer<String> onSubmit,
                    Runnable onCancel) {
                TextInput seam = input.get();
                if (seam == null) {
                    onCancel.run();
                    return;
                }
                seam.promptResolved(viewer, key, prompt, initialText, secret, onSubmit, onCancel);
            }
        };
    }
}
