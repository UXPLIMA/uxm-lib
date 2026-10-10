package com.uxplima.uxmlib.command.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import com.uxplima.uxmlib.config.HoconConfig;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Reads {@code commands.conf} and turns it into what the command DSL registers.
 *
 * <p>A command label is a thing an operator has to be able to change. Two plugins want {@code /glow}, or a
 * server runs in a language where the English word means nothing, and neither case should need a rebuild.
 * The annotation seam is what makes that possible without the label appearing twice: {@link FromConfig}
 * carries only a key, and this class rewrites it into a real {@code @Command} at registration.
 *
 * <p>The file is read once, at wiring. A rename after that needs a restart, because the server registers a
 * command tree at enable and does not offer a supported way to rename a live one.
 *
 * <p>The same annotation on a method names a branch. Its key sits under the {@code subcommands} block of the
 * command that declares the method, so a branch key is short and cannot collide with the {@code name},
 * {@code aliases} and {@code enabled} of the command itself.
 *
 * <p>A branch can be switched off in two shapes and they do different things. The block form,
 * {@code subcommands { run { enabled = false } }}, drops the branch out of the command tree at registration.
 * The plain form, {@code subcommands { run = false }}, leaves it in the tree, and
 * {@link #isBranchEnabled(String, String)} is what a handler reads to answer that the word it was given is
 * turned off. Both are honest; which one an operator wants depends on whether they would rather the word be
 * unknown or be refused.
 *
 * <p>A command and a branch may also carry {@code localized-aliases}, a list of words per language tag, as
 * {@code localized-aliases { tr = ["arsa"] }}. A root word becomes an ordinary alias that
 * {@link LocalizedAliasVisibility} keeps out of other readers' command lists. A branch word is answered for its
 * readers and the console only.
 */
public final class ConfiguredCommands {

    /**
     * One command's entry in the file.
     *
     * <p>{@code localizedAliases} holds the words for the readers of one language, keyed by a normal
     * language tag such as {@code tr} or {@code pt-BR}, in the order the file wrote them.
     */
    public record Entry(
            String name, List<String> aliases, boolean enabled, Map<String, List<String>> localizedAliases) {

        public Entry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(aliases, "aliases");
            Objects.requireNonNull(localizedAliases, "localizedAliases");
            aliases = List.copyOf(aliases);
            Map<String, List<String>> copy = new LinkedHashMap<>();
            localizedAliases.forEach((tag, words) -> copy.put(tag, List.copyOf(words)));
            localizedAliases = Collections.unmodifiableMap(copy);
            if (name.isBlank()) {
                throw new IllegalArgumentException("a command name must not be blank");
            }
        }

        /** An entry with no word for one language only. */
        public Entry(String name, List<String> aliases, boolean enabled) {
            this(name, aliases, enabled, Map.of());
        }

        /** Every localized word in its annotation form, {@code tag=word}, the file's order kept. */
        List<String> writtenLocalizedAliases() {
            List<String> written = new ArrayList<>();
            localizedAliases.forEach(
                    (tag, words) -> words.forEach(word -> written.add(new LocaleTags.Entry(tag, word).written())));
            return List.copyOf(written);
        }
    }

    private final HoconConfig config;

    private ConfiguredCommands(HoconConfig config) {
        this.config = config;
    }

    /** Read the file. A missing file is not an error: every key falls back to what the handler declares. */
    public static ConfiguredCommands load(Path file) {
        Objects.requireNonNull(file, "file");
        return new ConfiguredCommands(HoconConfig.load(file));
    }

    /** The entry for {@code key}, filled in from {@code fallbackName} where the file says nothing. */
    public Entry entryOf(String key, String fallbackName) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(fallbackName, "fallbackName");
        String base = "commands." + key + ".";
        return new Entry(
                config.getString(base + "name", fallbackName),
                config.getList(base + "aliases", String.class),
                config.getBoolean(base + "enabled", true),
                localizedOf(config.nodeAt(path(key, "localized-aliases"))));
    }

    /** The path of {@code leaf} under the dotted {@code key}, one segment per word. */
    private static Object[] path(String key, String leaf) {
        List<Object> segments = new ArrayList<>(List.of("commands"));
        segments.addAll(List.of(key.split("\\.")));
        segments.add(leaf);
        return segments.toArray();
    }

    /**
     * The {@code localized-aliases} block: a list of words per language tag. A tag that names no language
     * is dropped with its words, and so is a word that is blank or holds a space, because neither can be
     * typed as one literal.
     */
    private static Map<String, List<String>> localizedOf(ConfigurationNode node) {
        if (!node.isMap()) {
            return Map.of();
        }
        Map<String, List<String>> byTag = new LinkedHashMap<>();
        node.childrenMap()
                .forEach((rawTag, words) -> LocaleTags.normalize(String.valueOf(rawTag))
                        .ifPresent(tag -> {
                            List<String> kept = byTag.computeIfAbsent(tag, ignored -> new ArrayList<>());
                            for (ConfigurationNode word : words.childrenList()) {
                                String text = word.getString("").strip();
                                if (!text.isEmpty() && !text.contains(" ") && !kept.contains(text)) {
                                    kept.add(text);
                                }
                            }
                        }));
        byTag.values().removeIf(List::isEmpty);
        return byTag;
    }

    /**
     * Whether {@code key} should be registered at all.
     *
     * <p>Disabling is a real need: a server that already has a {@code /glow} from another plugin wants ours
     * off rather than fighting over the label, and an operator who wants only the menu can turn the command
     * off entirely.
     */
    public boolean isEnabled(String key, String fallbackName) {
        return entryOf(key, fallbackName).enabled();
    }

    /**
     * Whether the branch {@code branch} of the command {@code commandKey} is switched on.
     *
     * <p>This is the plain read, and it answers the other half of the question {@link #replacer()} answers.
     * An operator writes one line:
     *
     * <pre>{@code
     * commands { uxmbackup { subcommands { run = false } } }
     * }</pre>
     *
     * <p>and the branch <em>stays in the command tree</em>. Nothing is taken out of the tree, so the handler
     * is still reached and can say that the word it was given is turned off. That is the honest answer, and
     * it is the only one available: the server builds its command tree once, at enable, and offers no
     * supported way to remove one branch from a live one.
     *
     * <p>The block form is the other shape, and it means something else:
     *
     * <pre>{@code
     * commands { uxmbackup { subcommands { run { enabled = false } } } }
     * }</pre>
     *
     * <p>A branch turned off there is replaced by nothing at registration, so it never enters the tree and
     * the server answers for it in its own words. Read here, that branch reports off too, because a branch
     * nobody can reach is off by any reading.
     *
     * <p>A branch the file does not mention is on, so an operator writes only the ones they want off.
     * {@code CONTRACT.md} section 12 is the reason the plain form has to exist: a plugin that could not read
     * it had to ship a message key that could never print, or carry a reader of its own.
     */
    public boolean isBranchEnabled(String commandKey, String branch) {
        Objects.requireNonNull(commandKey, "commandKey");
        Objects.requireNonNull(branch, "branch");
        ConfigurationNode node = config.nodeAt("commands", commandKey, "subcommands", branch);
        // A map is the block form and carries its own `enabled`; anything else is the one-line switch.
        return node.isMap() ? node.node("enabled").getBoolean(true) : node.getBoolean(true);
    }

    /** The key a branch is read under: the {@code subcommands} block of the command that declares it. */
    public static String branchKey(String commandKey, String branchKey) {
        Objects.requireNonNull(commandKey, "commandKey");
        Objects.requireNonNull(branchKey, "branchKey");
        return commandKey + ".subcommands." + branchKey;
    }

    /**
     * The replacer that turns {@link FromConfig} into the annotation the DSL expects: a {@code @Command} on a
     * class, a {@code @}{@link Subcommand} on a method. A branch the file turns off is replaced by nothing,
     * so the scan never sees a {@code @Subcommand} and the method leaves the command tree.
     */
    public AnnotationReplacer<FromConfig> replacer() {
        return (fromConfig, element) ->
                element instanceof Method method ? branchOf(fromConfig, method) : rootOf(fromConfig);
    }

    private List<Annotation> rootOf(FromConfig fromConfig) {
        Entry entry = entryOf(fromConfig.value(), fallbackOf(fromConfig));
        // Every localized word is an ordinary alias as well, so it answers whoever types it. Which client is
        // shown it is decided when the command list is sent, by LocalizedAliasVisibility.
        List<String> aliases = new ArrayList<>(entry.aliases());
        entry.localizedAliases()
                .values()
                .forEach(words -> words.stream()
                        .filter(word -> !word.equals(entry.name()) && !aliases.contains(word))
                        .forEach(aliases::add));
        return List.of(Replacements.of(
                Command.class,
                Map.of(
                        "name", entry.name(),
                        "aliases", aliases.toArray(new String[0]),
                        "localizedAliases", entry.writtenLocalizedAliases().toArray(new String[0]),
                        "description", fromConfig.description())));
    }

    private List<Annotation> branchOf(FromConfig fromConfig, Method method) {
        Entry entry = entryOf(keyOf(fromConfig, method), fallbackOf(fromConfig));
        if (!entry.enabled()) {
            return List.of();
        }
        return List.of(Replacements.of(
                Subcommand.class,
                Map.of(
                        "value", entry.name(),
                        "aliases", entry.aliases().toArray(new String[0]),
                        "localizedAliases", entry.writtenLocalizedAliases().toArray(new String[0]),
                        "description", fromConfig.description())));
    }

    /** The label to use when the file names none: what the author wrote, or the key when they wrote nothing. */
    private static String fallbackOf(FromConfig fromConfig) {
        return fromConfig.fallbackName().isEmpty() ? fromConfig.value() : fromConfig.fallbackName();
    }

    /**
     * Where a branch is read from. The command the method belongs to owns the block, so the key on the
     * method is the short word an author writes once and an operator finds under that command.
     */
    private static String keyOf(FromConfig fromConfig, Method method) {
        AnnotatedElement owner = method.getDeclaringClass();
        FromConfig command = owner.getAnnotation(FromConfig.class);
        return command == null ? fromConfig.value() : branchKey(command.value(), fromConfig.value());
    }
}
