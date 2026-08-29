package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Secret;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import org.junit.jupiter.api.Test;

/**
 * Covers {@code @}{@link Secret}: the {@link Secrets} predicate recognises a method- or class-level mark, and
 * {@link HelpRenderer} keeps a secret branch out of the generated help while still building and registering
 * its node, so it runs for whoever types it.
 */
class SecretTest {

    @Command(name = "warp")
    static class WarpCommand {
        @Subcommand("list")
        void list(Sender sender) {}

        @Secret
        @Subcommand("debug")
        void debug(Sender sender) {}
    }

    @Command(name = "staff")
    @Secret
    static class StaffCommand {
        @Subcommand("panel")
        void panel(Sender sender) {}
    }

    @Test
    void aMethodMarkedSecretIsRecognised() {
        CommandModel warp = CommandModels.reflect(new WarpCommand(), ParamResolvers.withDefaults());

        assertThat(Secrets.isSecret(branch(warp, "debug"))).isTrue();
        assertThat(Secrets.isSecret(branch(warp, "list"))).isFalse();
    }

    @Test
    void aClassMarkedSecretMakesEveryBranchSecret() {
        CommandModel staff = CommandModels.reflect(new StaffCommand(), ParamResolvers.withDefaults());

        assertThat(Secrets.isSecret(branch(staff, "panel"))).isTrue();
    }

    @Test
    void aSecretBranchIsOmittedFromTheGeneratedHelp() {
        CommandModel warp = CommandModels.reflect(new WarpCommand(), ParamResolvers.withDefaults());

        List<HelpRenderer.Entry> entries = HelpRenderer.entriesOf(List.of(branch(warp, "list"), branch(warp, "debug")));

        assertThat(entries).extracting(HelpRenderer.Entry::usage).containsExactly("list");
    }

    private static BranchModel branch(CommandModel model, String path) {
        return model.branches().stream()
                .filter(b -> b.path().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no branch with path '" + path + "'"));
    }

    @Test
    void aSecretBranchStillBuildsAndRegistersItsNode() {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new WarpCommand());

        // The node is present and runnable: secrecy is help-only, not a node-removal.
        assertThat(node.getChild("debug")).isNotNull();
        assertThat(node.getChild("list")).isNotNull();
    }
}
