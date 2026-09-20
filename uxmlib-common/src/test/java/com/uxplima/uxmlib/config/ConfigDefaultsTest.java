package com.uxplima.uxmlib.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers default-resource extraction, defaults auto-merge, and comment round-trip. */
class ConfigDefaultsTest {

    private static ClassLoader loader() {
        return ConfigDefaultsTest.class.getClassLoader();
    }

    @Test
    void extractsTheBundledDefaultOnFirstRun(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("config.conf");
        assertThat(Files.exists(target)).isFalse();

        HoconConfig config = HoconConfig.loadOrExtract(target, "default-config.conf", loader());

        assertThat(Files.exists(target)).isTrue();
        assertThat(config.getInt("limit", 0)).isEqualTo(5);
        assertThat(config.getBoolean("feature.enabled", false)).isTrue();
        // The authored header comment survived the extraction (emitComments + byte-for-byte copy).
        assertThat(Files.readString(target)).contains("This header should survive extraction");
    }

    @Test
    void doesNotClobberAnExistingFile(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("config.conf");
        Files.writeString(target, "limit = 99\n");

        HoconConfig config = HoconConfig.loadOrExtract(target, "default-config.conf", loader());

        assertThat(config.getInt("limit", 0)).isEqualTo(99); // user's value kept, default not applied
    }

    @Test
    void mergeDefaultsAddsMissingKeysButKeepsUserValues(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("config.conf");
        Files.writeString(target, "limit = 99\n"); // user set limit, but not feature.enabled
        HoconConfig config = HoconConfig.load(target);

        boolean wrote = config.mergeDefaults("default-config.conf", loader());

        assertThat(wrote).isTrue();
        assertThat(config.getInt("limit", 0)).isEqualTo(99); // user value untouched
        assertThat(config.getBoolean("feature.enabled", false)).isTrue(); // missing key injected from default
    }

    /**
     * A merge rewrites a file somebody wrote by hand, so the file as it was is kept beside it.
     *
     * <p>This is the half that makes the merge safe to ship in every plugin rather than in one. The merge
     * itself is careful: it adds what is absent and never touches a value. It still renders the whole
     * document again from the tree, so an operator's own alignment and the odd comment in an unusual place
     * can move, and an operator who spent an afternoon on that file deserves the copy. A merge that adds
     * nothing writes nothing, so the copy appears only on the run that changed something.
     */
    @Test
    void mergeDefaultsKeepsTheFileAsItWas(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, "greeting = \"mine\"\n");
        HoconConfig config = HoconConfig.load(file);

        assertThat(config.mergeDefaults("default-config.conf", loader())).isTrue();

        Path backup = dir.resolve("config.conf.bak");
        assertThat(backup).exists();
        assertThat(Files.readString(backup))
                .describedAs("the copy is the file as it was, before the merge rendered it again")
                .isEqualTo("greeting = \"mine\"\n");
    }

    @Test
    void mergeDefaultsKeepsNoCopyWhenNothingWasAdded(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, "greeting = \"mine\"\nextra = 1\n");
        HoconConfig config = HoconConfig.load(file);
        config.mergeDefaults("default-config.conf", loader());
        Files.deleteIfExists(dir.resolve("config.conf.bak"));

        assertThat(config.mergeDefaults("default-config.conf", loader())).isFalse();

        assertThat(dir.resolve("config.conf.bak"))
                .describedAs("a run that changes nothing leaves nothing behind")
                .doesNotExist();
    }

    @Test
    void mergeDefaultsWritesNothingWhenAlreadyComplete(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("config.conf");
        HoconConfig config = HoconConfig.loadOrExtract(target, "default-config.conf", loader());

        boolean wrote = config.mergeDefaults("default-config.conf", loader());

        assertThat(wrote).isFalse(); // everything already present -> no rewrite
    }
}
