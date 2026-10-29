package com.uxplima.uxmlib.storage.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Loads migrations from inside a jar, the {@code jar:} URL a shaded plugin sees at runtime, with and without the
 * {@code Multi-Release} attribute Shadow writes into every plugin jar that shades HikariCP or sqlite-jdbc.
 */
class MigrationResourcesTest {

    @Test
    void loadsMigrationsFromInsideAJar(@TempDir Path tmp) throws IOException {
        List<Migration> migrations = loadFromJar(tmp.resolve("plain.jar"), null);

        assertThat(migrations).extracting(Migration::version).containsExactly(1, 2);
        assertThat(migrations.get(1).description()).isEqualTo("beta");
        assertThat(migrations.get(0).sql()).contains("CREATE TABLE alpha");
    }

    @Test
    void loadsMigrationsFromInsideAMultiReleaseJar(@TempDir Path tmp) throws IOException {
        // The JDK hands back a directory entry name that already ends in a slash for such a jar.
        List<Migration> migrations = loadFromJar(tmp.resolve("multi-release.jar"), multiRelease());

        assertThat(migrations).extracting(Migration::version).containsExactly(1, 2);
        assertThat(migrations.get(1).description()).isEqualTo("beta");
    }

    @Test
    void theManifestDoesNotChangeWhatIsFound(@TempDir Path tmp) throws IOException {
        assertThat(loadFromJar(tmp.resolve("with.jar"), multiRelease()))
                .isEqualTo(loadFromJar(tmp.resolve("without.jar"), null));
    }

    @Test
    void loadRequiredRefusesADirectoryWithNoMigrationsAndNamesIt(@TempDir Path tmp) throws IOException {
        Path jar = tmp.resolve("empty.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), multiRelease())) {
            putEntry(out, "db/", "");
            putEntry(out, "db/migration/", "");
        }
        try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
            assertThatThrownBy(() -> MigrationResources.loadRequired(loader, "db/migration"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("db/migration");
        }
    }

    private static Manifest multiRelease() {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Multi-Release", "true");
        return manifest;
    }

    /** Build a jar holding the same entries, with {@code manifest} when given, and load through it. */
    private static List<Migration> loadFromJar(Path jar, @Nullable Manifest manifest) throws IOException {
        try (JarOutputStream out = manifest == null
                ? new JarOutputStream(Files.newOutputStream(jar))
                : new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            putEntry(out, "db/", "");
            putEntry(out, "db/migration/", "");
            putEntry(out, "db/migration/V1__alpha.sql", "CREATE TABLE alpha (id INTEGER);");
            putEntry(out, "db/migration/V2__beta.sql", "CREATE TABLE beta (id INTEGER);");
            putEntry(out, "db/migration/README.txt", "ignore me");
        }
        try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
            return MigrationResources.load(loader, "db/migration");
        }
    }

    private static void putEntry(JarOutputStream out, String name, String content) throws IOException {
        out.putNextEntry(new JarEntry(name));
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }
}
