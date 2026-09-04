package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import org.junit.jupiter.api.Test;

/**
 * Covers the Lamp-style {@link AnnotationReplacer} SPI: a consumer registers a replacer that rewrites a
 * custom annotation into core annotation(s) at registration, and the rest of the build honours the rewritten
 * (effective) annotations rather than the raw ones. The focused case maps a custom {@code @Admin} on a branch
 * method into a core {@code @}{@link Permission}, then proves the rendered branch carries that permission and
 * that an unrelated branch is untouched.
 */
class AnnotationReplacerTest {

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @interface Admin {
        String value() default "admin";
    }

    @Command(name = "rep")
    static class RepCommand {
        @Admin("shop.admin")
        @Subcommand("reload")
        void reload(Sender sender) {}

        @Subcommand("open")
        void open(Sender sender, @Arg("page") int page) {}
    }

    /** A replacer that turns {@code @Admin("x")} into a core {@code @Permission("x")}. */
    private static AnnotationReplacer<Admin> permissionFromAdmin() {
        return (admin, element) -> List.of(Replacements.permission(admin.value()));
    }

    @Test
    void aReplacerRewritesACustomAnnotationIntoACoreOne() {
        ParamResolvers resolvers = ParamResolvers.withDefaults().replacer(Admin.class, permissionFromAdmin());

        CommandModel model = CommandModels.reflect(new RepCommand(), resolvers);
        BranchModel reload = branch(model, "reload");

        assertThat(reload.permission()).isNotNull();
        assertThat(java.util.Objects.requireNonNull(reload.permission()).value())
                .isEqualTo("shop.admin");
    }

    @Test
    void theRewrittenPermissionGatesTheRenderedBranch() {
        ParamResolvers resolvers = ParamResolvers.withDefaults().replacer(Admin.class, permissionFromAdmin());

        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new RepCommand(), resolvers);
        CommandNode<CommandSourceStack> reload = java.util.Objects.requireNonNull(node.getChild("reload"));
        // A permission becomes a Brigadier requires gate, so the node carries a non-default requirement.
        assertThat(reload.getRequirement()).isNotNull();
    }

    @Test
    void abranchWithoutTheCustomAnnotationIsUntouched() {
        ParamResolvers resolvers = ParamResolvers.withDefaults().replacer(Admin.class, permissionFromAdmin());

        CommandModel model = CommandModels.reflect(new RepCommand(), resolvers);
        BranchModel open = branch(model, "open");

        assertThat(open.permission()).isNull();
        assertThat(open.args()).extracting(ArgBinder.ParamArg::name).containsExactly("page");
    }

    @Test
    void aReplacerForAnUnregisteredAnnotationIsNeverConsulted() {
        // No replacer registered: a method-level core @Permission is still read straight off the method.
        CommandModel model = CommandModels.reflect(new Annotated(), ParamResolvers.withDefaults());
        assertThat(java.util.Objects.requireNonNull(branch(model, "x").permission())
                        .value())
                .isEqualTo("core.perm");
    }

    @Test
    @SuppressWarnings("NullAway") // intentionally passes null to assert the requireNonNull guards fire
    void registeringANullReplacerIsRejected() {
        assertThatThrownBy(() -> ParamResolvers.withDefaults().replacer(Admin.class, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ParamResolvers.withDefaults().replacer(null, permissionFromAdmin()))
                .isInstanceOf(NullPointerException.class);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Named {
        String value();
    }

    /** A branch whose literal comes from somewhere else, the way a root name comes out of a config file. */
    @Command(name = "renamed")
    static class RenamedCommand {
        @Named("reload")
        void reload(Sender sender) {}

        @Named("open")
        @Subcommand("open")
        void open(Sender sender) {}
    }

    /** A replacer that turns {@code @Named("x")} into a core {@code @Subcommand("x-of-the-file")}. */
    private static AnnotationReplacer<Named> subcommandFromNamed() {
        return (named, element) ->
                List.of(Replacements.of(Subcommand.class, java.util.Map.of("value", named.value() + "-of-the-file")));
    }

    @Test
    void areplacerNamesTheLiteralOfABranch() {
        ParamResolvers resolvers = ParamResolvers.withDefaults().replacer(Named.class, subcommandFromNamed());

        CommandModel model = CommandModels.reflect(new RenamedCommand(), resolvers);

        assertThat(model.branches()).extracting(BranchModel::path).contains("reload-of-the-file");
    }

    @Test
    void arawSubcommandStillWinsOverAReplacement() {
        // The rule everywhere else: a raw annotation on the element beats one a replacer produced.
        ParamResolvers resolvers = ParamResolvers.withDefaults().replacer(Named.class, subcommandFromNamed());

        CommandModel model = CommandModels.reflect(new RenamedCommand(), resolvers);

        assertThat(model.branches())
                .extracting(BranchModel::path)
                .contains("open")
                .doesNotContain("open-of-the-file");
    }

    @Test
    void arenamedBranchIsTheLiteralOfTheBuiltNode() {
        ParamResolvers resolvers = ParamResolvers.withDefaults().replacer(Named.class, subcommandFromNamed());

        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new RenamedCommand(), resolvers);

        assertThat(node.getChild("reload-of-the-file")).isNotNull();
        assertThat(node.getChild("reload")).isNull();
    }

    @Command(name = "core")
    static class Annotated {
        @Permission("core.perm")
        @Subcommand("x")
        void x(Sender sender) {}
    }

    private static BranchModel branch(CommandModel model, String path) {
        return model.branches().stream()
                .filter(b -> b.path().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no branch with path '" + path + "'"));
    }
}
