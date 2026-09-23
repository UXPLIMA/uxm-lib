package com.uxplima.uxmlib.hook.edit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The edit seam on a server with no world editor, which is the state most servers are in.
 *
 * <p>What has to be true here is that a consumer never has a branch. It asks for a guard, gets one, installs
 * its rule, and nothing happens: no class of an absent plugin is linked, nothing throws, and the consumer's
 * own code reads the same whether WorldEdit is installed or not. Wrapping a real edit needs a running
 * WorldEdit, so that half is exercised on a live server and not here.
 */
class EditGuardTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a server with no editor finds none, and never touches WorldEdit to find that out")
    void noEditorIsFound() {
        assertThat(WorldEditGuard.find()).isEmpty();
    }

    @Test
    @DisplayName("a FAWE server never gets the WorldEdit guard, whose extent FAWE throws away")
    void aFaweServerNeverGetsTheWorldEditGuard() {
        // FAWE's half is not on this classpath, which is the state a FAWE whose API moved leaves behind.
        MockBukkit.createMockPlugin("FastAsyncWorldEdit");
        MockBukkit.createMockPlugin("WorldEdit");

        assertThat(WorldEditGuard.find())
                .describedAs("FAWE drops the extent the WorldEdit guard hands it, so that guard would say it"
                        + " guards while guarding nothing")
                .isEmpty();
    }

    @Test
    @DisplayName("a consumer asking the server gets a guard rather than an empty answer")
    void theconsumerAlwaysGetsAGuard() {
        EditGuard guard = EditGuard.forServer();

        assertThat(guard)
                .describedAs("an empty answer would put a branch in every consumer, which is the branch"
                        + " this whole seam exists to remove")
                .isNotNull();
        assertThat(guard.isAvailable()).isFalse();
        assertThat(guard.pluginName()).isEqualTo("none");
    }

    @Test
    @DisplayName("installing and uninstalling on a server with no editor does nothing and throws nothing")
    void installingOnNothingIsSafe() {
        EditGuard guard = EditGuard.none();
        List<String> asked = new ArrayList<>();

        assertThatCode(() -> {
                    guard.install(rule(asked));
                    guard.uninstall();
                    guard.uninstall();
                })
                .describedAs("a plugin disabling is a plugin that may already have been disabled")
                .doesNotThrowAnyException();
        assertThat(asked).isEmpty();
    }

    @Test
    @DisplayName("a boundary is asked about one block at a time, and it is told who is editing")
    void theboundaryIsAskedPerBlock() {
        List<String> asked = new ArrayList<>();
        EditBoundary rule = rule(asked);

        assertThat(rule.mayChange(edit(), 0, 64, 0)).isTrue();
        assertThat(asked).containsExactly("asked");
    }

    @Test
    @DisplayName("the permission question goes to the editor and never to the server")
    void thepermissionComesOffTheEdit() {
        EditBoundary onlyStaff = (edit, x, y, z) -> edit.holds("plugin.bypass");

        assertThat(onlyStaff.mayChange(edit(), 0, 64, 0))
                .describedAs("asking the server from the editor's thread is the one thing this seam"
                        + " exists to stop a consumer doing")
                .isFalse();
    }

    @Test
    @DisplayName("being told an edit was cut short is optional, so a consumer that says nothing writes nothing")
    void beingToldIsOptional() {
        EditBoundary quiet = (edit, x, y, z) -> true;

        assertThatCode(() -> quiet.refused(edit()))
                .describedAs("saying nothing is a legitimate answer, and a consumer should not have to"
                        + " write an empty method to give it")
                .doesNotThrowAnyException();
    }

    private static EditBoundary.Edit edit() {
        return new EditBoundary.Edit() {

            @Override
            public UUID player() {
                return UUID.nameUUIDFromBytes("somebody".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            @Override
            public String world() {
                return "world";
            }

            @Override
            public boolean holds(String permission) {
                return false;
            }
        };
    }

    private static EditBoundary rule(List<String> asked) {
        return (edit, x, y, z) -> {
            asked.add("asked");
            return true;
        };
    }
}
