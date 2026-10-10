package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandSendEvent;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.text.message.LocaleSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the words a command answers to in one language only.
 *
 * <p>An operator on a Turkish server wants {@code /arsa} beside {@code /plot}, and an English player should
 * not find {@code arsa} in their completions. Such a word works for everyone who types it at the root, so
 * the console, a script and a Bedrock player all keep the ordinary path. It is only kept out of the list a
 * client is sent. Under a command it is shown to, and answered for, the readers of its language and the
 * console, because Brigadier sends a client exactly the branches it may use.
 */
final class LocalizedAliasTest {

    @FromConfig(value = "example", fallbackName = "example", description = "An example")
    static class ExampleCommand {
        String said = "";

        @FromConfig("sell")
        void sell(Sender sender) {
            said = "sell";
        }
    }

    @Test
    @DisplayName("the file's localized aliases are read and keyed by a normal language tag")
    void theFileIsRead(@TempDir Path folder) throws IOException {
        Path file = write(folder, """
                commands {
                  example {
                    localized-aliases {
                      tr    = ["arsa", "parsel"]
                      pt_br = ["lote"]
                    }
                  }
                }
                """);

        ConfiguredCommands.Entry entry = ConfiguredCommands.load(file).entryOf("example", "example");

        assertThat(entry.localizedAliases())
                .containsExactly(Map.entry("tr", List.of("arsa", "parsel")), Map.entry("pt-BR", List.of("lote")));
    }

    @Test
    @DisplayName("a tag that names no language is dropped with its words")
    void aBadTagIsDropped(@TempDir Path folder) throws IOException {
        Path file = write(folder, """
                commands { example { localized-aliases { "not a tag" = ["x"], tr = ["arsa"] } } }
                """);

        ConfiguredCommands.Entry entry = ConfiguredCommands.load(file).entryOf("example", "example");

        assertThat(entry.localizedAliases()).containsOnlyKeys("tr");
    }

    @Test
    @DisplayName("a root word in one language is registered for everyone, so typing it always works")
    void aRootWordIsAnAlias(@TempDir Path folder) throws IOException {
        Path file = write(folder, """
                commands { example { aliases = ["ex"], localized-aliases { tr = ["arsa"] } } }
                """);

        Command command = (Command) ConfiguredCommands.load(file)
                .replacer()
                .replace(fromConfig(), ExampleCommand.class)
                .get(0);

        assertThat(command.aliases()).containsExactly("ex", "arsa");
        assertThat(command.localizedAliases()).containsExactly("tr=arsa");
    }

    @Test
    @DisplayName("a branch word in one language is shown to its readers and to the console only")
    void aBranchWordIsForItsReaders(@TempDir Path folder) throws IOException {
        Path file = write(folder, """
                commands { example { subcommands { sell { localized-aliases { tr = ["sat"] } } } } }
                """);

        LiteralCommandNode<CommandSourceStack> node = build(file);
        CommandNode<CommandSourceStack> sat = Objects.requireNonNull(node.getChild("sat"));
        CommandNode<CommandSourceStack> sell = Objects.requireNonNull(node.getChild("sell"));

        assertThat(sat.canUse(sourceOf(player(Locale.forLanguageTag("tr-TR"))))).isTrue();
        assertThat(sat.canUse(sourceOf(player(Locale.ENGLISH)))).isFalse();
        assertThat(sat.canUse(sourceOf(mock(CommandSender.class)))).isTrue();
        assertThat(sell.canUse(sourceOf(player(Locale.ENGLISH)))).isTrue();
    }

    @Test
    @DisplayName("the language a branch word answers to is the one the plugin says the player reads")
    void theChosenLanguageCounts(@TempDir Path folder) throws IOException {
        Path file = write(folder, """
                commands { example { subcommands { sell { localized-aliases { tr = ["sat"] } } } } }
                """);
        Player english = player(Locale.ENGLISH);
        LocaleSource chosen = new LocaleSource() {
            @Override
            public Locale defaultLocale() {
                return Locale.ENGLISH;
            }

            @Override
            public Locale localeOf(net.kyori.adventure.audience.Audience viewer) {
                return Locale.forLanguageTag("tr");
            }
        };
        ConfiguredCommands commands = ConfiguredCommands.load(file);
        ParamResolvers resolvers = ParamResolvers.withDefaults()
                .replacer(FromConfig.class, commands.replacer())
                .locales(chosen);

        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new ExampleCommand(), resolvers);

        assertThat(Objects.requireNonNull(node.getChild("sat")).canUse(sourceOf(english)))
                .isTrue();
    }

    @Test
    @DisplayName("a branch word in one language runs the same method")
    void aBranchWordRuns(@TempDir Path folder) throws Exception {
        Path file = write(folder, """
                commands { example { subcommands { sell { localized-aliases { tr = ["sat"] } } } } }
                """);
        ConfiguredCommands commands = ConfiguredCommands.load(file);
        ExampleCommand handler = new ExampleCommand();
        com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher =
                new com.mojang.brigadier.CommandDispatcher<>();
        dispatcher
                .getRoot()
                .addChild(AnnotatedCommands.buildNode(
                        handler, ParamResolvers.withDefaults().replacer(FromConfig.class, commands.replacer())));

        dispatcher.execute("example sat", sourceOf(player(Locale.forLanguageTag("tr"))));

        assertThat(handler.said).isEqualTo("sell");
    }

    @Test
    @DisplayName("a country in the tag narrows it, and a bare language covers every country")
    void aTagMatchesItsLanguage() {
        assertThat(LocaleTags.matches("tr", Locale.forLanguageTag("tr-TR"))).isTrue();
        assertThat(LocaleTags.matches("pt-BR", Locale.forLanguageTag("pt-BR"))).isTrue();
        assertThat(LocaleTags.matches("pt-BR", Locale.forLanguageTag("pt-PT"))).isFalse();
        assertThat(LocaleTags.matches("tr", Locale.ENGLISH)).isFalse();
    }

    @Test
    @DisplayName("a root word in one language is kept out of every other reader's list, with its namespace")
    void aRootWordIsHiddenFromOtherReaders() {
        LocalizedAliasVisibility visibility = new LocalizedAliasVisibility(
                Map.of("arsa", List.of("tr")), "uxmPlots", LocaleSource.ofDefault(Locale.ENGLISH), null);
        List<String> englishList = new ArrayList<>(List.of("plot", "uxmplots:plot", "arsa", "uxmplots:arsa"));
        List<String> turkishList = new ArrayList<>(englishList);

        visibility.onCommandSend(new PlayerCommandSendEvent(player(Locale.ENGLISH), englishList));
        visibility.onCommandSend(new PlayerCommandSendEvent(player(Locale.forLanguageTag("tr-TR")), turkishList));

        assertThat(englishList).containsExactly("plot", "uxmplots:plot");
        assertThat(turkishList).containsExactly("plot", "uxmplots:plot", "arsa", "uxmplots:arsa");
    }

    private static LiteralCommandNode<CommandSourceStack> build(Path file) {
        ConfiguredCommands commands = ConfiguredCommands.load(file);
        ParamResolvers resolvers = ParamResolvers.withDefaults().replacer(FromConfig.class, commands.replacer());
        return AnnotatedCommands.buildNode(new ExampleCommand(), resolvers);
    }

    private static Player player(Locale locale) {
        Player player = mock(Player.class);
        when(player.locale()).thenReturn(locale);
        when(player.hasPermission(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        return player;
    }

    private static CommandSourceStack sourceOf(CommandSender sender) {
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(sender);
        when(source.getExecutor()).thenReturn(sender instanceof Player player ? player : null);
        return source;
    }

    private static FromConfig fromConfig() {
        return ExampleCommand.class.getAnnotation(FromConfig.class);
    }

    private static Path write(Path folder, String body) throws IOException {
        Path file = folder.resolve("commands.conf");
        Files.writeString(file, body, StandardCharsets.UTF_8);
        return file;
    }
}
