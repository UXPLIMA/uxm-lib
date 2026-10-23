package com.uxplima.uxmlib.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StorageDriversTest {

    @Test
    @DisplayName("the drivers a plugin fetches are the versions this library compiles and tests against")
    void theCoordinatesFollowTheCatalogue() throws IOException {
        String catalogue = Files.readString(Path.of("..", "gradle", "libs.versions.toml"), StandardCharsets.UTF_8);

        assertThat(StorageDrivers.MARIADB).endsWith(":" + version(catalogue, "mariadb-jdbc"));
        assertThat(StorageDrivers.POSTGRESQL).endsWith(":" + version(catalogue, "postgresql"));
        assertThat(StorageDrivers.coordinates()).containsExactly(StorageDrivers.MARIADB, StorageDrivers.POSTGRESQL);
    }

    private static String version(String catalogue, String key) {
        Matcher found = Pattern.compile("(?m)^" + Pattern.quote(key) + "\\s*=\\s*\"([^\"]+)\"")
                .matcher(catalogue);
        assertThat(found.find()).as(key + " in the catalogue").isTrue();
        return found.group(1);
    }
}
