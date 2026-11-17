package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.menu.spec.ClickKind;
import com.uxplima.uxmlib.menu.spec.Continuation;
import com.uxplima.uxmlib.menu.spec.MenuItemSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import com.uxplima.uxmlib.menu.spec.MenuSpecWriter;
import com.uxplima.uxmlib.menu.spec.Ref;
import org.junit.jupiter.api.Test;

/**
 * A menu file can mark an {@code input:} step as a secret, the mark survives being written back, and a text prompt
 * that cannot keep a secret out of chat is never handed one.
 */
class SecretInputStepTest {

    private static final String MENU = """
            title = "Warp"
            rows = 1
            items {
              password {
                slot = 0
                material = PAPER
                click {
                  left = [{ do = "input:pwarp.password", prompt = "Password", secret = true }, "pwarp:join"]
                  right = [{ do = "input:pwarp.rename", prompt = "New name" }, "pwarp:rename"]
                }
              }
            }
            """;

    private final Player viewer = (Player) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            });

    @Test
    void aSecretStepIsReadAndWrittenBackAsOne() {
        MenuSpec read = new MenuSpecLoader().parse(MENU);
        MenuSpec again = new MenuSpecLoader().parse(new MenuSpecWriter().write(read));

        for (MenuSpec spec : List.of(read, again)) {
            assertThat(input(spec, ClickKind.LEFT).secret()).isTrue();
            assertThat(input(spec, ClickKind.LEFT).key()).isEqualTo("pwarp.password");
            assertThat(input(spec, ClickKind.RIGHT).secret()).isFalse();
        }
    }

    @Test
    void aPromptThatCannotKeepASecretCancelsOneAndAsksForAnyOtherLine() {
        List<String> asked = new ArrayList<>();
        MenuTextPrompt plain = (player, key, prompt, initial, onSubmit, onCancel) -> asked.add(key);
        List<String> cancelled = new ArrayList<>();

        plain.prompt(
                viewer,
                "pwarp.password",
                Component.text("Password"),
                null,
                true,
                line -> {},
                () -> cancelled.add("pwarp.password"));
        plain.prompt(
                viewer,
                "pwarp.rename",
                Component.text("New name"),
                null,
                false,
                line -> {},
                () -> cancelled.add("pwarp.rename"));

        assertThat(asked).containsExactly("pwarp.rename");
        assertThat(cancelled).containsExactly("pwarp.password");
    }

    @Test
    void aPromptOverNoSeamYetCancels() {
        MenuTextPrompt through = MenuTextPrompt.through(() -> null);
        List<String> cancelled = new ArrayList<>();
        Consumer<String> submitted = line -> cancelled.add("submitted " + line);

        through.prompt(
                viewer,
                "pwarp.password",
                Component.text("Password"),
                null,
                true,
                submitted,
                () -> cancelled.add("secret"));
        through.prompt(
                viewer, "pwarp.rename", Component.text("New name"), null, submitted, () -> cancelled.add("plain"));

        assertThat(cancelled).containsExactly("secret", "plain");
    }

    private static Continuation.Input input(MenuSpec spec, ClickKind kind) {
        MenuItemSpec item = spec.items().get("password");
        assertThat(item).isNotNull();
        Ref first = Objects.requireNonNull(item).click().actionsFor(kind).getFirst();
        Continuation step = first.continuation().orElseThrow();
        assertThat(step).isInstanceOf(Continuation.Input.class);
        return (Continuation.Input) step;
    }
}
