package com.uxplima.uxmlib.text.language;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A corrected line reaches a server that already runs the plugin. The language files were written once, on the
 * first start, so a line a release corrected stayed wrong on every server that had the old one: the operator's
 * file won over the jar, although the operator never wrote that line.
 */
class LanguageFileUpdatesTest {

    private static final String OLD = """
            # The words a player reads.
            prefix = "<b>Old</b>"

            admin {
              # Said when the slot is taken.
              taken = "<red>Slot <slot> is taken."
              gone  = "<red>Gone."
            }
            """;

    private static final String NEW = """
            # The words a player reads.
            prefix = "<b>New</b>"

            admin {
              # Said when the slot is taken.
              taken = "<red>No free slot."
              gone  = "<red>Gone."
            }
            """;

    @Test
    @DisplayName("a fresh folder gets every shipped file, and the copy the next release compares against")
    void aFreshFolderGetsTheFilesAndTheirBaseline(@TempDir Path root) throws Exception {
        ship(root, NEW);
        Path folder = root.resolve("plugin/messages");

        LanguageFileUpdates.Report report = LanguageFileUpdates.bringUpToDate(loaderOver(root), "messages", folder);

        assertThat(report.written()).containsExactly(folder.resolve("messages_en.conf"));
        assertThat(report.updated()).isEmpty();
        assertThat(Files.readString(folder.resolve("messages_en.conf"))).isEqualTo(NEW);
        assertThat(Files.readString(folder.resolve(".defaults/messages_en.conf")))
                .isEqualTo(NEW);
    }

    @Test
    @DisplayName("a line the operator never touched takes the corrected words, and the rest of the file stays as it is")
    void anUntouchedLineIsCorrected(@TempDir Path root) throws Exception {
        Path folder = installed(root, OLD, OLD);
        ship(root, NEW);

        LanguageFileUpdates.Report report = LanguageFileUpdates.bringUpToDate(loaderOver(root), "messages", folder);

        assertThat(Files.readString(folder.resolve("messages_en.conf"))).isEqualTo(NEW);
        assertThat(report.updated()).containsExactly("messages_en.conf: prefix", "messages_en.conf: admin.taken");
        assertThat(Files.readString(folder.resolve(".defaults/messages_en.conf")))
                .isEqualTo(NEW);
    }

    @Test
    @DisplayName("a line the operator wrote is theirs, whatever the release says")
    void anEditedLineIsKept(@TempDir Path root) throws Exception {
        String edited = OLD.replace("<red>Slot <slot> is taken.", "<gold>Full, sorry.");
        Path folder = installed(root, edited, OLD);
        ship(root, NEW);

        LanguageFileUpdates.bringUpToDate(loaderOver(root), "messages", folder);

        String now = Files.readString(folder.resolve("messages_en.conf"));
        assertThat(now).contains("taken = \"<gold>Full, sorry.\"");
        assertThat(now).contains("prefix = \"<b>New</b>\"");
    }

    @Test
    @DisplayName("a file written before the baseline existed changes nothing, and keeps a baseline from now on")
    void noBaselineChangesNothing(@TempDir Path root) throws Exception {
        Path folder = root.resolve("plugin/messages");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("messages_en.conf"), OLD);
        ship(root, NEW);

        LanguageFileUpdates.Report report = LanguageFileUpdates.bringUpToDate(loaderOver(root), "messages", folder);

        assertThat(report.updated()).isEmpty();
        assertThat(Files.readString(folder.resolve("messages_en.conf"))).isEqualTo(OLD);
        assertThat(Files.readString(folder.resolve(".defaults/messages_en.conf")))
                .isEqualTo(NEW);
    }

    @Test
    @DisplayName("a second start with nothing new changes nothing")
    void aSecondStartChangesNothing(@TempDir Path root) throws Exception {
        Path folder = installed(root, OLD, OLD);
        ship(root, NEW);
        LanguageFileUpdates.bringUpToDate(loaderOver(root), "messages", folder);

        LanguageFileUpdates.Report again = LanguageFileUpdates.bringUpToDate(loaderOver(root), "messages", folder);

        assertThat(again.written()).isEmpty();
        assertThat(again.updated()).isEmpty();
        assertThat(Files.readString(folder.resolve("messages_en.conf"))).isEqualTo(NEW);
    }

    @Test
    @DisplayName("quoted and dotted keys, a value written with a colon and a one-line list are all found")
    void everyKeyShapeIsFound(@TempDir Path root) throws Exception {
        String old = """
                "menu.title" = "Old title"
                join.welcome: "Old welcome"
                lore = ["a", "b"]
                """;
        String shipped = """
                "menu.title" = "New title"
                join.welcome: "New welcome"
                lore = ["a", "c"]
                """;
        Path folder = installed(root, old, old);
        ship(root, shipped);

        LanguageFileUpdates.bringUpToDate(loaderOver(root), "messages", folder);

        assertThat(Files.readString(folder.resolve("messages_en.conf"))).isEqualTo(shipped);
    }

    @Test
    @DisplayName("a value written across several lines is never touched")
    void aMultiLineValueIsLeftAlone(@TempDir Path root) throws Exception {
        String old = "text = \"\"\"\nkey = \"inside\"\n\"\"\"\nafter = \"one\"\n";
        String shipped = "text = \"\"\"\nkey = \"changed\"\n\"\"\"\nafter = \"two\"\n";
        Path folder = installed(root, old, old);
        ship(root, shipped);

        LanguageFileUpdates.bringUpToDate(loaderOver(root), "messages", folder);

        assertThat(Files.readString(folder.resolve("messages_en.conf")))
                .isEqualTo("text = \"\"\"\nkey = \"inside\"\n\"\"\"\nafter = \"two\"\n");
    }

    @Test
    @DisplayName("a file written with Windows line endings keeps them")
    void windowsLineEndingsSurvive(@TempDir Path root) throws Exception {
        Path folder = installed(root, OLD.replace("\n", "\r\n"), OLD);
        ship(root, NEW);

        LanguageFileUpdates.bringUpToDate(loaderOver(root), "messages", folder);

        assertThat(Files.readString(folder.resolve("messages_en.conf"))).isEqualTo(NEW.replace("\n", "\r\n"));
    }

    /** A plugin folder as the previous release left it: the operator's file and the copy it was written from. */
    private static Path installed(Path root, String operator, String baseline) throws Exception {
        Path folder = root.resolve("plugin/messages");
        Files.createDirectories(folder.resolve(".defaults"));
        Files.writeString(folder.resolve("messages_en.conf"), operator);
        Files.writeString(folder.resolve(".defaults/messages_en.conf"), baseline);
        return folder;
    }

    private static void ship(Path root, String english) throws Exception {
        Path jar = root.resolve("jar/messages");
        Files.createDirectories(jar);
        Files.writeString(jar.resolve("messages_en.conf"), english);
    }

    private static ClassLoader loaderOver(Path root) throws Exception {
        return new java.net.URLClassLoader(
                new java.net.URL[] {root.resolve("jar").toUri().toURL()}, null);
    }
}
