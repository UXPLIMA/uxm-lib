package com.uxplima.uxmlib.menu.binding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.uxplima.uxmlib.menu.spec.MenuSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import org.junit.jupiter.api.Test;

/** A menu that draws what the engine fills itself loads, and a misspelled id is still reported. */
class MenuBindingsValidationTest {

    private final MenuBindings bindings = new MenuBindings();

    @Test
    void argumentsPromptAnswersDroppedItemsAndFormFieldsAreKnown() {
        MenuSpec spec = new MenuSpecLoader().parse("""
                title = "Search: %argument_words%"
                rows = 1
                items {
                  result {
                    slot = 0, material = PAPER, name = "%argument_words% (%input%)"
                    lore = ["%drag_name% x%drag_amount% %drag_material%", "Offer: %offer%"]
                  }
                }
                bedrock {
                  title = "Shop"
                  widgets = [
                    { type = label, text = "Pick one" },
                    { type = dropdown, name = offer, label = "Offer", options = ["Sword", "Shield"] }
                  ]
                }
                """);

        assertThat(bindings.validate(List.of(spec))).isEmpty();
    }

    @Test
    void aMisspelledIdIsStillReported() {
        bindings.placeholder("balance", ctx -> "0");
        MenuSpec spec = new MenuSpecLoader().parse("""
                title = "%argument_% %argumnet_words%"
                rows = 1
                items {
                  result {
                    slot = 0, material = PAPER, name = "%balance% %balanse%"
                    lore = ["%drag_nmae% %inputs% %offer%"]
                  }
                }
                """);

        assertThat(bindings.validate(List.of(spec)))
                .containsExactlyInAnyOrder("argument_", "argumnet_words", "balanse", "drag_nmae", "inputs", "offer");
    }
}
