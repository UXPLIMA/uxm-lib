package com.uxplima.uxmlib.gui.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A menu is a file, and the plugin finds it by the name of that file. */
class MenuFilesTest {

    private static final String MENU =
            """
            title = "@menu.title"
            rows = 3
            items {
              one { slot = 0, material = STONE, name = " " }
            }
            """;

    @Test
    @DisplayName("every file under the folder is a menu, named by its path")
    void everyFileIsAMenu(@TempDir Path folder) throws Exception {
        Files.writeString(folder.resolve("house.conf"), MENU, StandardCharsets.UTF_8);
        Files.createDirectories(folder.resolve("admin"));
        Files.writeString(folder.resolve("admin/log.conf"), MENU, StandardCharsets.UTF_8);

        MenuFiles menus = MenuFiles.load(folder, resource -> null, (menu, why) -> {});

        assertThat(menus.size()).isEqualTo(2);
        assertThat(menus.byName("house")).isPresent();
        assertThat(menus.byName("admin/log")).isPresent();
    }

    @Test
    @DisplayName("a file that cannot be read falls back to the shipped one and says so")
    void abrokenFileFallsBackToTheShippedOne(@TempDir Path folder) throws Exception {
        Files.writeString(folder.resolve("house.conf"), "title = \"@t\"\nrows = ", StandardCharsets.UTF_8);
        List<String> complaints = new ArrayList<>();

        MenuFiles menus = MenuFiles.load(
                folder,
                resource -> resource.equals("menus/house.conf")
                        ? new ByteArrayInputStream(MENU.getBytes(StandardCharsets.UTF_8))
                        : null,
                (menu, why) -> complaints.add(menu));

        assertThat(menus.byName("house")).isPresent();
        assertThat(complaints).containsExactly("house");
    }

    @Test
    @DisplayName("a menu with no readable file and no shipped one is simply absent")
    void aMenuWithNoFileAtAllIsAbsent(@TempDir Path folder) throws Exception {
        Files.writeString(folder.resolve("house.conf"), "rows = ", StandardCharsets.UTF_8);

        MenuFiles menus = MenuFiles.load(folder, resource -> null, (menu, why) -> {});

        assertThat(menus.byName("house")).isEmpty();
        assertThat(menus.size()).isZero();
    }

    @Test
    @DisplayName("a folder that is not there holds no menu and is not an error")
    void anAbsentFolderHoldsNoMenu(@TempDir Path folder) {
        assertThat(MenuFiles.load(folder.resolve("gone"), resource -> null, (menu, why) -> {})
                        .size())
                .isZero();
    }
}
