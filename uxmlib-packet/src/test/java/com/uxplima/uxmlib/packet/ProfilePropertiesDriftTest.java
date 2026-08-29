package com.uxplima.uxmlib.packet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Guards fake-player profile construction against the immutable-property-map trap. authlib's two-argument
 * {@code GameProfile(id, name)} constructor seats an immutable property map, so attaching the skin afterwards
 * with {@code profile.properties().put("textures", ...)} throws {@link UnsupportedOperationException} at spawn:
 * the NPC body and the tab-list skin then silently never render, a failure that surfaces only in a running
 * server's log. Profiles must instead be built through {@link GameProfiles}, which seats a mutable map before
 * construction. The packet layer cannot exercise the real authlib classes under unit test (the Mojang-mapped
 * dev bundle is kept off the test classpath), so this scans the sources and fails if any of them reaches for
 * that map's {@code put} again.
 */
class ProfilePropertiesDriftTest {

    @Test
    void noPacketSourceMutatesAGameProfilePropertyMap() {
        Path sources = repoRoot().resolve("uxmlib-packet/src/main/java");
        assertThat(Files.isDirectory(sources))
                .as("expected packet sources under %s", sources)
                .isTrue();

        List<String> offenders;
        try (Stream<Path> walk = Files.walk(sources)) {
            offenders = walk.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    // GameProfiles is the sanctioned builder; its javadoc names the anti-pattern it replaces.
                    .filter(path -> !path.getFileName().toString().equals("GameProfiles.java"))
                    .filter(ProfilePropertiesDriftTest::mutatesProfileProperties)
                    .map(path -> sources.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }

        assertThat(offenders)
                .as(
                        "build the profile through GameProfiles instead of mutating a GameProfile property map "
                                + "(authlib seats an immutable one, so put throws at spawn):\n%s",
                        String.join("\n", offenders))
                .isEmpty();
    }

    /** A source that calls {@code put} on a {@code GameProfile}'s own (possibly immutable) property map. */
    private static boolean mutatesProfileProperties(Path source) {
        try {
            String body = Files.readString(source);
            return body.contains(".properties().put(") || body.contains(".getProperties().put(");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate the repo root (settings.gradle.kts)");
    }
}
