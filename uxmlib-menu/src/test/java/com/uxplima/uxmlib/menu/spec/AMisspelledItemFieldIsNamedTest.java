package com.uxplima.uxmlib.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * An item field nobody reads is named in the log when the window loads.
 *
 * <p>Tried on 2026-09-23: {@code materail = DIAMOND} loaded without a word and the tile was drawn as stone, because
 * the field was never read. The loader already names a gesture nobody knows; a field is one word too, and as easy
 * to misspell. The item still loads, as the rest of the grammar does.
 */
class AMisspelledItemFieldIsNamedTest {

    private final List<String> logged = new ArrayList<>();
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            logged.add(record.getMessage());
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    };

    @BeforeEach
    void listen() {
        Logger.getLogger("").addHandler(handler);
    }

    @AfterEach
    void stop() {
        Logger.getLogger("").removeHandler(handler);
    }

    @Test
    @DisplayName("a misspelled field is named, with the item it sits on, and the item still loads")
    void aMisspelledFieldIsNamed() {
        MenuSpec spec = new MenuSpecLoader().parse("rows = 3\nitems { gem { slot = 1, materail = DIAMOND } }");

        assertThat(spec.items()).containsKey("gem");
        assertThat(logged).anySatisfy(line -> assertThat(line).contains("materail", "gem"));
    }

    @Test
    @DisplayName("an item written with the fields the loader reads says nothing")
    void aWellWrittenItemSaysNothing() {
        new MenuSpecLoader().parse("""
                        rows = 3
                        items {
                          gem {
                            slot = 1, priority = 2, material = DIAMOND, name = "x", lore = ["y"], lore-mode = replace
                            view = ["perm:a"], permission = "a", update = true, click { left = ["close"] }
                          }
                          many { slots = ["2-3"], material = STONE, pages = "1" }
                        }
                        """);

        assertThat(logged).noneSatisfy(line -> assertThat(line).contains("no field"));
    }
}
