package com.uxplima.uxmlib.storage.migration;

import static org.assertj.core.api.Assertions.assertThat;

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

import com.uxplima.uxmlib.storage.sql.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Loads {@code V*.sql} migrations off the classpath — from an exploded directory (the {@code file:} URL
 * a test or an IDE run sees) and from inside a jar (the {@code jar:} URL a shaded plugin sees at runtime) —
 * and proves the per-version overlay used for dialect-specific overrides.
 */
class MigrationResourcesTest {

    @Test
    void loadsOrderedMigrationsFromAnExplodedDirectory() {
        List<Migration> migrations = MigrationResources.load(getClass().getClassLoader(), "db/testmigration");
        assertThat(migrations).extracting(Migration::version).containsExactly(1, 2);
        assertThat(migrations.get(0).description()).isEqualTo("first");
        assertThat(migrations.get(0).sql()).contains("first_table");
    }

    @Test
    void returnsEmptyWhenTheDirectoryIsAbsent() {
        assertThat(MigrationResources.load(getClass().getClassLoader(), "db/nope"))
                .isEmpty();
    }

    @Test
    void loadsMigrationsFromInsideAJar(@TempDir Path tmp) throws IOException {
        List<Migration> migrations = loadFromJar(tmp.resolve("migrations.jar"), null);
        assertThat(migrations).extracting(Migration::version).containsExactly(1, 2);
        assertThat(migrations.get(1).description()).isEqualTo("beta");
        assertThat(migrations.get(0).sql()).contains("CREATE TABLE alpha");
    }

    @Test
    void loadsMigrationsFromInsideAMultiReleaseJar(@TempDir Path tmp) throws IOException {
        // Every shaded consumer jar is one of these: Shadow merges the manifests of HikariCP and sqlite-jdbc,
        // and both declare Multi-Release, so the attribute lands in the plugin jar. The JDK then hands back a
        // directory resource whose entry name already ends in a slash, which is the whole of the bug.
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Multi-Release", "true");

        List<Migration> migrations = loadFromJar(tmp.resolve("multirelease.jar"), manifest);

        assertThat(migrations).extracting(Migration::version).containsExactly(1, 2);
        assertThat(migrations.get(1).description()).isEqualTo("beta");
        assertThat(migrations.get(0).sql()).contains("CREATE TABLE alpha");
    }

    @Test
    void theManifestDoesNotChangeWhatIsFound(@TempDir Path tmp) throws IOException {
        Manifest multiRelease = new Manifest();
        multiRelease.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        multiRelease.getMainAttributes().putValue("Multi-Release", "true");

        assertThat(loadFromJar(tmp.resolve("with.jar"), multiRelease))
                .isEqualTo(loadFromJar(tmp.resolve("without.jar"), null));
    }

    /** Build a jar holding the same three entries, optionally with {@code manifest}, and load through it. */
    private static List<Migration> loadFromJar(Path jar, @org.jspecify.annotations.Nullable Manifest manifest)
            throws IOException {
        try (JarOutputStream out = manifest == null
                ? new JarOutputStream(Files.newOutputStream(jar))
                : new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            putEntry(out, "db/", "");
            putEntry(out, "db/migration/", "");
            putEntry(out, "db/migration/V1__alpha.sql", "CREATE TABLE alpha (id INTEGER);");
            putEntry(out, "db/migration/V2__beta.sql", "CREATE TABLE beta (id INTEGER);");
            putEntry(out, "db/migration/README.txt", "ignore me");
        }
        try (URLClassLoader cl = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
            return MigrationResources.load(cl, "db/migration");
        }
    }

    @Test
    void overlayReplacesAMigrationOfTheSameVersion() {
        List<Migration> base = List.of(
                new Migration(1, "first", "CREATE TABLE a (id INTEGER);"),
                new Migration(2, "second", "CREATE TABLE b (id INTEGER);"));
        List<Migration> overrides = List.of(new Migration(2, "second postgres", "CREATE TABLE b_pg (id INTEGER);"));

        List<Migration> merged = MigrationResources.overlay(base, overrides);

        assertThat(merged).extracting(Migration::version).containsExactly(1, 2);
        assertThat(merged.get(1).description()).isEqualTo("second postgres");
        assertThat(merged.get(1).sql()).contains("b_pg");
    }

    @Test
    void loadedMigrationsApplyThroughTheRunner() {
        Database database = Database.builder()
                .jdbcUrl("jdbc:sqlite:file:migrloadtest?mode=memory&cache=shared")
                .maxPoolSize(1)
                .build();
        try {
            List<Migration> migrations = MigrationResources.load(getClass().getClassLoader(), "db/testmigration");
            int applied = new MigrationRunner(database).apply(migrations);
            assertThat(applied).isEqualTo(2);
            assertThat(new MigrationRunner(database).currentVersion()).isEqualTo(2);
        } finally {
            database.close();
        }
    }

    private static void putEntry(JarOutputStream out, String name, String content) throws IOException {
        out.putNextEntry(new JarEntry(name));
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }
}
