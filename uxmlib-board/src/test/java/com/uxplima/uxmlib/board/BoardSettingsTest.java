package com.uxplima.uxmlib.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZoneId;
import java.util.EnumSet;

import com.uxplima.uxmlib.config.HoconConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BoardSettingsTest {

    @TempDir
    Path folder;

    private HoconConfig config(String text) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, text);
        return HoconConfig.load(file);
    }

    @Test
    void theBlockIsReadAsWritten() throws IOException {
        BoardSettings read = BoardSettings.read(config("""
                        leaderboard {
                            size = 5
                            refresh-seconds = 30
                            periods = [alltime, weekly]
                            time-zone = "Europe/Istanbul"
                            week-starts = sunday
                            keep-windows = 3
                            empty-head = "Notch"
                        }
                        """), "leaderboard");

        assertThat(read.size()).isEqualTo(5);
        assertThat(read.refresh()).isEqualTo(Duration.ofSeconds(30));
        assertThat(read.periods()).containsExactlyInAnyOrder(BoardPeriod.ALLTIME, BoardPeriod.WEEKLY);
        assertThat(read.calendar()).isEqualTo(new BoardCalendar(ZoneId.of("Europe/Istanbul"), DayOfWeek.SUNDAY));
        assertThat(read.keepWindows()).isEqualTo(3);
        assertThat(read.emptyHead()).isEqualTo("Notch");
    }

    @Test
    void aBlockLeftOutKeepsEveryPeriod() throws IOException {
        BoardSettings read = BoardSettings.read(config("other = 1"), "leaderboard");

        assertThat(read.periods()).isEqualTo(EnumSet.allOf(BoardPeriod.class));
        assertThat(read.size()).isEqualTo(10);
        assertThat(read.emptyHead()).isEqualTo(BoardSettings.STEVE);
    }

    @Test
    void aWrongValueNamesTheKey() throws IOException {
        assertThatThrownBy(() -> BoardSettings.read(config("leaderboard { periods = [yearly] }"), "leaderboard"))
                .hasMessageContaining("leaderboard.periods");
        assertThatThrownBy(() -> BoardSettings.read(config("leaderboard { time-zone = Mars }"), "leaderboard"))
                .hasMessageContaining("leaderboard.time-zone");
        assertThatThrownBy(() -> BoardSettings.read(config("leaderboard { size = 0 }"), "leaderboard"))
                .hasMessageContaining("leaderboard.size");
    }
}
