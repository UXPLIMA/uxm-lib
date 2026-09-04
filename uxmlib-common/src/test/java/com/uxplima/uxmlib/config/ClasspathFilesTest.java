package com.uxplima.uxmlib.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ClasspathFilesTest {

    @Test
    @DisplayName("lists the files directly under a directory, sorted, and no deeper")
    void listsTheFilesOfADirectory(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("messages/deeper"));
        Files.writeString(root.resolve("messages/messages_tr.conf"), "a", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("messages/messages_en.conf"), "b", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("messages/deeper/messages_de.conf"), "c", StandardCharsets.UTF_8);

        assertThat(ClasspathFiles.list(loaderOver(root), "messages"))
                .containsExactly("messages_en.conf", "messages_tr.conf");
    }

    @Test
    @DisplayName("a directory nothing put on the classpath holds no file")
    void anAbsentDirectoryIsEmpty(@TempDir Path root) throws IOException {
        assertThat(ClasspathFiles.list(loaderOver(root), "messages")).isEmpty();
    }

    private static ClassLoader loaderOver(Path root) throws IOException {
        URL url = root.toUri().toURL();
        return new URLClassLoader(new URL[] {url}, null);
    }
}
