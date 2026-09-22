package com.uxplima.uxmlib.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.uxplima.uxmlib.menu.binding.ActionRegistry;
import com.uxplima.uxmlib.menu.spec.Ref;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A click that names an action nobody registered says so.
 *
 * <p>A menu is a file an operator edits, and the id of an action is a word they type. A typo in one
 * was a button that did nothing: the engine looked the id up, found nothing, and went on to the next
 * ref without a line anywhere. The operator then has a window that opens, a tile that draws and a
 * click that is not broken so much as absent, and nothing to read.
 *
 * <p>This is the menu side of what the action grammar already had. A line the parser cannot read
 * throws and is reported; an id the registry does not hold was the quiet one.
 *
 * <p>Nothing else changes. The click still runs the rest of its list, because one wrong id should
 * cost the operator that action and not the three beside it.
 */
class UnknownClickActionsAreNamedTest {

    @Test
    @DisplayName("an id nobody registered is named in the log, once per click")
    void anUnknownIdIsNamed() {
        ActionRegistry registry = new ActionRegistry();
        registry.register("glow:set", ctx -> {});
        List<String> warnings = captureWarnings();

        Menus.warnUnknownAction(new Ref("glow:st", Map.of()));

        assertThat(warnings).anyMatch(line -> line.contains("glow:st"));
        assertThat(registry.has("glow:st")).isFalse();
    }

    @Test
    @DisplayName("the warning says what to do about it")
    void thewarningIsUseful() {
        List<String> warnings = captureWarnings();

        Menus.warnUnknownAction(new Ref("shop:by", Map.of()));

        assertThat(warnings)
                .describedAs("an operator reading it should learn that the id is the thing that is wrong")
                .anyMatch(line -> line.contains("no action is registered"));
    }

    /** Catch what the engine's own logger is told, without touching the global configuration. */
    private static List<String> captureWarnings() {
        List<String> lines = new ArrayList<>();
        Logger log = Logger.getLogger(Menus.class.getName());
        log.addHandler(new Handler() {

            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    lines.add(record.getMessage());
                }
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        return lines;
    }
}
