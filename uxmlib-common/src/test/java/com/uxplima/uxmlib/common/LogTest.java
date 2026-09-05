package com.uxplima.uxmlib.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The logging port and its two bindings: the one over {@link Logger} that a Bukkit plugin hands it, and the
 * silent constant a test uses. What is asserted is the placeholder contract ({@code {}}, left to right, SLF4J
 * style), the level each method writes at, and that a suppressed level builds no message.
 */
class LogTest {

    private Logger logger;
    private List<LogRecord> written;

    @BeforeEach
    void setUp() {
        written = new ArrayList<>();
        logger = Logger.getLogger("uxmlib-log-test-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(new Handler() {

            @Override
            public void publish(LogRecord record) {
                written.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
    }

    @Test
    void eachPlaceholderTakesTheNextArgumentInOrder() {
        Log.of(logger).info("menu {} opened for {}", "shop", "Notch");

        assertThat(written).singleElement().extracting(LogRecord::getMessage).isEqualTo("menu shop opened for Notch");
    }

    @Test
    void aSurplusArgumentIsDropped() {
        // A diagnostic line prints what it can. Throwing here would hide the failure the caller is reporting.
        Log.of(logger).info("one {}", "first", "second");

        assertThat(written).singleElement().extracting(LogRecord::getMessage).isEqualTo("one first");
    }

    @Test
    void aSurplusPlaceholderIsLeftStanding() {
        Log.of(logger).info("{} and {}", "first");

        assertThat(written).singleElement().extracting(LogRecord::getMessage).isEqualTo("first and {}");
    }

    @Test
    void aMessageWithNoArgumentKeepsItsBracesVerbatim() {
        // The braces of a JSON fragment must survive: nothing is formatted when there is nothing to substitute.
        Log.of(logger).info("{\"slot\":1}");

        assertThat(written).singleElement().extracting(LogRecord::getMessage).isEqualTo("{\"slot\":1}");
    }

    @Test
    void aNullArgumentPrintsAsNullRatherThanFailing() {
        Log.of(logger).warn("value {}", new Object[] {null});

        assertThat(written).singleElement().extracting(LogRecord::getMessage).isEqualTo("value null");
    }

    @Test
    void infoWarnAndDebugWriteAtTheirOwnLevels() {
        Log log = Log.of(logger);

        log.info("i");
        log.warn("w");
        log.debug("d");

        assertThat(written).extracting(LogRecord::getLevel).containsExactly(Level.INFO, Level.WARNING, Level.FINE);
    }

    @Test
    void errorCarriesTheCauseForTheOperator() {
        RuntimeException cause = new RuntimeException("broke");

        Log.of(logger).error("save failed", cause);

        assertThat(written).singleElement().satisfies(record -> {
            assertThat(record.getLevel()).isEqualTo(Level.SEVERE);
            assertThat(record.getMessage()).isEqualTo("save failed");
            assertThat(record.getThrown()).isSameAs(cause);
        });
    }

    @Test
    void aSuppressedLevelWritesNothing() {
        logger.setLevel(Level.INFO);

        Log.of(logger).debug("expensive {}", "detail");

        assertThat(written).isEmpty();
    }

    @Test
    void noneWritesNothingAndNeverThrows() {
        assertThatCode(() -> {
                    Log.NONE.info("i {}", 1);
                    Log.NONE.warn("w {}", 2);
                    Log.NONE.debug("d {}", 3);
                    Log.NONE.error("e", new RuntimeException("ignored"));
                })
                .doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings("NullAway") // intentionally passes null to assert the requireNonNull guard fires
    void aBindingRefusesANullLogger() {
        assertThatNullPointerException().isThrownBy(() -> Log.of(null)).withMessage("logger");
    }
}
